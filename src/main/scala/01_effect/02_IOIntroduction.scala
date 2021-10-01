package effect

import scala.io.StdIn

import cats.effect.IO

object IOIntroduction {

  // IO
  val io1: IO[Int] = IO.pure(42) // evaluate eagerly
  val io2: IO[Int] = IO.delay {
    println("go delay")
    42
  }

  val ioDelay: IO[Int] = IO { // apply == delay
    println("go delay")
    42
  }

  // map, flatMap
  val improvedMeaningOfLife = io1.map(_ + 2)
  val printedMeaningOfLife  = io2.flatMap(mol => IO.delay(println(mol)))

  val smallProgram: IO[Unit] =
    for {
      line1 <- IO(StdIn.readLine())
      line2 <- IO(StdIn.readLine())
      _     <- IO.delay(println(line1 + line2))
    } yield ()

  // mapN - combine IO effects as tuples

  import cats.syntax.apply._

  val combinedMeaningOfLife: IO[Int] = (io1, improvedMeaningOfLife).mapN(_ + _)

  val smallProgram_v2: IO[Unit] =
    (IO(StdIn.readLine()), IO(StdIn.readLine())).mapN(_ + _).map(println)

  /** Exercises
    */

  // 1. sequence 2 IOs and take the result of the LAST one
  def sequenceTakeLast[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa.flatMap(_ => iob.map(identity))
  //    for {
  //      _ <- ioa
  //      b <- iob
  //    } yield b

  def sequenceTask_v2[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa *> iob // andThen

  def sequenceTask_v3[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa >> iob // "andThen" with by-name call

  // 2. sequence 2 IOs and take the result of the FIRST one
  def sequenceTakeFirst[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    ioa.flatMap(a => iob.as(a))
  //    ioa.flatMap(a => iob.map(_ => a))
  //    for {
  //      a <- ioa
  //      _ <- iob
  //    } yield a

  def sequenceTaskFirst_v2[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    ioa <* iob

  // 3. repeat an IO effect forever
  def forever[A](io: IO[A]): IO[A] = io.flatMap(_ => forever(io))

  def forever_v2[A](io: IO[A]): IO[A] = io >> forever_v2(io)
  def forever_v3[A](io: IO[A]): IO[A] = io *> forever_v3(io) // not stack safe
  def forever_v4[A](io: IO[A]): IO[A] = io.foreverM          // with tail recursion

  // 4. convert an IO to a different type
  def convert[A, B](ioa: IO[A], value: B): IO[B]    = ioa.map(_ => value)
  def convert_v2[A, B](ioa: IO[A], value: B): IO[B] = ioa.as(value)

  // 5. discard value inside IO, just return Unit
  def asUnit[A](ioa: IO[A]): IO[Unit]    = ioa.map(_ => ())
  def asUnit_v2[A](ioa: IO[A]): IO[Unit] = ioa.as(()) // ! discouraged !
  def asUnit_v3[A](ioa: IO[A]): IO[Unit] = ioa.void   // encouraged

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  // 6. fix stack recursion
  def sumIO(n: Int): IO[Int] =
    if (n <= 0) IO(0)
    else
      for {
        lastNumber <- IO(n)
        prevSum    <- sumIO(n - 1)
      } yield prevSum + lastNumber

  // 7. write a fibonacci IO that does Not crash on recursion
  def fibonacci(n: Int): IO[BigInt] =
    if (n < 2) IO(1)
    else
      for {
        last <- IO.defer(fibonacci(n - 1)) // same as IO.delay(...).flatten
        prev <- IO.defer(fibonacci(n - 2))
      } yield last + prev

  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global // platform

    (1 to 10).foreach(i => println(fibonacci(i).unsafeRunSync()))

    // forever(IO {
    //   println("forever")
    //   Thread.sleep(1000)
    // }).unsafeRunSync()
    // println(smallProgram_v2.unsafeRunSync())
  }
}
