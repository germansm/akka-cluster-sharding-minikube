/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.ksmti.poc.actor.PublicEventsManager
import com.ksmti.poc.http.API
import com.typesafe.config.{Config, ConfigFactory}

class HttpListener(
    hostname: String,
    port: Int,
    override val shardRegion: ActorRef)(implicit system: ActorSystem)
    extends API {

  implicit val materializer: Materializer = Materializer(system)

  Http()
    .bindAndHandle(routes, hostname, port)
    .foreach { binding =>
      system.log.info("Started Service using [{}]", binding)
    }(system.dispatcher)

  override protected lazy val log: LoggingAdapter = system.log
}

trait SystemCreator {
  def createSystem(hostname: String, name: String): ActorSystem =
    ActorSystem(name, getConfig(hostname))

  protected def getConfig(hostname: String): Config = {
    ConfigFactory
      .parseString(s"""
           |akka.remote.artery.canonical.hostname = "$hostname"
           |akka.management.http.hostname = "$hostname"
         """.stripMargin)
      .withFallback(ConfigFactory.load())
  }

  def node(hostname: String, systemName: String = "PublicEventsSystem")(
      postCreationF: => ActorSystem => Unit): Unit = {
    val system = createSystem(hostname, systemName)
    postCreationF(system)
  }
}

object PublicEventsApp extends SystemCreator {
  def main(args: Array[String]): Unit = {
    val hostname = args.headOption.getOrElse("127.0.0.1")
    val port = args.drop(1).headOption.map(_.toInt).getOrElse(8080)
    node(hostname) { implicit system =>
      // Starts the Listener
      new HttpListener(
        hostname,
        port,
        system.actorOf(Props[PublicEventsManager](), "PublicEventsManager"))
//       Starts Akka Management
      AkkaManagement(system).start()
//       Cluster Bootstrap
      ClusterBootstrap(system).start()
      Cluster(system).registerOnMemberUp({
        system.log.info("Cluster Ready !")
      })
    }
  }
}
