/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import akka.actor
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.cluster.sharding.typed.{ClusterShardingQuery, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.ksmti.poc.actor.PublicEventEntity
import com.ksmti.poc.actor.PublicEventEntity.RequestMessage
import com.ksmti.poc.http.API
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

class HttpListener(hostname: String, port: Int, context: ActorContext[Nothing])
    extends API {

  private val sharding: ClusterSharding = ClusterSharding(context.system)

  override val shardRegion: ActorRef[ShardingEnvelope[RequestMessage]] =
    sharding.init(
      Entity(PublicEventEntity.TypeKey)(createBehavior = entityContext =>
        PublicEventEntity(
          PublicEventsDirectory.idGenerator(entityContext.entityId))))

  override val shardState: ActorRef[ClusterShardingQuery] = sharding.shardState

  private implicit lazy val innerSystem: actor.ActorSystem =
    context.system.classicSystem

  private implicit lazy val ec: ExecutionContext = innerSystem.dispatcher

  Http()
    .bindAndHandle(routes, hostname, port)
    .foreach { binding =>
      context.system.log.info("Started Listener using [{}]", binding)
    }

  override protected def log: LoggingAdapter = innerSystem.log

  override protected implicit def scheduler: Scheduler =
    context.system.scheduler
}

object PublicEventsApp extends App {
  // For Local Deployment it can be provided
  val hostname = args.headOption.getOrElse("0.0.0.0")
  val port = args.drop(1).headOption.map(_.toInt).getOrElse(8080)
  val systemName = args.drop(2).headOption.getOrElse("public-events")
  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      // Starts the Listener
      new HttpListener(hostname, port, context)
      // Starts Akka Management
      AkkaManagement(context.system).start()
      // Init Cluster Bootstrap
      ClusterBootstrap(context.system).start()
      Behaviors.empty
    },
    systemName,
    args.headOption.fold(ConfigFactory.load()) { _ =>
      ConfigFactory
        .parseString(
          s"""akka.remote.artery.canonical.hostname="$hostname""""
        )
        .withFallback(ConfigFactory.load())
    }
  )
}
