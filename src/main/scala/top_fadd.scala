package top

import chisel3._
import chisel3.util._
import chisel3.stage._
import race.vpu._
import race.vpu.VParams._
import race.vpu.exu.laneexu.fp._

class top extends Module{
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val is_bf16, is_fp16, is_fp32 = Input(Bool())
    val is_widen, a_already_widen = Input(Bool())
    val a_in_32 = Input(UInt(32.W))
    val b_in_32 = Input(UInt(32.W))
    val a_in_16 = Input(Vec(2, UInt(16.W)))
    val b_in_16 = Input(Vec(2, UInt(16.W)))

    val res_out_32 = Output(UInt(32.W))
    val res_out_16 = Output(Vec(2, UInt(16.W)))
    val valid_out = Output(Bool())
  })

  val fadd = Module(new FAdd_16_32(3, 3))
  fadd.io.valid_in := io.valid_in
  fadd.io.is_bf16 := io.is_bf16
  fadd.io.is_fp16 := io.is_fp16
  fadd.io.is_fp32 := io.is_fp32
  fadd.io.is_widen := io.is_widen
  fadd.io.a_already_widen := io.a_already_widen

  when(io.is_fp32) {
    fadd.io.a := io.a_in_32
    fadd.io.b := io.b_in_32
  }.otherwise {
    fadd.io.a := Cat(io.a_in_16(1), io.a_in_16(0))
    fadd.io.b := Cat(io.b_in_16(1), io.b_in_16(0))
  }

  io.res_out_32 := fadd.io.res
  io.res_out_16 := VecInit(fadd.io.res(15, 0), fadd.io.res(31, 16))
  io.valid_out := fadd.io.valid_out
}

object top extends App {
  println("Generating the top FAdd hardware")
  (new ChiselStage).emitVerilog(new top, args)
}