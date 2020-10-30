/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.sharding.ShardRegion.ShardRegionQuery
import akka.cluster.sharding.{
  ClusterSharding,
  ClusterShardingSettings,
  ShardRegion
}
import com.ksmti.poc.PublicEventsDirectory
import com.ksmti.poc.PublicEventsDirectory.PublicEvent
import com.ksmti.poc.actor.PublicEventsManager.{
  ConsultProgram,
  EventsProgram,
  ReservationRequest,
  StockRequest
}
import com.ksmti.poc.actor.PublicEventEntity.{
  PublicEventStock,
  SeatsReservation
}
import org.joda.time.DateTime

trait RequestMessage

trait ResponseMessage

object PublicEventsManager {

  case object ConsultProgram extends RequestMessage

  case class EventsProgram(program: Seq[PublicEvent],
                           timeStamp: Option[String] = None)
      extends ResponseMessage {

    def stamp: EventsProgram = {
      copy(timeStamp = Some(DateTime.now().toDateTimeISO.toString()))
    }
  }

  case class StockRequest(event: String) extends RequestMessage

  case class ReservationRequest(entityID: String, seats: Int = 1)
      extends RequestMessage
}

class PublicEventsManager extends Actor with ActorLogging {

  private lazy val publicEvents = PublicEventsDirectory.mspProgram

  private val program = EventsProgram(publicEvents.values.toSeq)

  private val shardingRegion = ClusterSharding(context.system).start(
    typeName = "PublicEventEntity",
    entityProps = Props[PublicEventEntity](),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = {
      case StockRequest(id) =>
        (PublicEventsDirectory.idGenerator(id), PublicEventStock)

      case ReservationRequest(id, seats) =>
        (PublicEventsDirectory.idGenerator(id), SeatsReservation(seats))
    },
    extractShardId = {
      case ShardRegion.StartEntity(id) =>
        shardId(id)

      // see https://manuel.bernhardt.io/2018/02/26/tour-akka-cluster-cluster-sharding/
      case StockRequest(id) =>
        shardId(PublicEventsDirectory.idGenerator(id))

      case ReservationRequest(id, _) =>
        shardId(PublicEventsDirectory.idGenerator(id))
    }
  )

  private val shardId: String => String = { uid =>
    (math.abs(uid.hashCode) % 5).toString
  }

  override def receive: Receive = {
    case ConsultProgram =>
      log.info("Received a ConsultProgram Request at [{}]", DateTime.now)
      sender() ! program.stamp

    case msg: RequestMessage =>
      shardingRegion.forward(msg)

    case query: ShardRegionQuery =>
      shardingRegion.forward(query)
  }

  override def preStart(): Unit = {
    log.info("Starting the PublicEventsManager ...")
    super.preStart()
  }
}
