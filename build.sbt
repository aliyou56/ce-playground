val scala3Version = "3.1.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "cats-effect-playground",
    version := "0.1",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.3.0"
    ),
  )

Compile / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint",
)
