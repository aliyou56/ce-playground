package coordination

import scala.concurrent.duration._

import cats.effect.kernel.Ref
import cats.effect.{ IO, IOApp }

import utils._

/** Purely functional atomic reference
  * why: concurrent + thread-safe reads/writes over shared values, in a purely functional way
  */
object Refs extends IOApp.Simple {

  val atomicMol: IO[Ref[IO, Int]]    = Ref[IO].of(42)
  val atomicMol_v2: IO[Ref[IO, Int]] = IO.ref(42)

  // modifying is an effect
  val increasedMol: IO[Int] = atomicMol.flatMap(ref => ref.getAndSet(42)) // thread-safe

  // get the value
  val mol = atomicMol.flatMap { ref =>
    ref.get // thread-safe
  }

  // updating with a function
  val fMol: IO[Unit] = atomicMol.flatMap { ref =>
    ref.update(value => value * 10)
  }

  val updateMol: IO[Int] = atomicMol.flatMap { ref =>
    ref.updateAndGet(value => value * 10) // get the new value
  // getAndUpdate => get the old value
  }

  // modifying with a function returning a different type
  val modifiedMol: IO[String] = atomicMol.flatMap { ref =>
    ref.modify(value => (value * 10, s"current values is $value"))
  }

  import cats.syntax.parallel._

  // hard to read/debug
  // mix pure/impure code
  // NOT THREAD SAFE
  def demoConcurrentWorkImpure: IO[Unit] = {
    var count = 0

    def task(workload: String): IO[Unit] = {
      val wordCount = workload.split(" ").length

      for {
        _        <- IO(s"Counting words for '$workload': $wordCount'").debug
        newCount <- IO(count + wordCount)
        _        <- IO(s"New total: $newCount").debug
        _        <- IO(count += wordCount)
      } yield ()
    }

    List("I love CE", "This ref thing is useless", "Wa are writing a lot of code")
      .map(task)
      .parSequence
      .void
  }

  def demoConcurrentWorkPure: IO[Unit] = {
    def task(workload: String, total: Ref[IO, Int]): IO[Unit] = {
      val wordCount = workload.split(" ").length

      for {
        _        <- IO(s"Counting words for '$workload': $wordCount'").debug
        newCount <- total.updateAndGet(currentCount => currentCount + wordCount)
        _        <- IO(s"New total: $newCount").debug
      } yield ()
    }

    for {
      initialCount <- IO.ref(0)
      _ <- List("I love CE", "This ref thing is useless", "Wa are writing a lot of code")
        .map(str => task(str, initialCount))
        .parSequence
    } yield ()
  }

  /** Exercise */
  def tickingClockImpure: IO[Unit] = {
    var ticks: Long = 0L
    def tickingClock: IO[Unit] =
      for {
        _ <- IO.sleep(1.second)
        _ <- IO(System.currentTimeMillis()).debug
        _ <- IO(ticks += 1)
        _ <- tickingClock
      } yield ()

    def printTicks: IO[Unit] =
      for {
        _ <- IO.sleep(5.seconds)
        _ <- IO(s"TICKS: $ticks").debug
        _ <- printTicks
      } yield ()

    for {
      _ <- (tickingClock, printTicks).parTupled
    } yield ()
  }

  def tickingClockPure: IO[Unit] = {
    def tickingClock(ticks: Ref[IO, Long]): IO[Unit] =
      for {
        _ <- IO.sleep(1.seconds)
        _ <- IO(System.currentTimeMillis()).debug
        _ <- ticks.updateAndGet(_ + 1)
        _ <- tickingClock(ticks)
      } yield ()

    def printTicks(ticks: Ref[IO, Long]): IO[Unit] =
      for {
        _ <- IO.sleep(5.seconds)
        t <- ticks.get
        _ <- IO(s"TICKS: $t").debug
        _ <- printTicks(ticks)
      } yield ()

    for {
      ticks <- Ref[IO].of(0L)
      _     <- (tickingClock(ticks), printTicks(ticks)).parTupled
    } yield ()
  }

  override def run: IO[Unit] =
//    demoConcurrentWorkImpure
//    demoConcurrentWorkPure
//    tickingClockImpure
    tickingClockPure
}
