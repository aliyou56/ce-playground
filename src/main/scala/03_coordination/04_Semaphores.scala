package coordination

import scala.concurrent.duration.*
import scala.util.Random

import cats.effect.std.Semaphore
import cats.effect.{ IO, IOApp }
import cats.effect.unsafe.IORuntime
import cats.syntax.parallel._
import utils.*

object Semaphores extends IOApp.Simple {

  val semaphore: IO[Semaphore[IO]] = Semaphore[IO](2) // 2 total permits

  // example: limiting the number of concurrent sessions on a server
  def doWorkWhileLoggedIn: IO[Int] = IO.sleep(1.second) >> IO(Random.nextInt(100))

  def login(id: Int, sem: Semaphore[IO]): IO[Int] =
    for {
      _   <- IO(s"[sessions $id] waiting to log in...").debug
      _   <- sem.acquire
      _   <- IO(s"[sessions $id] logged in, working...").debug
      res <- doWorkWhileLoggedIn
      _   <- IO(s"[sessions $id] done: $res, logging out...").debug
      _   <- sem.release
    } yield res

  def demoSemaphore =
    for {
      sem      <- Semaphore[IO](2)
      user1Fib <- login(1, sem).start
      user2Fib <- login(2, sem).start
      user3Fib <- login(3, sem).start
      _        <- user1Fib.join
      _        <- user2Fib.join
      _        <- user3Fib.join
    } yield ()

  def weightedLogin(id: Int, requiredPermits: Int, sem: Semaphore[IO]): IO[Int] =
    for {
      _   <- IO(s"[sessions $id] waiting to log in...").debug
      _   <- sem.acquireN(requiredPermits)
      _   <- IO(s"[sessions $id] logged in, working...").debug
      res <- doWorkWhileLoggedIn
      _   <- IO(s"[sessions $id] done: $res, logging out...").debug
      _   <- sem.releaseN(requiredPermits)
    } yield res

  def demoWeightedSemaphore =
    for {
      sem      <- Semaphore[IO](2)
      user1Fib <- weightedLogin(1, 1, sem).start
      user2Fib <- weightedLogin(2, 2, sem).start
      user3Fib <- weightedLogin(3, 3, sem).start
      _        <- user1Fib.join
      _        <- user2Fib.join
      _        <- user3Fib.join
    } yield ()

  /** Exercise: Semaphore with 1 permit == mutex */
  val mutex = Semaphore[IO](1)
  val users: IO[List[Int]] = (1 to 10).toList.parTraverse { id =>
    for {
      sem <- mutex // mistake -> flatMap Semaphore[IO](1) => create a new semaphore every time
      _   <- IO(s"[sessions $id] waiting to log in...").debug
      _   <- sem.acquire
      _   <- IO(s"[sessions $id] logged in, working...").debug
      res <- doWorkWhileLoggedIn
      _   <- IO(s"[sessions $id] done: $res, logging out...").debug
      _   <- sem.release
    } yield res
  }

  val usersFixed: IO[List[Int]] = mutex.flatMap { sem =>
    (1 to 10).toList.parTraverse { id =>
      for {
        _   <- IO(s"[sessions $id] waiting to log in...").debug
        _   <- sem.acquire
        _   <- IO(s"[sessions $id] logged in, working...").debug
        res <- doWorkWhileLoggedIn
        _   <- IO(s"[sessions $id] done: $res, logging out...").debug
        _   <- sem.release
      } yield res
    }
  }

  override def run: IO[Unit] =
//    demoSemaphore
//    demoWeightedSemaphore
//    users.debug.void
    usersFixed.debug.void
}
