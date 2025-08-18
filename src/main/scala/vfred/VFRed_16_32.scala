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
  val widen = uop.ctrl.widen
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

  val delayOut_info_f16 = 0.U

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
  }
  val accSumIn = Wire(ValidIO(new DelayChainBundle))
  val accSumRegEven, accSumRegOdd = Reg(ValidIO(new accSumBundle))
  val accAdder = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32,
                                        ExtAreZeros = false, UseShiftRightJam = false))
  accAdder.io.valid_in := delayOut.valid && delayOut.bits.isSum
  accAdder.io.is_fp16 := delayOut.bits.sew.isFp16
  accAdder.io.a := vfredCore.io.resSum
  accAdder.io.a_is_posInf := vfredCore.io.resSum_is_posInf
  accAdder.io.a_is_negInf := vfredCore.io.resSum_is_negInf
  accAdder.io.a_is_nan := vfredCore.io.resSum_is_nan
  val uop_accAdderOut = RegEnable(delayOut.bits.uop, delayOut.valid)
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
  }
  when (accAdder.io.valid_out && uop_accAdderOut.uopIdx(0)) {
    accSumRegOdd.bits.data := accAdder.io.res
    accSumRegOdd.bits.uop := uop_accAdderOut
  }
  // accSumIn := Mux(delayOut)

  val finalSumReg = Reg(ValidIO(new accSumBundle))
  val finalAdder = Module(new FAdd_extSig(ExpWidth = 8, SigWidth = 24, ExtendedWidth = ExtendedWidthFp32,
                                        ExtAreZeros = false, UseShiftRightJam = false))
  val uop_finalAdderOut = RegEnable(delayOut.bits.uop, delayOut.valid)





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