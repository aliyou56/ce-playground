package concurrency

import scala.concurrent.duration._

import cats.effect.{ IO, IOApp }

/** Uncancelable calls are MASKS which suppress cancellation
  * Poll calls are "gaps opened" in the uncancelable region.
  */
object CancellingIOs extends IOApp.Simple {

  import utils._

  /** Cancelling IO
    *  - fib.cancel
    *  - IO.race & other APIs
    *  - manual cancellation
    */
  val chainOfIOs: IO[Int] = IO("waiting").debug >> IO.canceled >> IO(42).debug

  // uncancelable
  // example: o,line store, payment processor
  // payment process MUST NOT be cancelled
  val specialPaymentSystem = (
    IO("Payment running, dont't cancel me...").debug >>
      IO.sleep(1.second) >>
      IO("Payment completed.").debug
  ).onCancel(IO("MEGA CANCEL of DOOM!").debug.void)

  // uncancelable take a function from Poll[IO] to IO
  // The poll object can be used to mark sections within the returned effect which CAN BE CANCELLED
  val atomicPayment    = IO.uncancelable(_ => specialPaymentSystem) // "masking"
  val atomicPayment_v2 = specialPaymentSystem.uncancelable

  val cancellationOfDoom =
    for {
//    fib <- specialPaymentSystem.start
      fib <- atomicPayment.start
      _   <- IO.sleep(500.millis) >> IO("attempting cancellation ...").debug >> fib.cancel
      _   <- fib.join
    } yield ()

  /** Example: authentification service
    * - input password, can be cancelled, because otherwise we might block indefinitely on user input
    * - verify password, CANNOT be cancelled once it's started
    */
  val inputPassword =
    IO("Input password:").debug >>
      IO("(typing password)").debug >>
      IO.sleep(3.seconds) >>
      IO("secret")

  val verifyPassword =
    (pw: String) =>
      IO("verifying...").debug >>
        IO.sleep(2.seconds) >>
        IO(pw == "secret")

  val authFlow: IO[Unit] = IO.uncancelable { poll =>
    for {
      pw <- poll(inputPassword)
        .onCancel(IO("Authentification timed out. Try again later").debug.void) // cancellable
      verified <- verifyPassword(pw)
      _ <-
        if (verified) IO("Authentification successful").debug
        else IO("Authentification failed").debug
    } yield ()
  }

  val authProgram =
    for {
      authFib <- authFlow.start
      _ <- IO.sleep(2.seconds) >>
        IO("Authentification timeout, attempting cancel ...").debug >> authFib.cancel
      _ <- authFib.join
    } yield ()

  /** Exercises
    */
  // 1. uncancelable eliminates ALL cancel points
  val cancelBeforeMol = IO.canceled >> IO(42).debug
  val uncancelableMol = IO.uncancelable(_ => IO.canceled >> IO(42).debug) // 42

  // 2.
  val invincibleAuthProgram =
    for {
      authFib <- IO.uncancelable(_ => authFlow.start)
      _ <- IO.sleep(3.seconds) >>
        IO("Authentification timeout, attempting cancel ...").debug >> authFib.cancel
      _ <- authFib.join
    } yield ()

  // 3.
  def threeStepProgram: IO[Unit] = {
    val sequence = IO.uncancelable { poll =>
      poll(
        IO("cancelable").debug >>
          IO.sleep(1.second)
      ) >>
        IO("cancelable end").debug >>
        IO("uncancelable").debug >>
        IO.sleep(1.second) >>
        IO("uncancelable end").debug >>
        poll(
          IO("second uncancelable").debug >>
            IO.sleep(1.second) >>
            IO("second cancellable end").debug
        )
    }

    for {
      fib <- sequence.start
      _   <- IO.sleep(1500.millis) >> IO("CANCELING").debug >> fib.cancel
      _   <- fib.join
    } yield ()
  }

  override def run: IO[Unit] =
//    cancellationOfDoom
//    authFlow
//    authProgram
//    uncancelableMol.void
//    invincibleAuthProgram
    threeStepProgram
}
