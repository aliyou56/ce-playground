package coordination

import scala.concurrent.duration.*
import scala.util.Random

import cats.effect.kernel.{ Deferred, Ref }
import cats.effect.std.CyclicBarrier
import cats.effect.{ IO, IOApp }
import cats.syntax.parallel.*
import utils.*

/** A cyclic barrier is a coordination primitive that
  *  - is initialized with a count
  *  - has a single API: await
  *
  * A cyclic barrier will (semantically) block all fibers calling ist await() method until we have exactly N fibers
  * waiting, at which point the barrier will unblock all fibers and reset to its original state.
  * Any further fiber will again block until we have exactly N fibers awaiting...
  */
object CyclicBarriers extends IOApp.Simple {

  // example: signing up for a social network just about to be launched
  def createUser(id: Int, barrier: CyclicBarrier[IO]): IO[Unit] =
    for {
      _ <- IO.sleep((Random.nextDouble * 500).toInt.millis)
      _ <- IO(
        s"[user $id] just heard there's a new social network - signing up for the waitList..."
      ).debug
      _ <- IO.sleep((Random.nextDouble * 1500).toInt.millis)
      _ <- IO(s"[user $id] on the waiting list now can't wait.").debug
      _ <- barrier.await // block
      _ <- IO(s"[user $id] OMG this is so cool").debug
    } yield ()

  def openingNetwork: IO[Unit] =
    for {
      _ <- IO(
        s"[announcer] The social network is up for registration. Launching when we have 10 users!"
      ).debug
      barrier <- CyclicBarrier[IO](10)
      _       <- (1 to 10).toList.parTraverse(id => createUser(id, barrier))
    } yield ()

  /** Exercise: Implement CB with Ref + Deferred */
  trait CBarrier {
    def await: IO[Unit]
  }

  object CBarrier {
    final case class State(nWaiting: Int, signal: Deferred[IO, Unit])

    def apply(capacity: Int): IO[CBarrier] =
      for {
        signal <- Deferred[IO, Unit]
        state  <- Ref[IO].of(State(capacity, signal))
      } yield new CBarrier {
        override def await: IO[Unit] = Deferred[IO, Unit].flatMap { newSignal =>
          state.modify {
            case State(1, signal) => State(capacity, newSignal) -> signal.complete(()).void
            case State(n, signal) => State(n - 1, signal)       -> signal.get
          }.flatten
        }
      }
  }

  override def run: IO[Unit] = openingNetwork
}
