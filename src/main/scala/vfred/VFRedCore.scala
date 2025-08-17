/**
  * Vector FP Reduction Core
  *   Input: N * FP32 or 2N * BF(FP)16
  *   Output: one FP32 (SUM or MIN/MAX)
  * 
  *   Note: for bf/fp16 min/max, the output resides in the upper 16 bits of the 32-bit result
  * 
  * Pipeline:   |      |              |      | 
  *        ---->|----->|--- ... ----->|----->|
  *         S0  |  S1  |              |S(D-1)|
  *   For min/max, if N == 4^k,     D = log4(N) + {if (fp32) 0 else 1}
  *                if N == 4^k * 2, D = log4(N/2) + 1 + {if (fp32) 0 else 1}
  *   For sum, D = 2 * ( log2(N) + {if (fp32) 0 else 1} )
  */
package race.vpu.exu.crosslane.fp

import chisel3._
import chisel3.util._
import race.vpu._
import race.vpu.exu.laneexu.fp._

object MinMaxFP {
  def apply(a: UInt, b: UInt, isMax: Bool): UInt = {
    val len = a.getWidth
    require(len == b.getWidth, "a and b must have the same width")
    val sign_a = a.head(1).asBool
    val sign_b = b.head(1).asBool
    val body_a = a.tail(1)
    val body_b = b.tail(1)
    val body_a_gt_b = body_a > body_b
    val res = Wire(UInt(len.W))
    when (sign_a =/= sign_b) {
      res := Mux(sign_a && isMax || !sign_a && !isMax, b, a)
    }.otherwise {
      when (body_a_gt_b) {
        res := Mux(!sign_a && isMax || sign_a && !isMax, a, b)
      }.otherwise {
        res := Mux(!sign_a && isMax || sign_a && !isMax, b, a)
      }
    }
    res
  }
}

