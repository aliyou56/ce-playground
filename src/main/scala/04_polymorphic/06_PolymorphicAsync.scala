package polymorphic

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import cats.syntax.flatMap.*
import cats.syntax.functor.*

import cats.effect.kernel.{ Async, Sync, Temporal }
import cats.effect.{ Concurrent, IO, IOApp }

import utils.*

/**
 * Async: the ability to suspend async effect from outside Cats Effect
 */
object PolymorphicAsync extends IOApp.Simple {

  // Async - asynchronous computation, "suspended" in F
  trait MyAsync[F[_]] extends Sync[F] with Temporal[F] {
    // fundamental description of async computations
    def executionContext: F[ExecutionContext]
    def async[A](cb: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A]
    def evalOn[A](fa: F[A], ec: ExecutionContext): F[A]

    def async_[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A] =
      async(kb => map(pure(cb(kb)))(_ => None))

    def never[A]: F[A] = async_(_ => ()) // never-ending effect
  }

  val asyncIO = Async[IO]

  // pure, map/flatMap, raiseError, uncancelable, start, ref/deferred, sleep, delay/defer/blocking, +async_/async
  val ec = asyncIO.executionContext

  // power: async_ + async: FFI
  val threadPool = Executors.newFixedThreadPool(10)
  type Callback[A] = Either[Throwable, A] => Unit

  val asyncMOL: IO[Int] = IO.async_ { (cb: Callback[Int]) =>
    // start computation on some other thread pool
    threadPool.execute { () =>
      println(s"[${Thread.currentThread.getName}] Computing an async MOL")
      cb(Right(42))
    }
  }

  val asyncMOLComplex: IO[Int] = IO.async { (cb: Callback[Int]) =>
    IO {
      threadPool.execute { () =>
        println(s"[${Thread.currentThread.getName}] Computing an async MOL")
        cb(Right(42))
      }
    }.as(Some(IO("Cancelled!").debug.void)) // finalizer
  }

  val myExecutionContext = ExecutionContext.fromExecutorService(threadPool)
  val asyncMOL_v3 =
    asyncIO
      .evalOn(IO(42), myExecutionContext)
      .guarantee(IO(threadPool.shutdown))

  // never
  val neverIO = asyncIO.never

  /** Exercise: 1. never and async_ in terms of async */

  // 2. tuple 2 effects with different requirements
  def firstEffect[F[_]: Concurrent, A](a: A): F[A] = Concurrent[F].pure(a)
  def secondEffect[F[_]: Sync, A](a: A): F[A]      = Sync[F].pure(a)

  def tupledEffect[F[_]: Async, A](a: A): F[(A, A)] = for {
    f1 <- firstEffect(a)
    f2 <- secondEffect(a)
  } yield f1 -> f2

  override def run: IO[Unit] = ???
}
