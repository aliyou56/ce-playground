package effect

import cats.Parallel
import cats.effect.{ IO, IOApp }

object IOParallelism extends IOApp.Simple {

  val io1 = IO(s"[${Thread.currentThread().getName}] io1")
  val io2 = IO(s"[${Thread.currentThread().getName}] io2")

  val composedIO = for {
    a <- io1
    b <- io2
  } yield s"$a and $b love scala"

  import utils._             // debug extension method
  import cats.syntax.apply._ // mapN extension method

  val meaningOfLife: IO[Int] = IO.delay(42)
  val favLang: IO[String]    = IO.delay("scala")
  val goalInLife =
    (meaningOfLife.debug, favLang.debug).mapN((num, str) => s"my goal in life is $num and $str")

  // parallelism on IOs
  // convert a sequential IO to parallel IO
  def parIO1: IO.Par[Int]    = Parallel[IO].parallel(meaningOfLife.debug)
  def parIO2: IO.Par[String] = Parallel[IO].parallel(favLang.debug)
  import cats.effect.implicits._

  val goalInLifePar: IO.Par[String] =
    (parIO1, parIO2).mapN((num, str) => s"my goal in life is $num and $str")

  // turn back to sequential
  val goalInLife_v2: IO[String] = Parallel[IO].sequential(goalInLifePar)

  // shorthand:
  import cats.syntax.parallel._
  val goalInLife_v3: IO[String] =
    (meaningOfLife.debug, favLang.debug).parMapN { (num, str) =>
      s"my goal in life is $num and $str"
    }

  // regarding failure
  val aFailure: IO[String] = IO.raiseError(new RuntimeException("Boom"))
  // compose success + failure
  val parallelWithFailure =
    (meaningOfLife.debug, aFailure.debug).parMapN((n, s) => s"$n$s")

  // compose failure + failure
  val anotherFailure: IO[String] = IO.raiseError(new RuntimeException("2x Boom"))
  val parFailures: IO[String]    = (IO(Thread.sleep(2000)) >> aFailure, anotherFailure).parMapN(_ + _)

  override def run: IO[Unit] =
    parFailures.debug.void
}
