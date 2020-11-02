/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.ksmti.poc.PublicEventsDirectory
import org.joda.time.DateTime

object PublicEventEntity {

  val TypeKey: EntityTypeKey[RequestMessage] =
    EntityTypeKey[RequestMessage]("PublicEventEntity")

  trait RequestMessage {
    def replyTo: ActorRef[ResponseMessage]
  }

  trait ResponseMessage

  case class SeatsReservation(seats: Int = 1,
                              replyTo: ActorRef[ResponseMessage])
      extends RequestMessage {
    require(seats > 0)
  }

  case class PublicEventStock(replyTo: ActorRef[ResponseMessage])
      extends RequestMessage

  case class AvailableStock(stock: Int, timeStamp: String)
      extends ResponseMessage

  case class SuccessReservation(reservationID: Long, timeStamp: String)
      extends ResponseMessage

  object InvalidEvent extends ResponseMessage

  object InvalidReservation extends ResponseMessage

  def apply(entityId: String, committed: Int = 0): Behavior[RequestMessage] =
    Behaviors.setup { context =>
      val initialStock: Int = PublicEventsDirectory.mspProgram
        .get(entityId)
        .map(_.stock)
        .getOrElse(0)

      Behaviors.receiveMessage[RequestMessage] {

        case rq if initialStock == 0 =>
          rq.replyTo ! InvalidEvent
          Behaviors.same

        case PublicEventStock(replyTo) =>
          context.log.debug("COMMITTED [{}]", committed)
          replyTo ! AvailableStock(initialStock - committed,
                                   DateTime.now.toDateTimeISO.toString())
          Behaviors.same

        case SeatsReservation(_, replyTo) if committed == initialStock =>
          context.log.debug("COMMITTED [{}]", committed)
          replyTo ! InvalidReservation
          Behaviors.same

        case SeatsReservation(seats, replyTo) =>
          context.log.debug("COMMITTED [{}]", committed)
          if (initialStock >= committed + seats) {
            replyTo ! SuccessReservation(DateTime.now().getMillis,
                                         DateTime.now.toDateTimeISO.toString())
            apply(entityId, committed + seats)
          } else {
            replyTo ! InvalidReservation
            Behaviors.same
          }
      }
    }
}
