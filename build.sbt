organization := "com.ksmti.poc"

scalaVersion := "2.13.3"

version := "1.0.0"

scalacOptions in Compile ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint"
)

javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m")

lazy val akkaVersion = "2.6.10"

lazy val httpVersion = "10.1.12"

lazy val managementVersion = "1.0.9"

// Akka Actors / Remote / Cluster Libs
lazy val akkaLibs = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
)

// Akka Http Libs
lazy val akkaHttp = Seq(
  "com.typesafe.akka" %% "akka-http" % httpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % httpVersion
)

// Management Libs
lazy val managementLibs = Seq(
  "com.lightbend.akka.management" %% "akka-management" % managementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % managementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % managementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-aws-api" % managementVersion
)

// Chill-Akka / Kryo Serializer Libs
lazy val serializationLibs = Seq("com.twitter" %% "chill-akka" % "0.9.5")

// Testing Libs
lazy val testingLibs = Seq(
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test
)

libraryDependencies ++= (akkaLibs ++ akkaHttp ++ managementLibs ++ serializationLibs ++ testingLibs)

fork in run := true

mainClass in (Compile, run) := Some("com.ksmti.poc.PublicEventsApp")

// disable parallel tests
parallelExecution in Test := false

mainClass in assembly := Some("com.ksmti.poc.PublicEventsApp")

test in assembly := {}

headerLicense := Some(HeaderLicense.Custom("""| Copyright (C) 2015-2020 KSMTI
                                              |
                                              | <http://www.ksmti.com>
    """.stripMargin))
scalafmtOnCompile := true

lazy val `PublicEvents` = project.in(file(".")).enablePlugins(AutomateHeaderPlugin)

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
