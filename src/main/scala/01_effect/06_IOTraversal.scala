package effect

import java.util.concurrent.Executors

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

import cats.effect.{ IO, IOApp }

object IOTraversal extends IOApp.Simple {

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  def heavyComputation(str: String): Future[Int] = Future {
    Thread.sleep(Random.nextInt(1000))
    str.split(" ").length
  }

  val workload: List[String] =
    List("I like CE", "Scala is great", "Looking forward to some awesome stuff")

  def clunkyFutures: Unit = {
    val listOfFutures: List[Future[Int]] = workload.map(heavyComputation)
    // Future[List[Int] -> hard to obtain
    listOfFutures.foreach(_.foreach(println))
  }

  import cats.Traverse
  import cats.instances.list._

  val listTraverse = Traverse[List]

  def traverseFuture: Unit = {
    val singleFuture: Future[List[Int]] =
      listTraverse.traverse(workload)(heavyComputation) // stores all the result
    singleFuture.foreach(println)
  }

  import utils._

  def computeAsIO(str: String): IO[Int] = IO {
    Thread.sleep(Random.nextInt(1000))
    str.split(" ").length
  }.debug

  val ios: List[IO[Int]]      = workload.map(computeAsIO)
  val singleIO: IO[List[Int]] = listTraverse.traverse(workload)(computeAsIO)

  // parallel traversal

  import cats.syntax.parallel._

  val parallelSingleIO: IO[List[Int]] = workload.parTraverse(computeAsIO)

  /** Exercises */

  def sequence[A](list: List[IO[A]]): IO[List[A]] =
    Traverse[List].traverse(list)(identity)

  def sequence_v2[F[_]: Traverse, A](wrapper: F[IO[A]]): IO[F[A]] =
    Traverse[F].traverse(wrapper)(identity)

  // parallel version
  def parSequence[A](list: List[IO[A]]): IO[List[A]] =
    list.parTraverse(identity)

  def parSequence_v2[F[_]: Traverse, A](wrapper: F[IO[A]]): IO[F[A]] =
    wrapper.parTraverse(identity)

  // existing sequence API
  val singleIO_v2: IO[List[Int]] = listTraverse.sequence(ios)

  val parallelSingleIO_v2: IO[List[Int]] =
    ios.parSequence // extension method from the Parallel syntax package

  override def run: IO[Unit] =
    sequence_v2(ios).map(_.sum).debug.void
  //    parallelSingleIO.map(_.sum).debug.void
}
