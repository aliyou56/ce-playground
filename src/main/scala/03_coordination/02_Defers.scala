package coordination

import scala.concurrent.duration.*

import cats.effect.kernel.{ Deferred, Fiber, Outcome, Ref }
import cats.effect.{ Deferred, IO, IOApp }
import utils.*

/** Deferred is a primitive for waiting for an effect, while some othe effect completes with a value
  * A purely functional concurrency primitive with two methods
  *  - get: blocks the fiber (semantically) until a value is present
  *  - complete: inserts a value that can be read by the blocked fibers
  *
  *  . Allows inter-fiber communication
  *  . avoids busy waiting
  *  . maintains thread safety
  */
object Defers extends IOApp.Simple {

  val aDeferred: IO[Deferred[IO, Int]]    = Deferred[IO, Int]
  val aDeferred_v2: IO[Deferred[IO, Int]] = IO.deferred[Int]

  // get blocks the calling fiber (semantically) until some other fiber completes the Deferred with a value
  def reader: IO[Int] = aDeferred.flatMap { signal =>
    signal.get // blocks the fiber
  }

  def writer = aDeferred.flatMap { signal =>
    signal.complete(42)
  }

  def demoDeferred: IO[Unit] = {
    def consumer(signal: Deferred[IO, Int]) =
      for {
        _   <- IO("[consumer] waiting for result...").debug
        mol <- signal.get
        _   <- IO(s"[consumer] get the result: $mol").debug
      } yield ()

    def producer(signal: Deferred[IO, Int]) =
      for {
        _   <- IO("[producer] crunching numbers...").debug
        _   <- IO.sleep(1.second)
        mol <- IO(42)
        _   <- IO(s"[producer] complete: $mol").debug
        _   <- signal.complete(mol)
      } yield ()

    for {
      signal      <- Deferred[IO, Int]
      fibConsumer <- consumer(signal).start
      fibProducer <- producer(signal).start
      _           <- fibConsumer.join
      _           <- fibProducer.join
    } yield ()
  }

  import cats.syntax.traverse._

  // simulate download some content
  val fileParts = List("I ", "love S", "cala", " with Cat", "s Effect!<EOF>")

  def fileNotifierWithRef: IO[Unit] = {
    def downloadFile(contentRef: Ref[IO, String]): IO[Unit] =
      fileParts
        .map { part =>
          IO(s"[Downloader] got '$part'").debug >> IO.sleep(1.second) >> contentRef.update(
            currentContent => currentContent + part
          )
        }
        .sequence
        .void

    def notifyComplete(contentRef: Ref[IO, String]): IO[Unit] =
      for {
        file <- contentRef.get
        _ <-
          if (file.endsWith("<EOF>")) IO("Notifier] File download complete").debug
          else
            IO("[Notifier] downloading...").debug >> IO.sleep(500.millis) >> notifyComplete(
              contentRef
            ) // !!! busy wait !!!
      } yield ()

    for {
      contentRef    <- Ref[IO].of("")
      fibDownloader <- downloadFile(contentRef).start
      fibNotifier   <- notifyComplete(contentRef).start
      _             <- fibDownloader.join
      _             <- fibNotifier.join
    } yield ()
  }

  // deferred
  def fileNotifierWithDeferrer: IO[Unit] = {
    def notifyComplete(signal: Deferred[IO, String]): IO[Unit] =
      for {
        _ <- IO("[notifier] downloading...").debug
        _ <- signal.get // blocks until the signal is completed
        _ <- IO("[notifier] File download complete").debug
      } yield ()

    def downloadFilePart(
        part: String,
        contentRef: Ref[IO, String],
        signal: Deferred[IO, String],
      ): IO[Unit] =
      for {
        _             <- IO(s"[downloader] got '$part'").debug
        _             <- IO.sleep(1.second)
        latestContent <- contentRef.updateAndGet(currentContent => currentContent + part)
        _             <- if (latestContent.contains("<EOF>")) signal.complete(latestContent) else IO.unit
      } yield ()

    for {
      contentRef  <- Ref[IO].of("")
      signal      <- Deferred[IO, String]
      notifierFib <- notifyComplete(signal).start
      fileTasksFib <- fileParts
        .map(part => downloadFilePart(part, contentRef, signal))
        .sequence
        .start
      _ <- notifierFib.join
      _ <- fileTasksFib.join
    } yield ()

  }

  /** Exercises:
    * - (medium) write a small alarm notification with two simultaneous IOs
    *    - one that increments a counter every second (a clock)
    *    - one that waits for the counter to become 10, then prints a message "time's up!"
    *
    * - (mega hard) implement racePair with Deferred.
    *    - use a Deferred which can hold on Either[outcome for ioa, outcome for iob]
    *    - start two fibers, one for each IO
    *     - on completion (with any status), each IO needs to complete that Deferred
    *      (hint: use a finalizer from the Resources lesson)
    *      (hint2: use a guarantee call to make sure the fibers complete the Deferred)
    *     - what do you do in case of cancellation (the hardest part)?
    */

  def alarm: IO[Unit] = {
    def incrementCounter(signal: Deferred[IO, Unit], counterRef: Ref[IO, Int]): IO[Unit] =
      for {
        _       <- IO.sleep(400.millis)
        counter <- counterRef.getAndUpdate(_ + 1)
        _       <- IO(s"counter incremented: $counter").debug
        _       <- if (counter >= 10) signal.complete(()) else incrementCounter(signal, counterRef)
      } yield ()

    def waitAndPrint(signal: Deferred[IO, Unit]): IO[Unit] =
      for {
        _ <- IO("waiting for the signal").debug
        _ <- signal.get
        _ <- IO("time's up!").debug
      } yield ()

    for {
      signal     <- Deferred[IO, Unit]
      counterRef <- IO.ref(0)
      incFib     <- incrementCounter(signal, counterRef).start
      waiterFib  <- waitAndPrint(signal).start
      _          <- incFib.join
      _          <- waiterFib.join
    } yield ()
  }

  type RaceResult[A, B] = Either[
    (Outcome[IO, Throwable, A], Fiber[IO, Throwable, B]),
    (Fiber[IO, Throwable, A], Outcome[IO, Throwable, B]),
  ]

  type EitherOutcome[A, B] =
    Either[Outcome[IO, Throwable, A], Outcome[IO, Throwable, B]]

  def ourRacePair[A, B](ioa: IO[A], iob: IO[B]): IO[RaceResult[A, B]] =
    IO.uncancelable { poll =>
      for {
        signal <- Deferred[IO, EitherOutcome[A, B]]
        fibA   <- ioa.guaranteeCase(outcomeA => signal.complete(Left(outcomeA)).void).start
        fibB   <- iob.guaranteeCase(outcomeB => signal.complete(Right(outcomeB)).void).start
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
//    demoDeferred
//    fileNotifierWithRef
//    fileNotifierWithDeferrer
//    alarm
    ourRacePair(
      IO("computation 1").debug >> IO.sleep(1.second) >> IO(42),
      IO("computation 2").debug >> IO.sleep(2.seconds) >> IO(24),
    ).debug.void
}
