/**
  * Vector Reduction supporting bf/fp16 and fp32
  */
//TODO: tail/mask handling
package race.vpu.exu.laneexu.fp

import chisel3._
import chisel3.util._
import race.vpu._
import VParams._

class VFRed_16_32 extends Module {
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val vs2 = Input(UInt(VLEN.W))
    val vs1 = Input(UInt(32.W))
    val uop = Input(new VUop)
    val sewIn = Input(new SewFpOH)
    val valid_out = Output(Bool())
    val vd = Output(UInt(32.W))
    val fflags = Output(UInt(5.W))
  })

  val N = VLEN / 32
  val uop = io.uop
  val widen = uop.ctrl.widen
  val is_sum = uop.ctrl.funct6(2, 1) === "b00".U
  val is_min = uop.ctrl.funct6(2, 1) === "b10".U
  val is_max = uop.ctrl.funct6(1)

  val vs2_32 = VecInit(UIntSplit(io.vs2, 32))

  val vfred_core = Module(new VFRedCore(N))
  vfred_core.io.valid_in := io.valid_in
  vfred_core.io.is_bf16 := io.sewIn.isBf16
  vfred_core.io.is_fp16 := io.sewIn.isFp16
  vfred_core.io.is_fp32 := io.sewIn.isFp32
  vfred_core.io.is_sum := is_sum
  vfred_core.io.is_min := is_min
  vfred_core.io.is_max := is_max
  vfred_core.io.vs2 := vs2_32

  val Delay_core_minmax_fp32 = log2Ceil(2 * N) / 2
  val Delay_core_sum_fp32 = 2 * log2Ceil(N)
  
  //-----------------------------------------------------
  //  Delay chain of vs1[0] and uop. (Before accumulator)
  //-----------------------------------------------------
  val 






}