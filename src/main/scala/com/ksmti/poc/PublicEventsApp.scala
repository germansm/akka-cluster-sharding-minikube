/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import akka.actor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityTypeKey
}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.ksmti.poc.actor.PublicEventEntity
import com.ksmti.poc.actor.PublicEventEntity.RequestMessage
import com.ksmti.poc.http.API
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext

class HttpListener(
    hostname: String,
    port: Int,
    override val shardRegion: ActorRef[ShardingEnvelope[RequestMessage]],
    system: ActorSystem[Nothing])
    extends API {

  private implicit lazy val innerSystem: actor.ActorSystem =
    system.classicSystem

  private implicit lazy val ec: ExecutionContext = innerSystem.dispatcher

  Http()
    .bindAndHandle(routes, hostname, port)
    .foreach { binding =>
      system.log.info("Started Service using [{}]", binding)
    }

  override protected def log: LoggingAdapter = innerSystem.log

  override protected implicit def scheduler: Scheduler = system.scheduler
}

object PublicEventsApp {
  def main(args: Array[String]): Unit = {
    val hostname = args.headOption.getOrElse("127.0.0.1")
    val port = args.drop(1).headOption.map(_.toInt).getOrElse(8080)

    val system =
      ActorSystem[Nothing](Behaviors.empty,
                           "PublicEventsSystem",
                           getConfig(hostname))

    val sharding = ClusterSharding(system)

    val TypeKey = EntityTypeKey[RequestMessage]("PublicEventEntity")

    val shardRegion: ActorRef[ShardingEnvelope[RequestMessage]] =
      sharding.init(
        Entity(TypeKey)(createBehavior = entityContext =>
          PublicEventEntity(
            PublicEventsDirectory.idGenerator(entityContext.entityId))))

    // Starts the Listener
    new HttpListener(hostname, port, shardRegion, system)
    // Starts Akka Management
    AkkaManagement(system).start()
    // Init Cluster Bootstrap
    ClusterBootstrap(system).start()
  }

  protected def getConfig(hostname: String): Config = {
    ConfigFactory
      .parseString(s"""
                      |akka.remote.artery.canonical.hostname = "$hostname"
                      |akka.management.http.hostname = "$hostname"
         """.stripMargin)
      .withFallback(ConfigFactory.load())
  }
}
