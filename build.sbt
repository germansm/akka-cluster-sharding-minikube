import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

organization := "com.ksmti.poc"

scalaVersion := "2.13.3"

version := "0.0.1"

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
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.10",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
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
  "com.lightbend.akka.discovery" %% "akka-discovery-aws-api" % managementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % managementVersion
)

// Chill-Akka / Kryo Serializer Libs
lazy val serializationLibs = Seq("com.twitter" %% "chill-akka" % "0.9.5")

// Testing Libs
lazy val testingLibs = Seq(
  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
)

libraryDependencies ++= (akkaLibs ++ akkaHttp ++ managementLibs ++ serializationLibs ++ testingLibs)

fork in run := true

mainClass in (Compile, run) := Some("com.ksmti.poc.PublicEventsApp")

// disable parallel tests
parallelExecution in Test := false

headerLicense := Some(HeaderLicense.Custom("""| Copyright (C) 2015-2020 KSMTI
                                              |
                                              | <http://www.ksmti.com>
    """.stripMargin))
scalafmtOnCompile := true

lazy val `PublicEvents` = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(MultiJvmPlugin)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

enablePlugins(JavaServerAppPackaging, DockerPlugin)

// Ports: Http, Management, Artery
dockerExposedPorts := Seq(8080, 8558, 25520)
dockerUpdateLatest := true
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
dockerBaseImage := "adoptopenjdk:11-jre-hotspot"

mainClass in assembly := Some("com.ksmti.poc.PublicEventsApp")

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
