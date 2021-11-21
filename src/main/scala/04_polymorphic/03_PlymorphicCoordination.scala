package polymorphic

import scala.concurrent.duration.*

import cats.syntax.flatMap.*
import cats.syntax.functor.*

import cats.effect.{ IO, IOApp, Concurrent, Ref }
import cats.effect.kernel.{ Deferred, Fiber, Outcome, Spawn }
import cats.effect.syntax.monadCancel.* // guaranteeCase extension method
import cats.effect.syntax.spawn.*       // start extension method

import utils.general.*

object PolymorphicCoordination extends IOApp.Simple {

  // concurrent - Ref + Deferred for ANY effect type
  trait MyConcurrent[F[_]] extends Spawn[F] {
    def ref[A](a: A): F[Ref[F, A]]
    def deferred[A]: F[Deferred[F, A]]
  }

  val concurrentIO = Concurrent[IO]    // given instance of concurrent[IO]
  val aDeferred    = Deferred[IO, Int] // given Concurrent[IO] in scope

  val aDeferred_v2 = concurrentIO.deferred[Int]
  val aRef         = concurrentIO.ref(42)

  // capabilities: pure, map/flatMap, raiseError, uncancelable, start(fibers) + ref/deferred

  def polymorphicEggBoiler[F[_]](using concurrent: Concurrent[F]): F[Unit] = {

    def eggReadyNotification(signal: Deferred[F, Unit]) =
      for {
        _ <- concurrent.pure("Egg boiling on some other fiber, waiting...").debug
        _ <- signal.get
        _ <- concurrent.pure("EGG READY!").debug
      } yield ()

    def tickingClock(counter: Ref[F, Int], signal: Deferred[F, Unit]): F[Unit] =
      for {
        _     <- unsafeSleep[F, Throwable](1.second)
        count <- counter.updateAndGet(_ + 1)
        _     <- concurrent.pure(count).debug
        _ <-
          if (count >= 10) signal.complete(()).void
          else tickingClock(counter, signal)
      } yield ()

    for {
      counter         <- concurrent.ref(0)
      signal          <- concurrent.deferred[Unit]
      notificationFib <- eggReadyNotification(signal).start
      clock           <- tickingClock(counter, signal).start
      _               <- notificationFib.join
      _               <- clock.join
    } yield ()
  }

  /** Exercise: Generalize racePair */

  type RaceResult[F[_], A, B] = Either[
    (Outcome[F, Throwable, A], Fiber[F, Throwable, B]),
    (Fiber[F, Throwable, A], Outcome[F, Throwable, B]),
  ]

  type EitherOutcome[F[_], A, B] =
    Either[Outcome[F, Throwable, A], Outcome[F, Throwable, B]]

  def polymorphicRacePair[F[_], A, B](fa: F[A],fb: F[B])(
    using concurrent: Concurrent[F]
  ): F[RaceResult[F, A, B]] =
    concurrent.uncancelable { poll =>
      for {
        signal <- concurrent.deferred[EitherOutcome[F, A, B]]
        fibA   <- fa.guaranteeCase(outcomeA => signal.complete(Left(outcomeA)).void).start
        fibB   <- fb.guaranteeCase(outcomeB => signal.complete(Right(outcomeB)).void).start
        result <- poll(signal.get).onCancel {
          for {
            cancelFibA <- fibA.cancel.start
            cancelFibB <- fibB.cancel.start
            _          <- cancelFibA.join
            _          <- cancelFibB.join
          } yield ()
        }
      } yield result match {
        case Left(outcomeA)  => Left(outcomeA -> fibB)
        case Right(outcomeB) => Right(fibA -> outcomeB)
      }
    }

  override def run: IO[Unit] =
    // polymorphicEggBoiler[IO]
    polymorphicRacePair[IO, Int, Int](
      IO("computation 1").debug >> IO.sleep(1.second) >> IO(42),
      IO("computation 2").debug >> IO.sleep(2.seconds) >> IO(24),
    ).debug.void
}
