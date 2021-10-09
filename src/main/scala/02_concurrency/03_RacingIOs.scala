package concurrency

import scala.concurrent.duration.*

import cats.effect.kernel.Outcome.{ Canceled, Errored, Succeeded }
import cats.effect.kernel.{ Fiber, Outcome }
import cats.effect.{ IO, IOApp }

object RacingIOs extends IOApp.Simple {

  import utils._

  def runWithSleep[A](value: A, duration: FiniteDuration): IO[A] =
    (
      IO(s"starting computation: $value").debug >>
        IO.sleep(duration) >>
        IO(s"computation for $value: done").debug >>
        IO(value)
    ).onCancel(IO(s"computation cancelled for $value").debug.void)

  def testRace = {
    val meaningOfLife                  = runWithSleep(42, 1.seconds)
    val favLang                        = runWithSleep("Scala", 2.seconds)
    val first: IO[Either[Int, String]] = IO.race(meaningOfLife, favLang)

    first.flatMap {
      case Left(mol)  => IO(s"Meaning of life won: $mol").debug
      case Right(fav) => IO(s"Fav lang won: $fav").debug
    }
  }

  def testRacePair = {
    val meaningOfLife                  = runWithSleep(42, 1.seconds)
    val favLang                        = runWithSleep("Scala", 2.seconds)
    val raceResult: IO[Either[
      (Outcome[IO, Throwable, Int], Fiber[IO, Throwable, String]),
      (Fiber[IO, Throwable, Int], Outcome[IO, Throwable, String])
    ]] = IO.racePair(meaningOfLife, favLang)

    raceResult.flatMap {
      case Left((outMmol, fibLang)) => fibLang.cancel >> IO("Mol won").debug >> IO(outMmol).debug
      case Right((fibMol, outLang)) => fibMol.cancel >> IO("Lang won").debug >> IO(outLang).debug
    }
  }

  /** Exercise */
  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val result = IO.race(io, IO.sleep(duration))

    result.flatMap {
      case Left(v)  => IO(v)
      case Right(_) => IO.raiseError(new RuntimeException("Computation timed out!"))
    }
  }

  def unrace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob).flatMap {
      case Left((_, fib)) => fib.join.flatMap {
        case Succeeded(fb) => fb.map(Right.apply)
        case Errored(e)    => IO.raiseError(e)
        case Canceled()    => IO.raiseError(new RuntimeException("Loser cancelled!"))
      }
      case Right((fib, _)) => fib.join.flatMap {
        case Succeeded(fa) => fa.map(Left.apply)
        case Errored(e)    => IO.raiseError(e)
        case Canceled()    => IO.raiseError(new RuntimeException("Loser cancelled!"))
      }
    }

  def simpleRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob).flatMap {
      case Left((outA, fibB)) => outA match {
        case Succeeded(fa) => fibB.cancel >> fa.map(Left.apply)
        case Errored(e)    => fibB.cancel >> IO.raiseError(e)
        case Canceled()    => fibB.join.flatMap {
          case Succeeded(fb) => fb.map(Right.apply)
          case Errored(e)    => IO.raiseError(e)
          case Canceled()    => IO.raiseError(new RuntimeException("Both computation cancelled."))
        }
      }
      case Right((fibA, outB)) => outB match {
        case Succeeded(fb) => fibA.cancel >> fb.map(Right.apply)
        case Errored(e)    => fibA.cancel >> IO.raiseError(e)
        case Canceled()    => fibA.join.flatMap {
          case Succeeded(fa) => fa.map(Left.apply)
          case Errored(e)    => IO.raiseError(e)
          case Canceled()    => IO.raiseError(new RuntimeException("Both computation cancelled."))
        }
      }
    }

  val task = IO.sleep(980.millis) >> IO("heavy computation").debug
  def testTimeout  = timeout(task, 1.seconds)
  def testTimeout2 = task.timeout(1.seconds)

  override def run: IO[Unit] =
//    testRace.void
//    testRacePair.void
    testTimeout.void
}