class VFRedCore(
  N: Int = 64, // N = VLEN / 32
  ExtendedWidthFp19: Int = 12,
  ExtendedWidthFp32: Int = 12
) extends Module {
  val SigWidthFp19 = 10 + 1  // Fixed
  val SigWidthFp32 = 23 + 1  // Fixed
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val is_bf16, is_fp16, is_fp32 = Input(Bool())
    val is_sum, is_max, is_min = Input(Bool())
    val vs2 = Input(Vec(N, UInt(32.W)))
    val resSum = Output(new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32))
    val resSum_is_posInf, resSum_is_negInf, resSum_is_nan = Output(Bool())
    val valid_out_sum = Output(Bool())
    val resMinMax = Output(UInt(32.W))
    val valid_out_minmax = Output(Bool())
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
  val fp19_res = Wire(Vec(N, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp19, ExtendedWidth = ExtendedWidthFp19 + 1)))
  val fp19_res_is_posInf = Wire(Vec(N, Bool()))
  val fp19_res_is_negInf = Wire(Vec(N, Bool()))
  val fp19_res_is_nan = Wire(Vec(N, Bool()))
  val fp19_res_valid = Wire(Bool())
  for (i <- 0 until N) {
    val fadd_extSig_fp19 = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = SigWidthFp19, ExtendedWidth = ExtendedWidthFp19, ExtAreZeros = true, UseShiftRightJam = false))
    fadd_extSig_fp19.io.valid_in := io.valid_in && is_16 && io.is_sum
    fadd_extSig_fp19.io.is_fp16 := is_fp16
    fadd_extSig_fp19.io.a.sign := sign_in_16(2*i)
    fadd_extSig_fp19.io.a.exp := exp_adjust_subnorm_16(2*i)
    fadd_extSig_fp19.io.a.sig := sig_adjust_subnorm_16(2*i) ## 0.U(ExtendedWidthFp19.W)
    fadd_extSig_fp19.io.b.sign := sign_in_16(2*i+1)
    fadd_extSig_fp19.io.b.exp := exp_adjust_subnorm_16(2*i+1)
    fadd_extSig_fp19.io.b.sig := sig_adjust_subnorm_16(2*i+1) ## 0.U(ExtendedWidthFp19.W)
    fadd_extSig_fp19.io.a_is_posInf := is_inf_16(2*i) && !sign_in_16(2*i)
    fadd_extSig_fp19.io.b_is_posInf := is_inf_16(2*i+1) && !sign_in_16(2*i+1)
    fadd_extSig_fp19.io.a_is_negInf := is_inf_16(2*i) && sign_in_16(2*i)
    fadd_extSig_fp19.io.b_is_negInf := is_inf_16(2*i+1) && sign_in_16(2*i+1)
    fadd_extSig_fp19.io.a_is_nan := is_nan_16(2*i)
    fadd_extSig_fp19.io.b_is_nan := is_nan_16(2*i+1)

    fp19_res(i) := RegEnable(fadd_extSig_fp19.io.res, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_posInf(i) := RegEnable(fadd_extSig_fp19.io.res_is_posInf, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_negInf(i) := RegEnable(fadd_extSig_fp19.io.res_is_negInf, fadd_extSig_fp19.io.valid_out)
    fp19_res_is_nan(i) := RegEnable(fadd_extSig_fp19.io.res_is_nan, fadd_extSig_fp19.io.valid_out)
    if (i == 0) { fp19_res_valid := RegNext(fadd_extSig_fp19.io.valid_out, init = false.B) }
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
  class InfoFPSpecial extends Bundle {
    val is_posInf = Bool()
    val is_negInf = Bool()
    val is_nan = Bool()
  }
  val fp32_adderTree_in = Wire(Vec(N, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32)))
  val fp32_adderTree_in_info = Wire(Vec(N, new InfoFPSpecial()))
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

  fp32_adderTree_in_valid := fp19_res_valid || io.valid_in && io.is_sum
  for (i <- 0 until N) {
    fp32_adderTree_in_info(i).is_posInf := Mux(fp19_res_valid, fp19_res_is_posInf(i), is_inf_32(i) && !sign_in_32(i))
    fp32_adderTree_in_info(i).is_negInf := Mux(fp19_res_valid, fp19_res_is_negInf(i), is_inf_32(i) && sign_in_32(i))
    fp32_adderTree_in_info(i).is_nan := Mux(fp19_res_valid, fp19_res_is_nan(i), is_nan_32(i))
    fp32_adderTree_in(i).sign := Mux(fp19_res_valid, fp19_res(i).sign, sign_in_32(i))
    fp32_adderTree_in(i).exp := Mux(fp19_res_valid, fp19_res(i).exp, exp_adjust_subnorm_32(i))
    fp32_adderTree_in(i).sig := Mux(fp19_res_valid, fp19_res_sig_19to32(i), sig_adjust_subnorm_32(i))
  }

  //----------------------------
  // One layer of adder tree
  //----------------------------
  def oneAdderTreeLayer(data: Seq[FpExtFormat], info: Seq[InfoFPSpecial], valid: Bool):
                       (Seq[FpExtFormat], Seq[InfoFPSpecial], Bool) = {
    val n = data.size
    require(n == info.size, "data and info must have the same size")
    val adders = Seq.fill(n/2)(Module(new FAdd_extSig(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32,
                                                      ExtAreZeros = false, UseShiftRightJam = false)))
    val data_out = Wire(Vec(n/2, new FpExtFormat(ExpWidth = 8, SigWidth = SigWidthFp32, ExtendedWidth = ExtendedWidthFp32)))
    val info_out = Wire(Vec(n/2, new InfoFPSpecial()))
    for (i <- 0 until n/2) {
      adders(i).io.valid_in := valid
      adders(i).io.is_fp16 := false.B
      adders(i).io.a.sign := data(2*i).sign
      adders(i).io.a.exp := data(2*i).exp
      adders(i).io.a.sig := data(2*i).sig
      adders(i).io.b.sign := data(2*i+1).sign
      adders(i).io.b.exp := data(2*i+1).exp
      adders(i).io.b.sig := data(2*i+1).sig
      adders(i).io.a_is_posInf := info(2*i).is_posInf
      adders(i).io.a_is_negInf := info(2*i).is_negInf
      adders(i).io.b_is_posInf := info(2*i+1).is_posInf
      adders(i).io.b_is_negInf := info(2*i+1).is_negInf
      adders(i).io.a_is_nan := info(2*i).is_nan
      adders(i).io.b_is_nan := info(2*i+1).is_nan

      data_out(i).sign := RegEnable(adders(i).io.res.sign, adders(i).io.valid_out)
      data_out(i).exp := RegEnable(adders(i).io.res.exp, adders(i).io.valid_out)
      data_out(i).sig := RegEnable(adders(i).io.res.sig(SigWidthFp32 + ExtendedWidthFp32, 1), adders(i).io.valid_out)
      info_out(i).is_posInf := RegEnable(adders(i).io.res_is_posInf, adders(i).io.valid_out)
      info_out(i).is_negInf := RegEnable(adders(i).io.res_is_negInf, adders(i).io.valid_out)
      info_out(i).is_nan := RegEnable(adders(i).io.res_is_nan, adders(i).io.valid_out)
    }
    val valid_out = RegNext(adders(0).io.valid_out, init = false.B)

    (data_out, info_out, valid_out)
  }

  //---------------------------------
  // Construct the entire adder tree
  //---------------------------------
  def adderTreeGen(layerIdx: Int): (Seq[FpExtFormat], Seq[InfoFPSpecial], Bool) = {
    layerIdx match {
      case 0 => oneAdderTreeLayer(fp32_adderTree_in, fp32_adderTree_in_info, fp32_adderTree_in_valid) 
      case k => {
        val (dataPrev, infoPrev, validPrev) = adderTreeGen(k - 1)
        oneAdderTreeLayer(dataPrev, infoPrev, validPrev)
      }
    }
  }
  
  val (fp32_adderTree_out, fp32_adderTree_out_info, fp32_adderTree_out_valid) = adderTreeGen(log2Ceil(N) - 1)

  //---------------------------------
  // Output the final SUM result (already registered)
  //---------------------------------
  io.resSum.sign := fp32_adderTree_out(0).sign
  io.resSum.exp := fp32_adderTree_out(0).exp
  io.resSum.sig := fp32_adderTree_out(0).sig
  io.resSum_is_posInf := fp32_adderTree_out_info(0).is_posInf
  io.resSum_is_negInf := fp32_adderTree_out_info(0).is_negInf
  io.resSum_is_nan := fp32_adderTree_out_info(0).is_nan
  io.valid_out_sum := fp32_adderTree_out_valid

  //----------------------------------
  //  Min/Max Tree
  //----------------------------------
  // If input is bf/fp16, perform pairwise min/max to get N 16-bit results first.
  val f16_resMinMax = Wire(Vec(N, UInt(16.W)))
  val valid_in_minMax = io.valid_in && (io.is_min || io.is_max)
  for (i <- 0 until N) {
    f16_resMinMax(i) := RegEnable(MinMaxFP(vs2_16(2*i), vs2_16(2*i+1), io.is_max), valid_in_minMax)
  }
  val f16_resMinMax_valid = RegNext(valid_in_minMax, init = false.B)
  val f16_resMinMax_is_max = RegEnable(io.is_max, valid_in_minMax)

  // Inputs of the 1st layer of fp32 min/max tree
  val fp32_minMaxTree_in_valid = f16_resMinMax_valid || valid_in_minMax
  val fp32_minMaxTree_in_is_max = Mux(f16_resMinMax_valid, f16_resMinMax_is_max, io.is_max)
  val fp32_minMaxTree_in = Wire(Vec(N, UInt(32.W)))
  for (i <- 0 until N) {
    fp32_minMaxTree_in(i) := Mux(f16_resMinMax_valid, f16_resMinMax(i) ## 0.U(16.W), io.vs2(i))
  }
  
  // One layer of 2 -> 1 min/max tree (2 -> 1)
  def oneMinMaxTreeLayer2to1(data: Seq[UInt], valid: Bool, is_max: Bool): (Seq[UInt], Bool, Bool) = {
    val n = data.size
    val data_out = Wire(Vec(n/2, UInt(32.W)))
    for (i <- 0 until n/2) {
      data_out(i) := RegEnable(MinMaxFP(data(2*i), data(2*i+1), is_max), valid)
    }
    (data_out, RegNext(valid, init = false.B), RegEnable(is_max, valid))
  }
  // One layer of 4 -> 1 min/max tree
  def oneMinMaxTreeLayer4to1(data: Seq[UInt], valid: Bool, is_max: Bool): (Seq[UInt], Bool, Bool) = {
    val n = data.size
    val data_out_4to2 = Wire(Vec(n/2, UInt(32.W)))
    for (i <- 0 until n/2) {
      data_out_4to2(i) := MinMaxFP(data(2*i), data(2*i+1), is_max)
    }
    oneMinMaxTreeLayer2to1(data_out_4to2, valid, is_max)
  }

  //---- Min/Max Tree of FP32. If n == 4^k,     all use 4 -> 1 layers.
  //                           If n == 4^k * 2, the last layer use 2 -> 1 reduction.
  def minMaxTreeGen(layerIdx: Int): (Seq[UInt], Bool, Bool) = {
    layerIdx match {
      case 0 => oneMinMaxTreeLayer2to1(fp32_minMaxTree_in, fp32_minMaxTree_in_valid, fp32_minMaxTree_in_is_max)
      case 1 => oneMinMaxTreeLayer4to1(fp32_minMaxTree_in, fp32_minMaxTree_in_valid, fp32_minMaxTree_in_is_max)
      case k => {
        val (dataPrev, validPrev, isMaxPrev) = minMaxTreeGen(k - 2)
        oneMinMaxTreeLayer4to1(dataPrev, validPrev, isMaxPrev)
      }
    }
  }

  val (fp32_minMaxTree_out, fp32_minMaxTree_out_valid, fp32_minMaxTree_out_is_max) = minMaxTreeGen(log2Ceil(N) - 1)

  //---------------------------------
  // Output the final MIN/MAX result (already registered)
  //---------------------------------
  io.resMinMax := fp32_minMaxTree_out(0)
  io.valid_out_minmax := fp32_minMaxTree_out_valid
}

object VerilogVFRedCore extends App {
  println("Generating the VFRedCore hardware")
  emitVerilog(new VFRedCore(N = 64), Array("--target-dir", "build/verilog_vfred_core"))
}