package race.vpu.yunsuan.util

import chisel3._
import chisel3.util._

object LZD {
  def apply(in: UInt): UInt = PriorityEncoder(Reverse(Cat(in, 1.U)))
}

class DelayNWithValid[T <: Data](gen: T, n: Int, hasInit: Boolean = true) extends Module{
  val io = IO(new Bundle(){
    val in_bits = Input(gen)
    val in_valid = Input(Bool())
    val out_bits = Output(gen)
    val out_valid = Output(Bool())
  })
  val (res_valid, res_bits) = (0 until n).foldLeft((io.in_valid, io.in_bits)) {
    (prev, _) =>
      val valid = Wire(Bool())
      if (hasInit) {
        valid := RegNext(prev._1, init = false.B)
      } else {
        valid := RegNext(prev._1)
      }
      val data = RegEnable(prev._2, prev._1)
      (valid, data)
  }
  io.out_valid := res_valid
  io.out_bits := res_bits
}

object DelayNWithValid{
  def apply[T <: Data](in: T, valid: Bool, n: Int): (Bool, T) = {
    val pipMod = Module(new DelayNWithValid(chiselTypeOf(in), n))
    pipMod.io.in_valid := valid
    pipMod.io.in_bits := in
    (pipMod.io.out_valid, pipMod.io.out_bits)
  }

  def apply[T <: Valid[Data]](in: T, n: Int): T = {
    val pipMod = Module(new DelayNWithValid(chiselTypeOf(in.bits), n))
    pipMod.io.in_valid := in.valid
    pipMod.io.in_bits := in.bits
    val res = Wire(chiselTypeOf(in))
    res.valid := pipMod.io.out_valid
    res.bits := pipMod.io.out_bits
    res
  }

  def apply[T <: Data](in: T, valid: Bool, n: Int, hasInit: Boolean): (Bool, T) = {
    val pipMod = Module(new DelayNWithValid(chiselTypeOf(in), n, hasInit = hasInit))
    pipMod.io.in_valid := valid
    pipMod.io.in_bits := in
    (pipMod.io.out_valid, pipMod.io.out_bits)
  }

  def apply[T <: Valid[Data]](in: T, n: Int, hasInit: Boolean): T = {
    val pipMod = Module(new DelayNWithValid(chiselTypeOf(in.bits), n, hasInit = hasInit))
    pipMod.io.in_valid := in.valid
    pipMod.io.in_bits := in.bits
    val res = Wire(chiselTypeOf(in))
    res.valid := pipMod.io.out_valid
    res.bits := pipMod.io.out_bits
    res
  }
}