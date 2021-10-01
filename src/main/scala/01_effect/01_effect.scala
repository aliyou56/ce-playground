package effect

import scala.concurrent.Future
import scala.io.StdIn

object Effects {
  // pure functional programming
  // substitution
  def combine(a: Int, b: Int): Int = a + b

  // referential transparency

  /** Effect types
    * - type signature describes the kind of calculation that will be performed
    * - type signature describes the VALUE that will be calculated
    * - when side effects are needed, effect construction is separate from effect execution
    */

  /*
     example: Option is an effect type
     - describe a possibly absent value
     - computes a value of type A, if it exists
     - side effects are not needed
   */
  val anOption: Option[Int] = Option(42)

  /*
  Future:
  - describes an asynchronous computation
  - computes a value of type A, if it's successful
  - side effect is required (allocating/scheduling a thread), execution is NOT separated from construction
   */
  import scala.concurrent.ExecutionContext.Implicits.global
  val aFuture: Future[Int] = Future(42)

  /** MyIO data type from the monads lesson
    * - describe any computation that might produce side effects
    * - calculate a value of type A, if it's successful
    * - side effects are required for the evaluation of () => A
    *  -> the creation of MyIO does NOT produce the side effects on construction
    */
  case class MyIO[A](unsafeRun: () => A) {
    def map[B](fab: A => B): MyIO[B] = MyIO(() => fab(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] = MyIO(() => f(unsafeRun()).unsafeRun())
  }

  val anIO: MyIO[Int] = MyIO { () =>
    println("writing")
    42
  }

  val clock: MyIO[Long] = MyIO(() => System.currentTimeMillis())

  def measure[A](computation: MyIO[A]): MyIO[Long] =
    for {
      start <- clock
      _     <- computation
      end   <- clock
    } yield end - start

  def putStrLn(str: String): MyIO[Unit] = MyIO(() => println(str))

  def getStrLn: MyIO[String] = MyIO(() => StdIn.readLine())

  def testTime: Unit = {
    val test = measure(MyIO(() => Thread.sleep(1000)))
    println(test.unsafeRun())
  }

  def main(args: Array[String]): Unit =
//    anIO.unsafeRun()
    testTime
}
