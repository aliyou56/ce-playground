package concurrency

import java.util.concurrent.Executors

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import cats.effect.{ IO, IOApp }

object AsyncIOs extends IOApp.Simple {
  import utils._

  // IOs can run asynchronously on fibers, without having to manually manage the fiber lifecycle
  val threadPool         = Executors.newFixedThreadPool(8)
  given ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  type Callback[A] = Either[Throwable, A] => Unit

  def computeMol: Int = {
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread...")
    42
  }

  def computeMolEither: Either[Throwable, Int] = Try(computeMol).toEither

  def computeMolOnThreadPool: Unit = threadPool.execute(() => computeMolEither)

  // lift computation to an IO
  // async is a FFI (Foreign Function Interface)
  val asyncMolIO: IO[Int] =
    IO.async_ {
      cb =>                        // CE thread blocks (semantically) until this cb is invoked (by some other thread)
        threadPool.execute { () => // computation not managed by CE
          val result = computeMolEither
          cb(result) // CE thread is notified with the result
        }
    }

  /** Exercise: Lift an async computation on ec to an IO */
  def asyncToIO[A](computation: () => A)(using ec: ExecutionContext): IO[A] =
    IO.async_[A] { (cb: Callback[A]) =>
      ec.execute { () =>
        val result: Either[Throwable, A] = Try(computation()).toEither
        cb(result)
      }
    }

  val asyncMolIO_v2: IO[Int] = asyncToIO(() => computeMol)

  /** Exercise: Lift an async computation as a Future, to an IO */
  def futureToIO[A](future: => Future[A]): IO[A] =
    IO.async_ { (cb: Callback[A]) =>
      future.onComplete { tryResult =>
        val result = tryResult.toEither
        cb(result)
      }
    }

  lazy val molFuture = Future(computeMol)
  val asyncMolIO_v3  = futureToIO(molFuture)
  val asyncMolIO_v4  = IO.fromFuture(IO(molFuture))

  /** Exercise: a never-ending IO*/
  val neverEndingIO    = IO.async_ { _ => () }
  val neverEndingIO_v2 = IO.never

  // Full async call
  def demoAsyncCancellation = {
    val asyncMolIO_v2: IO[Int] = IO.async { (cb: Callback[Int]) =>
      // return IO[Option[IO[Unit]]]
      /**
       * finalizer in case computation gets cancelled.
       * finalizers are of type IO[Unit]
       * not specifying finalizer => Option[IO[Unit]]
       * creating option is an effect => IO[Option[IO[Unit]]]
       */
      IO {
        threadPool.execute { () =>
          val result = computeMolEither
          cb(result)
        }
      }.as(Some(IO("Cancelled!").debug.void))
    }

    for {
      fib <- asyncMolIO_v2.start
      _ <- IO.sleep(500.millis) >> IO("cancelling...").debug >> fib.cancel
      _ <- fib.join
    } yield ()
  }

  override def run: IO[Unit] =
//    asyncMolIO.debug >> IO(threadPool.shutdown())
//    asyncMolIO_v2.debug >> IO(threadPool.shutdown())
//    asyncMolIO_v3.debug >> IO(threadPool.shutdown())
//    asyncMolIO_v4.debug >> IO(threadPool.shutdown())
    demoAsyncCancellation.debug >> IO(threadPool.shutdown())
}
