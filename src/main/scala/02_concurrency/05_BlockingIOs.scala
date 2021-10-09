package concurrency

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import cats.effect.{ IO, IOApp }

/** Blocking calls & IO.sleep and yield control over the calling thread automatically */
object BlockingIOs extends IOApp.Simple {
  import utils._

  val someSleeps =
    for {
      _ <- IO.sleep(1.second).debug // semantic blocking
      _ <- IO.sleep(1.seconds).debug
    } yield ()

  // evaluation on a thread form another thread pool specific for blocking calls
  val aBlockingIO = IO.blocking {
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName}] computed a blocking code")
    ()
  }

  // yielding
  val iosOnManyThreads =
    for {
      _ <- IO("first").debug
      _ <- IO.cede // a signal to yield control over the thread
      _ <- IO("second").debug // the rest of this effect may run on another thread (not necessarily)
      _ <- IO.cede
      _ <- IO("third").debug
    } yield ()

  def testThousandEffectSwitch = {
    val ec: ExecutionContext =
      ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

    (1 to 1000).map(IO.pure).reduce(_.debug >> IO.cede >> _.debug).evalOn(ec)
  }

  override def run: IO[Unit] =
//    someSleeps
//    aBlockingIO
//    iosOnManyThreads
    testThousandEffectSwitch.void
}
