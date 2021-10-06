package concurrency

import java.io.{ File, FileReader }
import java.util.Scanner

import scala.concurrent.duration.*

import cats.effect.kernel.Outcome.{ Canceled, Errored, Succeeded }
import cats.effect.kernel.Resource
import cats.effect.{ IO, IOApp }

object Resources extends IOApp.Simple {

  import utils._

  // use-case: manage a connection lifecycle
  class Connection(url: String) {
    def open: IO[String]  = IO(s"opening connection to $url").debug
    def close: IO[String] = IO(s"closing connection to $url").debug
  }

  // problem: leaking resources
  val syncFetchUrl =
    for {
      fib <- (new Connection("google.com").open *> IO.sleep(Int.MaxValue.seconds)).start
      _   <- IO.sleep(1.seconds) *> fib.cancel
    } yield ()

  def correctAsyncFetchUrl =
    for {
      conn <- IO(new Connection("google.com"))
      fib <-
        (conn.open *> IO.sleep(Int.MaxValue.seconds))
          .onCancel(conn.close.void)
          .start
      _ <- IO.sleep(1.seconds) *> fib.cancel
    } yield ()

  // bracket pattern
  val bracketFetchUrl =
    IO(new Connection("google.com"))
      .bracket(conn => conn.open *> IO.sleep(Int.MaxValue.seconds))(conn => conn.close.void)

  val bracketProgram =
    for {
      fib <- bracketFetchUrl.start
      _   <- IO.sleep(1.seconds) *> fib.cancel
    } yield ()

  /** Exercise: read the file with the bracket pattern
    * - open a scanner
    * - read the file line by line, every 100 millis
    * - close the scanner
    * - if cancelled/throws error, close the scanner
    */
  def openFileScanner(path: String): IO[Scanner] =
    IO(new Scanner(new FileReader(new File(path))))

  def readLineByLine(scanner: Scanner): IO[Unit] =
    if (scanner.hasNext())
      IO(scanner.nextLine()).debug >> IO.sleep(100.millis) >> readLineByLine(scanner)
    else IO.unit

  def bracketReadFile(path: String): IO[Unit] =
    IO(s"opening file at $path").debug *>
      openFileScanner(path).bracket { scanner =>
        readLineByLine(scanner).onCancel(IO(scanner.close()))
      } { s =>
        IO(s"closing file at $path").debug *> IO(s.close())
      }

  /** !! nesting resources */
  def connFromConfig(path: String): IO[Unit] =
    openFileScanner(path).bracket { scanner =>
      // acquire connection based on the file
      IO(new Connection(scanner.nextLine())).bracket { conn =>
        conn.open >> IO.never
      }(conn => conn.close.void)
    }(scanner => IO("closing file") >> IO(scanner.close()))

  val connResource =
    Resource.make(IO(new Connection("google.com")))(conn => conn.close.void)
  val resourceFetchUrl =
    for {
      fib <- connResource.use(conn => conn.open >> IO.never).start
      _   <- IO.sleep(1.seconds) >> fib.cancel
    } yield ()

  //
  val simpleResource = IO("some resource")

  val usingResource: String => IO[String] =
    str => IO(s"using the string $str").debug

  val releaseResource: String => IO[Unit] =
    str => IO(s"finalizing the string: $str").debug.void

  val usingResourceWithBracket =
    simpleResource.bracket(usingResource)(releaseResource)

  val usingResourceWithResource =
    Resource.make(simpleResource)(releaseResource).use(usingResource)

  /** Exercise */
  def getResourceFromFile(path: String) =
    Resource.make(openFileScanner(path)) { scanner =>
      IO(s"closing file at $path").debug >> IO(scanner.close())
    }

  def resourceReadFile(path: String) =
    IO(s"opening file at $path") >>
      getResourceFromFile(path).use { scanner =>
        readLineByLine(scanner)
      }

  def cancelReadFile(path: String) =
    for {
      fib <- resourceReadFile(path).start
      _   <- IO.sleep(2.seconds) >> fib.cancel
    } yield ()

  // nested resources
  def connFromConfigResource(path: String) =
    Resource
      .make(IO("opening file").debug >> openFileScanner(path))(scanner => IO("closing file").debug >> IO(scanner.close))
      .flatMap { scanner =>
        Resource
          .make(IO(new Connection(scanner.nextLine())))(conn => conn.close.void)
      }

  // connection + file will close automatically
  val openConnection =
    connFromConfigResource("src/main/resources/connection.config")
      .use(conn => conn.open >> IO.never)

  val cancelledConnection =
    for {
      fib <- openConnection.start
      _   <- IO.sleep(1.second) >> IO("cancelling!").debug >> fib.cancel
    } yield ()

  def connFromConfigResourceFor(path: String) =
    for {
      scanner <- Resource.make(IO("opening file").debug >> openFileScanner(path))(scanner =>
        IO("closing file").debug >> IO(scanner.close)
      )
      conn <- Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close.void)
    } yield conn

  // finalizers to regular IOs
  val ioWithFinalizer =
    IO("some resource")
      .debug
      .guarantee(IO("freeing resource").debug.void)

  val ioWithFinalizer2 =
    IO("some resource")
      .debug
      .guaranteeCase {
        case Succeeded(fa) => fa.flatMap(result => IO(s"releasing resource: $result").debug.void)
        case Errored(err)  => IO("nothing to release").debug.void
        case Canceled()    => IO("resource got cancelled").debug.void
      }

  override def run =
//    bracketProgram.void
//    bracketReadFile("src/main/scala/com/rockthejvm/concurrency/Resources.scala")
//    resourceFetchUrl.void
//    cancelReadFile("src/main/scala/com/rockthejvm/concurrency/Resources.scala")
//    openConnection.void
//    cancelledConnection
    ioWithFinalizer2.void
}
