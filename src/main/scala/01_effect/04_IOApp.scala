package effect

import cats.effect.{ExitCode, IO, IOApp}

val program: IO[Unit] = IO(println("hello world"))

object IOApps extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    program.as(ExitCode.Success)
}

object MySimpleApp extends IOApp.Simple {
  override def run: IO[Unit] = program
}
