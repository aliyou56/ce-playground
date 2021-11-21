package polymorphic

import scala.concurrent.duration.*

import cats.effect.kernel.{ Deferred, Spawn }
import cats.effect.{ Concurrent, IO, IOApp, Ref }

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

  import cats.syntax.flatMap.*
  import cats.syntax.functor.*
  import cats.effect.syntax.spawn.*

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

  override def run: IO[Unit] = polymorphicEggBoiler[IO]
}
