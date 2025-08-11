// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import scalalib._
// support BSP
import mill.bsp._

object ivys{
  val sv = "2.13.13"
  val chisel3 = ivy"edu.berkeley.cs::chisel3:3.6.1"
  val chisel3Plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.6.1"
  val chiseltest = ivy"edu.berkeley.cs::chiseltest:0.6.2"
  val chiselCirct = ivy"com.sifive::chisel-circt:0.6.0"
  val scalatest = ivy"org.scalatest::scalatest:3.2.2"
}

trait VfpuModule extends ScalaModule {
  override def scalaVersion = ivys.sv

  override def scalacOptions = Seq("-Xsource:2.13")

  override def ivyDeps = Agg(ivys.chisel3, ivys.chiselCirct)

  override def scalacPluginIvyDeps = Agg(ivys.chisel3Plugin)
}

object vfpu extends VfpuModule  {
  override def millSourcePath = os.pwd
}