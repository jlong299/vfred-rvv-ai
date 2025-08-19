/**
  * Vector Reduction supporting bf/fp16 and fp32
  */
//TODO: tail/mask handling
package race.vpu.exu.crosslane.fp

import chisel3._
import chisel3.util._
import race.vpu._
import VParams._
import race.vpu.yunsuan.util._
import race.vpu.exu.laneexu.fp._

class VFRed_16_32 extends Module {
  val ExtendedWidthFp19 = 12
  val ExtendedWidthFp32 = 12
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val vs2 = Input(UInt(VLEN.W))
    val vs1 = Input(UInt(32.W))
    val uop = Input(new VUop)
    val sewIn = Input(new SewFpOH)
    val valid_out = Output(Bool())
    val vd = Output(UInt(32.W))
    val uop_out = Output(new VUop)
    val fflags = Output(UInt(5.W))
  })

  val N = VLEN / 32
  val uop = io.uop
  val isSum = uop.ctrl.funct6(2, 1) === "b00".U
  val isMin = uop.ctrl.funct6(2, 1) === "b10".U
  val isMax = uop.ctrl.funct6(1)

  val vs2_32 = VecInit(UIntSplit(io.vs2, 32))

  val vfredCore = Module(new VFRedCore(N, ExtendedWidthFp19, ExtendedWidthFp32))
  vfredCore.io.valid_in := io.valid_in
  vfredCore.io.is_bf16 := io.sewIn.isBf16
  vfredCore.io.is_fp16 := io.sewIn.isFp16
  vfredCore.io.is_fp32 := io.sewIn.isFp32
  vfredCore.io.is_sum := isSum
  vfredCore.io.is_min := isMin
  vfredCore.io.is_max := isMax
  vfredCore.io.vs2 := vs2_32

  val Delay_core_minmax_fp32 = log2Ceil(2 * N) / 2
  val Delay_core_sum_fp32 = 2 * log2Ceil(N)
  
  //-----------------------------------------------------
  //  Delay chain of vs1[0] and uop. (Before accumulator)
  //-----------------------------------------------------
  class DelayChainBundle extends Bundle {
    val data = UInt(32.W)
    val uop = new VUop
    val sew = new SewFpOH
    val isSum, isMin, isMax = Bool()
  }

  val delayIn0 = Wire(ValidIO(new DelayChainBundle))
  delayIn0.valid := io.valid_in
  delayIn0.bits.data := io.vs1
  delayIn0.bits.uop := io.uop
  delayIn0.bits.sew := io.sewIn
  delayIn0.bits.isSum := isSum
  delayIn0.bits.isMin := isMin
  delayIn0.bits.isMax := isMax
  val delayIn1 = DelayNWithValid(delayIn0, 1)
  val delayIn2 = DelayNWithValid(delayIn1, 1)

  val delayIn2Mux = Mux1H(Seq(
    (delayIn0.valid && delayIn0.bits.sew.isFp32) -> delayIn0,
    (delayIn1.valid && delayIn1.bits.sew.is16 && !delayIn1.bits.isSum) -> delayIn1,
    (delayIn2.valid && delayIn2.bits.sew.is16 && delayIn2.bits.isSum) -> delayIn2,
  ))

  val delayMinMax = DelayNWithValid(delayIn2Mux, Delay_core_minmax_fp32)
  val delaySum = DelayNWithValid(delayMinMax, Delay_core_sum_fp32 - Delay_core_minmax_fp32)

  val delayOut = Mux1H(Seq(
    (delayMinMax.valid && !delayMinMax.bits.isSum) -> delayMinMax,
    (delaySum.valid && delaySum.bits.isSum) -> delaySum,
  ))
  assert(delayOut.valid === Mux(delayOut.bits.isSum,  vfredCore.io.valid_out_sum, vfredCore.io.valid_out_minmax), "VFReduction")

  val delayOut_info_fp16 = FpInfo(delayOut.bits.data(15, 0), "fp16")
  val delayOut_info_bf16 = FpInfo(delayOut.bits.data(15, 0), "bf16")
  val delayOut_info_fp32 = FpInfo(delayOut.bits.data, "fp32")
  val delayOut_pInf_nInf_nan = Mux1H(Seq(
    delayOut.bits.sew.isFp16 -> delayOut_info_fp16.pInf_nInf_nan,
    delayOut.bits.sew.isBf16 -> delayOut_info_bf16.pInf_nInf_nan,
    delayOut.bits.sew.isFp32 -> delayOut_info_fp32.pInf_nInf_nan,
  ))
  // data : when fp/bf16, add zeros to 32 + ext
  val delayOut_data = Wire(new FpExtFormat(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32))
  when (delayOut.bits.sew.isFp32 || delayOut.bits.uop.ctrl.widen) {
    delayOut_data.sign := delayOut.bits.data(31)
    delayOut_data.exp := Mux(delayOut_info_fp32.isSubnorm, 1.U, delayOut.bits.data(30, 23))
    delayOut_data.sig := Mux(delayOut_info_fp32.isSubnorm, 0.U(1.W), 1.U(1.W)) ## delayOut.bits.data(22, 0)
  }.elsewhen (delayOut.bits.sew.isBf16) {
    delayOut_data.sign := delayOut.bits.data(15)
    delayOut_data.exp := Mux(delayOut_info_bf16.isSubnorm, 1.U, delayOut.bits.data(14, 7))
    delayOut_data.sig := Mux(delayOut_info_bf16.isSubnorm, 0.U(1.W), 1.U(1.W)) ## delayOut.bits.data(6, 0) ##
                             0.U((16 + ExtendedWidthFp32).W)
  }.otherwise {
    delayOut_data.sign := delayOut.bits.data(15)
    delayOut_data.exp := Mux(delayOut_info_fp16.isSubnorm, 1.U, delayOut.bits.data(14, 10))
    delayOut_data.sig := Mux(delayOut_info_fp16.isSubnorm, 0.U(1.W), 1.U(1.W)) ## delayOut.bits.data(9, 0) ##
                             0.U((13 + ExtendedWidthFp32).W)
  }

  //------------------------------
  // Accumlator of min/max result
  //------------------------------
  //                    ----------                          | <-- delayOut
  // vfredCore.out ---> | min/max | <--- accMinmaxIn -- Mux |
  //                    ----------                          | <---
  //                         |                                   |
  //                         v                                   |
  //         (Register) accMinmaxReg------------------------------
  val accMinmaxIn = Wire(ValidIO(new DelayChainBundle))
  val accMinmaxReg = Reg(ValidIO(new DelayChainBundle))
  when (reset.asBool) {
    accMinmaxReg.valid := false.B
  }.otherwise {
    accMinmaxReg.valid := delayOut.valid && !delayOut.bits.isSum
  }
  val accMinmaxRes = MinMaxFP(vfredCore.io.resMinMax, accMinmaxIn.bits.data, accMinmaxIn.bits.isMax)
  when (delayOut.valid) {
    accMinmaxReg.bits := accMinmaxIn.bits
    accMinmaxReg.bits.data := accMinmaxRes
  }
  val delayOutMinmax = WireDefault(delayOut)
  delayOutMinmax.bits.data := Mux(delayOut.bits.sew.isFp32, delayOut.bits.data, delayOut.bits.data(15, 0) ## 0.U(16.W))
  accMinmaxIn := Mux(delayOut.bits.uop.uopIdx === 0.U, delayOutMinmax, accMinmaxReg)
  val validOutMinmax = accMinmaxReg.valid && accMinmaxReg.bits.uop.uopEnd


  //--------------------------
  // Accumlator of sum result
  //--------------------------
  //                    -----------                      | <-- delayOut
  // vfredCore.out ---> |   sum   | <--- accSumIn -- Mux | <-- 0
  //                    | 1 delay |                      | <-----
  //                    -----------                      | <--  |
  //                      /     \                            |  |
  //               --------     -------                      |  |
  //               | even |-----| odd |----------------------|  |
  //               --------     ------- ------------------------|
  //                      \     /
  //                        sum
  //                         |
  class accSumBundle extends Bundle {
    val data = new FpExtFormat(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32)
    val uop = new VUop
    val sew = new SewFpOH
    val pInf_nInf_nan = UInt(3.W) // Cat(isPosInf, isNegInf, isNan)
  }
  val accSumRegEven, accSumRegOdd = Reg(ValidIO(new accSumBundle))
  
  val accAdder = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32,
                                        ExtAreZeros = false, UseShiftRightJam = false))
  accAdder.io.valid_in := delayOut.valid && delayOut.bits.isSum
  accAdder.io.is_fp16 := delayOut.bits.sew.isFp16
  accAdder.io.a := vfredCore.io.resSum
  accAdder.io.a_is_posInf := vfredCore.io.resSum_is_posInf
  accAdder.io.a_is_negInf := vfredCore.io.resSum_is_negInf
  accAdder.io.a_is_nan := vfredCore.io.resSum_is_nan
  accAdder.io.b := Mux(delayOut.bits.uop.uopIdx === 0.U, delayOut_data,
                   Mux(delayOut.bits.uop.uopIdx === 1.U, 0.U,
                   Mux(!delayOut.bits.uop.uopIdx(0), accSumRegEven.bits.data, accSumRegOdd.bits.data)))
  accAdder.io.b_is_posInf := delayOut_pInf_nInf_nan(2)
  accAdder.io.b_is_negInf := delayOut_pInf_nInf_nan(1)
  accAdder.io.b_is_nan := delayOut_pInf_nInf_nan(0)

  val uop_accAdderOut = RegEnable(delayOut.bits.uop, delayOut.valid)
  val sew_accAdderOut = RegEnable(delayOut.bits.sew, delayOut.valid)
  when (reset.asBool) {
    accSumRegEven.valid := false.B
    accSumRegOdd.valid := false.B
  }.otherwise {
    accSumRegEven.valid := accAdder.io.valid_out && !uop_accAdderOut.uopIdx(0)
    accSumRegOdd.valid := accAdder.io.valid_out && uop_accAdderOut.uopIdx(0)
  }
  when (accAdder.io.valid_out && !uop_accAdderOut.uopIdx(0)) {
    accSumRegEven.bits.data := accAdder.io.res
    accSumRegEven.bits.uop := uop_accAdderOut
    accSumRegEven.bits.sew := sew_accAdderOut
    accSumRegEven.bits.pInf_nInf_nan := Cat(accAdder.io.res_is_posInf, accAdder.io.res_is_negInf, accAdder.io.res_is_nan)
  }
  when (accAdder.io.valid_out && uop_accAdderOut.uopIdx(0)) {
    accSumRegOdd.bits.data := accAdder.io.res
    accSumRegOdd.bits.uop := uop_accAdderOut
    accSumRegOdd.bits.sew := sew_accAdderOut
    accSumRegOdd.bits.pInf_nInf_nan := Cat(accAdder.io.res_is_posInf, accAdder.io.res_is_negInf, accAdder.io.res_is_nan)
  }

  val finalSumReg = Reg(ValidIO(new accSumBundle))
  val onlyOneUop = accSumRegEven.bits.uop.uopIdx === 0.U && accSumRegEven.bits.uop.uopEnd &&
                   accSumRegEven.valid
  val finalAdder = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32,
                                        ExtAreZeros = false, UseShiftRightJam = false))
  finalAdder.io.valid_in := accSumRegEven.valid && accSumRegEven.bits.uop.uopEnd ||
                            accSumRegOdd.valid && accSumRegOdd.bits.uop.uopEnd
  finalAdder.io.is_fp16 := accSumRegEven.bits.sew.isFp16
  finalAdder.io.a := accSumRegEven.bits.data
  finalAdder.io.a_is_posInf := accSumRegEven.bits.pInf_nInf_nan(2)
  finalAdder.io.a_is_negInf := accSumRegEven.bits.pInf_nInf_nan(1)
  finalAdder.io.a_is_nan := accSumRegEven.bits.pInf_nInf_nan(0)
  finalAdder.io.b := Mux(onlyOneUop, 0.U.asTypeOf(chiselTypeOf(accSumRegOdd.bits.data)), accSumRegOdd.bits.data)
  finalAdder.io.b_is_posInf := Mux(onlyOneUop, false.B, accSumRegOdd.bits.pInf_nInf_nan(2))
  finalAdder.io.b_is_negInf := Mux(onlyOneUop, false.B, accSumRegOdd.bits.pInf_nInf_nan(1))
  finalAdder.io.b_is_nan := Mux(onlyOneUop, false.B, accSumRegOdd.bits.pInf_nInf_nan(0))

  val uop_finalAdderOut = RegEnable(accSumRegEven.bits.uop, finalAdder.io.valid_in)
  val sew_finalAdderOut = RegEnable(accSumRegEven.bits.sew, finalAdder.io.valid_in)
  when (reset.asBool) {
    finalSumReg.valid := false.B
  }.otherwise {
    finalSumReg.valid := finalAdder.io.valid_out
  }
  when (finalAdder.io.valid_out) {
    finalSumReg.bits.data := finalAdder.io.res
    finalSumReg.bits.uop := uop_finalAdderOut
    finalSumReg.bits.sew := sew_finalAdderOut
    finalSumReg.bits.pInf_nInf_nan := Cat(finalAdder.io.res_is_posInf, finalAdder.io.res_is_negInf, finalAdder.io.res_is_nan)
  }

  //----- Rounding of final adder result -----
  





  io.valid_out := validOutMinmax || true.B
  io.uop_out := Mux1H(Seq(
    validOutMinmax -> accMinmaxReg.bits.uop,
  ))
  io.vd := Mux1H(Seq(
    validOutMinmax -> Mux(accMinmaxReg.bits.sew.isFp32, accMinmaxReg.bits.data, accMinmaxReg.bits.data.head(16)),
  ))
  io.fflags := 0.U
}


object VerilogVFRed_16_32 extends App {
  println("Generating the VFRed_16_32 hardware")
  emitVerilog(new VFRed_16_32, Array("--target-dir", "build/verilog_vfred_16_32"))
}