package concurrency

import scala.concurrent.duration._

import cats.effect.kernel.{ Fiber, Outcome }
import cats.effect.kernel.Outcome.{ Canceled, Errored, Succeeded }
import cats.effect.{ IO, IOApp }

/** Concurrency vs Parallelism
  *  - Parallelism = multiple computations running at the same time
  *  - Concurrency = multiple computations overlap
  * Parallel programs may not necessarily be concurrent: e.g tasks are independent
  * Concurrent programs may not necessarily be parallel: e.g multi-tasking on the same CPU
  */
object Fibers extends IOApp.Simple {

  val meaningOfLife = IO.pure(42)
  val favLang       = IO.pure("Scala")

  import utils._

  def sameThreadIOs = for {
    _ <- meaningOfLife.debug
    _ <- favLang.debug
  } yield ()

  // fiber
  def createFiber: Fiber[IO, Throwable, String] = ??? // almost impossible to create fibers manually

  // the fiber is not actually allocated, but the fiber allocation is wrapped in another effect
  def aFiber: IO[Fiber[IO, Throwable, Int]] = meaningOfLife.debug.start

  def differentThreadIOs = for {
    _ <- aFiber
    _ <- favLang.debug
  } yield ()

  // joining a fiber
  def runOnSomeOtherThread[A](io: IO[A]): IO[Outcome[IO, Throwable, A]] =
    for {
      fib <- io.start
      res <- fib.join // effect waiting for the fiber to terminate
    } yield res
  /*
  possible Outcomes:
  - success with an IO
  - failure with an exception
  - cancelled
   */

  val someIOOnAnotherThread = runOnSomeOtherThread(meaningOfLife)
  val someResultFromAnotherThread = someIOOnAnotherThread.flatMap {
    case Succeeded(fa) => fa
    case Errored(e)    => IO(-1)
    case Canceled()    => IO(0)
  }

  def throwOnAnotherThread = for {
    fib <- IO.raiseError[Int](new RuntimeException("no matter for you")).start
    res <- fib.join
  } yield res

  def testCancel = {
    val task                        = IO("starting").debug >> IO.sleep(1.second) >> IO("done").debug
    val taskWithCancellationHandler = task.onCancel(IO("I'm being cancelled").debug.void)

    for {
      fib <- taskWithCancellationHandler.start // separate thread
      _   <- IO.sleep(500.millis) >> IO("cancelling").debug // current thread
      _   <- fib.cancel
      res <- fib.join
    } yield res
  }

  /** Exercises:
    *  1. Write a function that runs an IO on another thread, and, depending on the result of the fiber
    *    - return the result in an IO
    *    - if errored or cancelled, return a failed IO
    *
    *  2. Write a function that takes 2 IOs, runs them on different fibers and returns an IO with a tuple containing both result
    *    - if both IOs complete successfully, take their results
    *    - if the first IO returns an error, raise that error (ignoring the second IO's result/error)
    *    - if the first IO doesn't error but second IO returns an error, raise that error
    *    - if one (or both) cancelled, raise a RuntimeException
    *
    *  3. Write a function that adds a timeout to an IO:
    *    - IO runs on a fiber
    *    - if the timeout duration passes, then the fiber is cancelled
    *    - the method returns an IO[A] which contains
    *      - the original value if the computation is successful before the timeout signal
    *      - the exception if the computation is failed before the timeout signal
    *      - a RuntimeException if it times out (i.e cancelled by the timeout)
    */

  def processResultsFromFiber[A](io: IO[A]): IO[A] = {
    val ioResult = for {
      fib <- io.debug.start
      res <- fib.join
    } yield res

    ioResult.flatMap {
      case Succeeded(effect) => effect
      case Errored(e)        => IO.raiseError(e)
      case Canceled()        => IO.raiseError(new RuntimeException("Computation cancelled."))
    }
  }

  def testEx1 = {
    val aComputation = IO("starting").debug >> IO.sleep(1.second) >> IO("done").debug >> IO(42)
    processResultsFromFiber(aComputation).void
  }

  def tupled[A, B](ioa: IO[A], iob: IO[B]): IO[(A, B)] = {
    val result = for {
      fibA <- ioa.start
      fibB <- iob.start
      resA <- fibA.join
      resB <- fibB.join
    } yield (resA, resB)

    result.flatMap {
      case (Succeeded(fa), Succeeded(fb)) =>
        for {
          a <- fa
          b <- fb
        } yield (a, b)
      case (Errored(e), _) => IO.raiseError(e)
      case (_, Errored(e)) => IO.raiseError(e)
      case _               => IO.raiseError(new RuntimeException("Some computation cancelled."))
    }
  }

  def testEx2 = {
    val ioa = IO.sleep(2.seconds) >> IO(1).debug
    val iob = IO.sleep(3.seconds) >> IO(2).debug
    tupled(ioa, iob).debug.void
  }

  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val computation = for {
      fib <- io.start
      _   <- (IO.sleep(duration) >> fib.cancel).start
      res <- fib.join
    } yield res

    computation.flatMap {
      case Succeeded(fa) => fa
      case Errored(e)    => IO.raiseError(e)
      case Canceled()    => IO.raiseError(new RuntimeException("Computation cancelled: timeout!"))
    }
  }

  def testEx3 = {
    val aComputation = IO("starting").debug >> IO.sleep(1.second) >> IO("done").debug >> IO(42)
    timeout(aComputation, 2.seconds).void
  }

  override def run: IO[Unit] =
//    testEx1
//    testEx2
    testEx3
}
