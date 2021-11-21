package polymorphic

import cats.effect.kernel.Outcome.{ Canceled, Errored, Succeeded }
import cats.effect.kernel.{ MonadCancel, Poll }
import cats.effect.{ IO, IOApp }
import cats.{ Applicative, Monad }

object PolymorphicCancellation extends IOApp.Simple {
  trait MyApplicativeError[F[_], E] extends Applicative[F] {
    def raiseError[A](error: E): F[A]
    def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]
  }

  trait MyMonadError[F[_], E] extends MyApplicativeError[F, E] with Monad[F]

  trait MyPoll[F[_]] {
    def apply[A](fa: F[A]): F[A]
  }

  trait MyMonadCancel[F[_], E] extends MyMonadError[F, E] {
    def canceled: F[Unit]
    def uncancelable[A](poll: Poll[F] => F[A]): F[A]
  }

  // monadCancel for IO
  val monadCancelIO: MonadCancel[IO, Throwable] = MonadCancel[IO]

  // creating values
  val molIO: IO[Int]          = monadCancelIO.pure(42)
  val ambitiousMolIO: IO[Int] = monadCancelIO.map(molIO)(_ * 10)

  val mustCompute: IO[Int] = monadCancelIO.uncancelable { _ =>
    for {
      _   <- monadCancelIO.pure("once started, I can't go back")
      res <- monadCancelIO.pure(56)
    } yield res
  }

  import cats.syntax.flatMap._
  import cats.syntax.functor._

  // generalize the code
  def mustComputeGeneral[F[_], E](using mc: MonadCancel[F, E]): F[Int] =
    mc.uncancelable { _ =>
      for {
        _   <- mc.pure("once started, I can't go back")
        res <- mc.pure(56)
      } yield res
    }

  val mustCompute_v2: IO[Int] = mustComputeGeneral[IO, Throwable]

  // allow cancellation listeners
  val mustComputeWithListener = mustCompute.onCancel(IO("I'm being cancelled!").void)
  val mustComputeWithListener_v2 =
    monadCancelIO.onCancel(mustCompute, IO("I'm being cancelled!").void)
  import cats.effect.syntax.monadCancel._ // onCancel as extension method

  // allow finalizers: guarantee, guaranteeCase
  val aComputationFinalizers =
    monadCancelIO.guaranteeCase(IO(42)) {
      case Succeeded(fa) => fa.flatMap(a => IO(s"successful: $a").void)
      case Errored(e)    => IO(s"failed: $e").void
      case Canceled()    => IO("canceled").void
    }

  // bracket pattern is specific to monadCancel
  val aComputationWithUsage =
    monadCancelIO.bracket(IO(42)) { value =>
      IO(s"Using the meaning of life: $value")
    } { value =>
      IO("releasing the meaning of life...").void
    }

  /**
   * Exercise - generalize a piece of code
   */
  import utils.general.*
  import scala.concurrent.duration.*

  def unsafeSleep[F[_], E](duration: FiniteDuration)(using mc: MonadCancel[F, E]): F[Unit] =
    mc.pure(Thread.sleep(duration.toMillis)) // not semantic blocking

  def inputPassword[F[_], E](using mc: MonadCancel[F, E]): F[String] =
    for {
      _      <- mc.pure("Input password:").debug
      _      <- mc.pure("(typing password)").debug
      _      <- unsafeSleep[F, E](5.seconds)
      secret <- mc.pure("secret")
    } yield secret

  def verifyPassword[F[_], E](pw: String)(using mc: MonadCancel[F, E]): F[Boolean] =
    for {
      _        <- mc.pure("verifying...").debug
      _        <- unsafeSleep[F, E](2.seconds)
      verified <- mc.pure(pw == "secret")
    } yield verified

  def authFlow[F[_], E](using mc: MonadCancel[F, E]): F[Unit] =
    mc.uncancelable { poll =>
      for {
        pw <- poll(inputPassword)
          .onCancel(
            mc.pure("Authentification timed out. Try again later").debug.void
          ) // cancellable
        verified <- verifyPassword(pw)
        _ <-
          if (verified) mc.pure("Authentification successful").debug
          else mc.pure("Authentification failed").debug
      } yield ()
    }

  def authProgram: IO[Unit] =
    for {
      authFib <- authFlow[IO, Throwable].start

      _ <-
        IO.sleep(3.seconds) >>
          IO("Authentification timeout, attempting cancel ...").debug >>
          authFib.cancel

      _ <- authFib.join
    } yield ()

  override def run: IO[Unit] = authProgram
}
