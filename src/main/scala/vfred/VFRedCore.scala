/**
  * Vector FP Reduction Core
  *   Input: N * FP32 or 2N * BF(FP)16
  *   Output: one FP32
  */

package race.vpu.exu.laneexu.fp

import chisel3._
import chisel3.util._
import race.vpu._

class VFRedCore(
  N: Int = 64 // N = VLEN / 32
) extends Module {
  val SigWidthFp19 = 10 + 1  // Fixed
  val SigWidthFp32 = 23 + 1  // Fixed
  val ExtendedWidthFp19 = 12
  val ExtendedWidthFp32 = 12
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val is_bf16, is_fp16, is_fp32 = Input(Bool())
    val vs2 = Input(Vec(N, UInt(32.W)))
    val res = Output(new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32))
    val res_is_posInf, res_is_negInf, res_is_nan = Output(Bool())
    val valid_out = Output(Bool())
  })

  require(N > 1 && (N & (N - 1)) == 0, s"N must be a power of 2, but got $N")

  val (is_bf16, is_fp16, is_fp32) = (io.is_bf16, io.is_fp16, io.is_fp32)
  val is_16 = is_fp16 || is_bf16
  val is_32 = is_fp32

  val vs2_16 = Wire(Vec(2*N, UInt(16.W)))
  for (i <- 0 until N) {
    vs2_16(2*i) := io.vs2(i)(15, 0)
    vs2_16(2*i+1) := io.vs2(i)(31, 16)
  }

  val sign_in_16 = vs2_16.map(x => x(15))
  val exp_in_16 = vs2_16.map(x => Mux(is_fp16, x(14, 14-5+1), x(14, 14-8+1)))
  val frac_in_16 = vs2_16.map(x => Mux(is_fp16, x(0+11-2, 0), Cat(x(0+8-2, 0), 0.U(3.W))))

  val exp_is_0_16 = exp_in_16.map(x => x === 0.U)
  val exp_is_all1s_16 = exp_in_16.map(x => x === Mux(is_fp16, "b00011111".U, "b11111111".U))
  val frac_is_0_16 = frac_in_16.map(x => x === 0.U)
  val is_subnorm_16 = exp_is_0_16
  val is_inf_16 = exp_is_all1s_16 zip frac_is_0_16 map {case (is_all1s, is_0_frac) => is_all1s && is_0_frac}
  val is_nan_16 = exp_is_all1s_16 zip frac_is_0_16 map {case (is_all1s, is_0_frac) => is_all1s && !is_0_frac}
  
  val exp_adjust_subnorm_16 = is_subnorm_16 zip exp_in_16 map {case (is_subnorm, exp) =>
    Mux(is_subnorm, 1.U, exp)
  }
  //  x.xxxxxxx000   bf16 (1 + 7 + "000")
  //  x.xxxxxxxxxx   fp16 (1 + 10)
  val sig_adjust_subnorm_16 = Wire(Vec(2*N, UInt(11.W)))
  for (i <- 0 until 2*N) {
    sig_adjust_subnorm_16(i) := Mux(is_subnorm_16(i), 0.U(1.W), 1.U(1.W)) ## frac_in_16(i)
  }

  //------------------------------------------------------------------------------
  // If input is bf/fp16, perform pairwise additions to get N fp19 results first.
  // Extra delay: 2 cycles
  //------------------------------------------------------------------------------
  val fp19_res = Reg(Vec(N, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp19, ExtendedWidth = ExtendedWidthFp19 + 1)))
  val fp19_res_is_posInf = Reg(Vec(N, Bool()))
  val fp19_res_is_negInf = Reg(Vec(N, Bool()))
  val fp19_res_is_nan = Reg(Vec(N, Bool()))
  val fp19_res_valid = Reg(Bool())
  for (i <- 0 until N) {
    val fadd_extSig_fp19 = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = SigWidthFp19, ExtendedWidth = ExtendedWidthFp19, ExtAreZeros = true, UseShiftRightJam = false))
    fadd_extSig_fp19.io.valid_in := io.valid_in && is_16
    fadd_extSig_fp19.io.is_fp16 := is_fp16
    fadd_extSig_fp19.io.a.sign := sign_in_16(2*i)
    fadd_extSig_fp19.io.a.exp := exp_adjust_subnorm_16(2*i)
    fadd_extSig_fp19.io.a.sig := sig_adjust_subnorm_16(2*i) ## 0.U(ExtendedWidthFp19.W)
    fadd_extSig_fp19.io.b.sign := sign_in_16(2*i+1)
    fadd_extSig_fp19.io.b.exp := exp_adjust_subnorm_16(2*i+1)
    fadd_extSig_fp19.io.b.sig := sig_adjust_subnorm_16(2*i+1) ## 0.U(ExtendedWidthFp19.W)
    fadd_extSig_fp19.io.a_is_inf := is_inf_16(2*i)
    fadd_extSig_fp19.io.b_is_inf := is_inf_16(2*i+1)
    fadd_extSig_fp19.io.a_is_nan := is_nan_16(2*i)
    fadd_extSig_fp19.io.b_is_nan := is_nan_16(2*i+1)

    fp19_res(i) := RegEnable(fadd_extSig_fp19.io.res, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_posInf(i) := RegEnable(fadd_extSig_fp19.io.res_is_posInf, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_negInf(i) := RegEnable(fadd_extSig_fp19.io.res_is_negInf, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_nan(i) := RegEnable(fadd_extSig_fp19.io.res_is_nan, fadd_extSig_fp19.io.valid_out)
    if (i == 0) { fp19_res_valid := RegNext(fadd_extSig_fp19.io.valid_out) }
  }

  //----------------------------------------------------------------------------------
  // If input is fp32, do not use fadd_extSig_fp19, go directly to fp32 addition tree.
  //----------------------------------------------------------------------------------
  val sign_in_32 = io.vs2.map(x => x(31))
  val exp_in_32 = io.vs2.map(x => x(30, 23))
  val frac_in_32 = io.vs2.map(x => x(22, 0))

  val exp_is_0_32 = exp_in_32.map(x => x === 0.U)
  val exp_is_all1s_32 = exp_in_32.map(x => x === "b11111111".U)
  val frac_is_0_32 = frac_in_32.map(x => x === 0.U)
  val is_subnorm_32 = exp_is_0_32
  val is_inf_32 = exp_is_all1s_32 zip frac_is_0_32 map {case (is_all1s, is_0_frac) => is_all1s && is_0_frac}
  val is_nan_32 = exp_is_all1s_32 zip frac_is_0_32 map {case (is_all1s, is_0_frac) => is_all1s && !is_0_frac}

  val exp_adjust_subnorm_32 = is_subnorm_32 zip exp_in_32 map {case (is_subnorm, exp) =>
    Mux(is_subnorm, 1.U, exp)
  }
  val sig_adjust_subnorm_32 = Wire(Vec(N, UInt(24.W)))
  for (i <- 0 until N) {
    sig_adjust_subnorm_32(i) := Mux(is_subnorm_32(i), 0.U(1.W), 1.U(1.W)) ## frac_in_32(i)
  }

  //--------------------------------------------
  // Inputs of the 1st layer of fp32 adder tree
  //--------------------------------------------
  val fp32_adderTree_in = Wire(Vec(N, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32)))
  val fp32_adderTree_in_is_posInf = Wire(Vec(N, Bool()))
  val fp32_adderTree_in_is_negInf = Wire(Vec(N, Bool()))
  val fp32_adderTree_in_is_nan = Wire(Vec(N, Bool()))
  val fp32_adderTree_in_valid = Wire(Bool())

  val SigWidth_fp19_res = SigWidthFp19 + ExtendedWidthFp19 + 1
  val SigWidth_adderTree = SigWidthFp32 + ExtendedWidthFp32
  val fp19_res_sig_19to32 = Wire(Vec(N, UInt(SigWidth_adderTree.W)))
  for (i <- 0 until N) {
    if (SigWidth_fp19_res <= SigWidth_adderTree) {
      fp19_res_sig_19to32(i) := fp19_res(i).sig ## 0.U((SigWidth_adderTree - SigWidth_fp19_res).W)
    } else {
      fp19_res_sig_19to32(i) := fp19_res(i).sig.head(SigWidth_adderTree)
    }
  }

  fp32_adderTree_in_valid := fp19_res_valid || io.valid_in
  for (i <- 0 until N) {
    fp32_adderTree_in_is_posInf(i) := Mux(fp19_res_valid, fp19_res_is_posInf(i), is_inf_32(i) && !sign_in_32(i))
    fp32_adderTree_in_is_negInf(i) := Mux(fp19_res_valid, fp19_res_is_negInf(i), is_inf_32(i) && sign_in_32(i))
    fp32_adderTree_in_is_nan(i) := Mux(fp19_res_valid, fp19_res_is_nan(i), is_nan_32(i))
    fp32_adderTree_in(i).sign := Mux(fp19_res_valid, fp19_res(i).sign, sign_in_32(i))
    fp32_adderTree_in(i).exp := Mux(fp19_res_valid, fp19_res(i).exp, exp_adjust_subnorm_32(i))
    fp32_adderTree_in(i).sig := Mux(fp19_res_valid, fp19_res_sig_19to32(i), sig_adjust_subnorm_32(i))
  }

  //----------------------------
  // One layer of adder tree
  //----------------------------
  def oneAdderTreeLayer(data: Seq[FpExtFormat], is_posInf: Seq[Bool], is_negInf: Seq[Bool], is_nan: Seq[Bool], valid: Bool):
                       (Seq[FpExtFormat], Seq[Bool], Seq[Bool], Seq[Bool], Bool) = {
    val n = data.size
    require(n == is_posInf.size && n == is_negInf.size && n == is_nan.size, "data, is_posInf, is_negInf, and is_nan must have the same size")
    val adders = Seq.fill(n/2)(Module(new FAdd_extSig(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32,
                                                      ExtAreZeros = false, UseShiftRightJam = false)))
    val data_out = Reg(Vec(n/2, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32)))
    val is_posInf_out = Reg(Vec(n/2, Bool()))
    val is_negInf_out = Reg(Vec(n/2, Bool()))
    val is_nan_out = Reg(Vec(n/2, Bool()))
    val valid_out = Reg(Bool())
    for (i <- 0 until n/2) {
      adders(i).io.valid_in := valid
      adders(i).io.is_fp16 := false.B
      adders(i).io.a.sign := Mux(is_posInf(2*i), false.B, Mux(is_negInf(2*i), true.B, data(2*i).sign))
      adders(i).io.a.exp := data(2*i).exp
      adders(i).io.a.sig := data(2*i).sig
      adders(i).io.b.sign := Mux(is_posInf(2*i+1), false.B, Mux(is_negInf(2*i+1), true.B, data(2*i+1).sign))
      adders(i).io.b.exp := data(2*i+1).exp
      adders(i).io.b.sig := data(2*i+1).sig
      adders(i).io.a_is_inf := is_posInf(2*i) || is_negInf(2*i)
      adders(i).io.b_is_inf := is_posInf(2*i+1) || is_negInf(2*i+1)
      adders(i).io.a_is_nan := is_nan(2*i)
      adders(i).io.b_is_nan := is_nan(2*i+1)

      data_out(i).sign := RegEnable(adders(i).io.res.sign, adders(i).io.valid_out)
      data_out(i).exp := RegEnable(adders(i).io.res.exp, adders(i).io.valid_out)
      data_out(i).sig := RegEnable(adders(i).io.res.sig(SigWidthFp32 + ExtendedWidthFp32, 1), adders(i).io.valid_out)
      is_posInf_out(i) := RegEnable(adders(i).io.res_is_posInf, adders(i).io.valid_out)
      is_negInf_out(i) := RegEnable(adders(i).io.res_is_negInf, adders(i).io.valid_out)
      is_nan_out(i) := RegEnable(adders(i).io.res_is_nan, adders(i).io.valid_out)
      if (i == 0) { valid_out := RegNext(adders(i).io.valid_out) }
    }

    (data_out, is_posInf_out, is_negInf_out, is_nan_out, valid_out)
  }

  //---------------------------------
  // Construct the entire adder tree
  //---------------------------------
  def adderTreeGen(layerIdx: Int): (Seq[FpExtFormat], Seq[Bool], Seq[Bool], Seq[Bool], Bool) = {
    layerIdx match {
      case 0 => oneAdderTreeLayer(fp32_adderTree_in, fp32_adderTree_in_is_posInf, fp32_adderTree_in_is_negInf,
                                  fp32_adderTree_in_is_nan, fp32_adderTree_in_valid)
      case k => {
        val (dataPrev, posInfPrev, negInfPrev, nanPrev, validPrev) = adderTreeGen(k - 1)
        oneAdderTreeLayer(dataPrev, posInfPrev, negInfPrev, nanPrev, validPrev)
      }
    }
  }
  
  val (fp32_adderTree_out, fp32_adderTree_out_is_posInf, fp32_adderTree_out_is_negInf,
       fp32_adderTree_out_is_nan, fp32_adderTree_out_valid) = adderTreeGen(log2Ceil(N) - 1)

  //---------------------------------
  // Output the final result (already registered)
  //---------------------------------
  io.res.sign := fp32_adderTree_out(0).sign
  io.res.exp := fp32_adderTree_out(0).exp
  io.res.sig := fp32_adderTree_out(0).sig
  io.res_is_posInf := fp32_adderTree_out_is_posInf(0)
  io.res_is_negInf := fp32_adderTree_out_is_negInf(0)
  io.res_is_nan := fp32_adderTree_out_is_nan(0)
  io.valid_out := fp32_adderTree_out_valid
}