package coordination

import scala.collection.immutable.Queue
import scala.concurrent.duration.*
import scala.util.Random

import cats.effect.kernel.Outcome.{ Canceled, Errored, Succeeded }
import cats.effect.kernel.{ Deferred, Ref }
import cats.effect.{ IO, IOApp }
import cats.syntax.parallel.*
import utils.*

trait Mutex {
  def acquire: IO[Unit]
  def release: IO[Unit]
}

object Mutex {
  type Signal = Deferred[IO, Unit]
  case class State(locked: Boolean, queue: Queue[Signal])
  val unlocked = State(locked = false, Queue())

  private def createSignal(): IO[Signal] = Deferred[IO, Unit]

  def create: IO[Mutex] = Ref[IO].of(unlocked).map(createMutexWithCancellation)

  private def createMutexWithCancellation(state: Ref[IO, State]): Mutex =
    new Mutex {
      override def acquire: IO[Unit] = IO.uncancelable { poll =>
        createSignal().flatMap { signal =>
          val cleanup = state.modify {
            case State(locked, queue) =>
              val newQueue   = queue.filterNot(_ eq signal)
              val isBlocking = queue.exists(_ eq signal)
              val decision   = if (isBlocking) IO.unit else release
              State(locked, newQueue) -> decision
          }.flatten

          state.modify {
            case State(false, _) => State(locked = true, Queue()) -> IO.unit
            case State(true, queue) =>
              State(locked = true, queue.enqueue(signal)) -> poll(signal.get).onCancel(cleanup)
          }.flatten // modify returns IO[B], our B is IO[Unit], so we will end up with IO[IO[Unit]]
        }
      }

      override def release: IO[Unit] = state.modify {
        case State(false, _) => unlocked -> IO.unit
        case State(true, queue) =>
          if (queue.isEmpty) unlocked -> IO.unit
          else {
            val (signal, rest) = queue.dequeue
            State(locked = true, rest) -> signal.complete(()).void
          }
      }.flatten
    }

  private def createSimpleMutex(state: Ref[IO, State]): Mutex =
    new Mutex {

      /** Change the state of the Ref:
        * - if the mutex is currently unlocked, state becomes (true, [])
        * - if the mutex is locked, state becomes (true, queue + new signal) AND WAIT ON THAT SIGNAL.
        */
      override def acquire: IO[Unit] = createSignal().flatMap { signal =>
        state.modify {
          case State(false, _)    => State(locked = true, Queue())               -> IO.unit
          case State(true, queue) => State(locked = true, queue.enqueue(signal)) -> signal.get
        }.flatten // modify returns IO[B], our B is IO[Unit], so we will end up with IO[IO[Unit]]
      }

      /** Change the state of hte Ref:
        * - if the mutex is unlocked, leave the state unchanged
        * - if the mutex is locked,
        *    - if the queue is empty, unlock the mutex, i.e state becomes (false, [])
        *    - if the queue is not empty, take a signal out of the queue and complete it (thereby unlocking a fiber waiting on it)
        */
      override def release: IO[Unit] = state.modify {
        case State(false, _) => unlocked -> IO.unit
        case State(true, queue) =>
          if (queue.isEmpty) unlocked -> IO.unit
          else {
            val (signal, rest) = queue.dequeue
            State(locked = true, rest) -> signal.complete(()).void
          }
      }.flatten
    }
}

object MutexPlayground extends IOApp.Simple {

  def criticalTask: IO[Int] = IO.sleep(1.second) >> IO(Random.nextInt(100))

  def createNonLockingTask(id: Int): IO[Int] =
    for {
      _   <- IO(s"[task $id] working...").debug
      res <- criticalTask
      _   <- IO(s"[task $id] got result: $res").debug
    } yield res

  def demoNonLockingTasks: IO[List[Int]] =
    (1 to 10).toList.parTraverse(id => createNonLockingTask(id))

  def createLockingTask(id: Int, mutex: Mutex): IO[Int] =
    for {
      _ <- IO(s"[task $id] waiting for the lock...").debug
      _ <- mutex.acquire
      // critical section
      _   <- IO(s"[task $id] working...").debug
      res <- criticalTask
      _   <- IO(s"[task $id] got result: $res").debug
      // critical section end
      _ <- mutex.release
      _ <- IO(s"[task $id] lock removed").debug
    } yield res

  def demoLockingTasks: IO[List[Int]] =
    for {
      mutex   <- Mutex.create
      results <- (1 to 10).toList.parTraverse(id => createLockingTask(id, mutex))
    } yield results

  def createCancelingTask(id: Int, mutex: Mutex): IO[Int] =
    if (id % 2 == 0) createLockingTask(id, mutex)
    else
      for {
        fib <- createLockingTask(id, mutex)
          .onCancel(
            IO(s"[task $id] received cancellation!").debug.void
          )
          .start
        _   <- IO.sleep(2.seconds) >> fib.cancel
        out <- fib.join
        result <- out match {
          case Succeeded(fa) => fa
          case Errored(_)    => IO(-1)
          case Canceled()    => IO(-2)
        }
      } yield result

  def demoCancelingTasks: IO[List[Int]] =
    for {
      mutex   <- Mutex.create
      results <- (1 to 10).toList.parTraverse(id => createCancelingTask(id, mutex))
    } yield results

  def demoCancellingWhileWorking =
    for {
      mutex <- Mutex.create
      fib1 <- (
        IO("[fib 1] getting mutex").debug >>
          mutex.acquire >>
          IO("[fib1] got the mutex, never releasing").debug >> IO.never
      ).start
      fib2 <- (
        IO("[fib2] sleeping").debug >>
          IO.sleep(1.second) >>
          IO("[fib2] trying to get the mutex").debug >>
          mutex.acquire.onCancel(IO("WATCH THE BREAK HERE").debug.void) >>
          IO("[fib2] acquired mutex").debug
      ).start
      fib3 <- (
        IO("[fib3] sleeping").debug >>
          IO.sleep(1500.millis) >>
          IO("[fib3] trying to get the mutex").debug >>
          mutex.acquire >>
          IO("[fib3] acquired mutex - FAIL!").debug
      ).start
      _ <- IO.sleep(2.seconds) >> IO("CANCELLING fib2!").debug >> fib2.cancel
      _ <- fib1.join
      _ <- fib2.join
      _ <- fib3.join
    } yield ()

  override def run: IO[Unit] =
//    demoNonLockingTasks.debug.void
//    demoLockingTasks.debug.void
//    demoCancelingTasks.debug.void
    demoCancellingWhileWorking.debug
}
