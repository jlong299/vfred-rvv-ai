package race.vpu.exu.laneexu.fp

import chisel3._
import chisel3.util._
import race.vpu._

class FpInfo(value: UInt, fpType: String) {
  require(fpType == "fp16" || fpType == "fp32" || fpType == "bf16", "Invalid fpType")
  private val (expWidth, fracWidth) = fpType match {
    case "fp16" => (5, 10)
    case "bf16" => (8, 7)
    case "fp32" => (8, 23)
    case _ => throw new Exception("Invalid fpType case")
  }
  require(value.getWidth == 1 + expWidth + fracWidth, "Invalid value width")
  private val exp = value.head(1 + expWidth).tail(1)
  private val frac = value(fracWidth - 1, 0)
  val sign = value.head(1).asBool

  val exp_is_0 = exp === 0.U
  val exp_is_all1s = exp.andR
  val frac_is_0 = frac === 0.U

  val isSubnorm = exp_is_0 && !frac_is_0
  val isZero = exp_is_0 && frac_is_0
  val isInf = exp_is_all1s && frac_is_0
  val isNan = exp_is_all1s && !frac_is_0
}

class ShiftRightJam(val len: Int) extends Module {
  val max_shift_width = log2Up(len + 1) 
  val io = IO(new Bundle() {
    val in = Input(UInt(len.W))
    val shamt = Input(UInt())
    val out = Output(UInt(len.W))
    val sticky = Output(Bool())
  })
  val exceed_max_shift = io.shamt > len.U
  val shamt = io.shamt(max_shift_width - 1, 0)
  val sticky_mask =
    ((1.U << shamt).asUInt - 1.U)(len - 1, 0) | Fill(len, exceed_max_shift)
  io.out := Mux(exceed_max_shift, 0.U, io.in >> io.shamt)
  io.sticky := (io.in & sticky_mask).orR 
}

object ShiftRightJam {
  def apply(x: UInt, shamt: UInt): (UInt, Bool) = {
    val shiftRightJam = Module(new ShiftRightJam(x.getWidth))
    shiftRightJam.io.in := x
    shiftRightJam.io.shamt := shamt
    (shiftRightJam.io.out, shiftRightJam.io.sticky)
  }
}