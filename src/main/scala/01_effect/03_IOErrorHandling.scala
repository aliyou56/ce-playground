package effect

import scala.util.{ Failure, Success, Try }

import cats.effect.IO

object IOErrorHandling {

  // IO: pure, delay, defer
  // create failed effects
  val aFailedCompute: IO[Int] = IO.delay(throw new RuntimeException("A Failure"))
  val aFailure: IO[Int]       = IO.raiseError(new RuntimeException("a proper fail"))

  // handle exceptions
  val dealWithIt = aFailure.handleErrorWith {
    case _: RuntimeException => IO.delay(println("I'm still here"))
  }

  // turn into an either
  val effectAsEither: IO[Either[Throwable, Int]] = aFailure.attempt

  // reedeem: transform the failure and the success in one go
  val resultAsString: IO[String] =
    aFailure.redeem(ex => s"FAIL : $ex", value => s"SUCCESS: $value")
  // redeemWith
  val resultAsEffect: IO[Unit] =
    aFailure.redeemWith(ex => IO(println(s"FAIL : $ex")), value => IO(println(s"SUCCESS: $value")))

  /** Exercices */

  // 1 - construct potentially failed IOs from standard data types (Option, Try, Either)
  def option2IO[A](opt: Option[A])(ifEmpty: Throwable): IO[A] =
    opt match {
      case Some(a) => IO.pure(a) // value has been already computed
      case None    => IO.raiseError(ifEmpty)
    }

  def try2IO[A](aTry: Try[A]): IO[A] =
    aTry match {
      case Success(a) => IO.pure(a)
      case Failure(e) => IO.raiseError(e)
    }

  def either2IO[A](anEither: Either[Throwable, A]): IO[A] =
    anEither match {
      case Right(a) => IO.pure(a)
      case Left(e)  => IO.raiseError(e)
    }

  // 2. handleError, handleErrorWith
  def handleIOError[A](io: IO[A])(handler: Throwable => A): IO[A] =
    io.redeem(handler, identity)

  def handleIOErrorWith[A](io: IO[A])(handler: Throwable => IO[A]): IO[A] =
    io.redeemWith(handler, IO.pure)

  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global

    //    aFailedCompute.unsafeRunSync()
    println(resultAsEffect.unsafeRunSync())
  }
}
