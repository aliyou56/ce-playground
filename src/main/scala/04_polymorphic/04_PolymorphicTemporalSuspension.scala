package polymorphic

import scala.concurrent.duration.*

import cats.syntax.flatMap.*

import cats.effect.{ Concurrent, IO, IOApp, Temporal }

import utils.general.*

object PolymorphicTemporalSuspension extends IOApp.Simple {

  // Temporal - time-blocking effects
  trait MyTemporal[F[_]] extends Concurrent[F] {
    def sleep(time: FiniteDuration): F[Unit] // semantically blocks this fiber for a specified time
  }

  // abilities: pure, mp/flatMap, raiseError, uncancelable, start, ref/deferred, + sleep

  val temporalIO     = Temporal[IO] // given Temporal[IO] in scope
  val chainOfEffects = IO("loading...").debug *> IO.sleep(1.second) *> IO("Game ready!").debug
  val chainOfEffects_v2 = temporalIO.pure("loading...") *> temporalIO
    .sleep(1.second) *> temporalIO.pure("Game ready!").debug

  /** Exercise: generalize timeout */
  def timeout[F[_], A](fa: F[A], duration: FiniteDuration)(using temporal: Temporal[F]): F[A] =
    temporal
      .race(fa, temporal.sleep(duration))
      .flatMap {
        case Left(a)  => temporal.pure(a)
        case Right(_) => temporal.raiseError(new java.lang.RuntimeException("Computation time out"))
      }

  override def run: IO[Unit] = ???
}
