/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import akka.actor.{Actor, ActorLogging}
import com.ksmti.poc.PublicEventsDirectory
import com.ksmti.poc.actor.PublicEventEntity.{
  AvailableStock,
  InvalidReservation,
  InvalidEvent,
  PublicEventStock,
  SeatsReservation,
  SuccessReservation
}
import org.joda.time.DateTime

object PublicEventEntity {

  object ReservationResponse

  case class SeatsReservation(seats: Int = 1) extends RequestMessage {
    require(seats > 0)
  }

  object PublicEventStock extends RequestMessage

  case class AvailableStock(stock: Int, timeStamp: String)
      extends ResponseMessage

  case class SuccessReservation(reservationID: Long, timeStamp: String)
      extends ResponseMessage

  object InvalidEvent extends ResponseMessage

  object InvalidReservation extends ResponseMessage

}

class PublicEventEntity extends Actor with ActorLogging {

  private lazy val eventID: String = self.path.name

  private lazy val initialStock: Int = PublicEventsDirectory.mspProgram
    .get(eventID)
    .map(_.stock)
    .getOrElse(0)

  override def receive: Receive = {
    case _ =>
      sender() ! InvalidEvent
  }

  private lazy val eventBehavior: Int => Receive = { committed =>
    {
      case PublicEventStock =>
        log.debug("COMMITTED [{}]", committed)
        sender() ! AvailableStock(initialStock - committed,
                                  DateTime.now.toDateTimeISO.toString())

      case SeatsReservation(seats) =>
        log.debug("COMMITTED [{}]", committed)
        if (initialStock >= committed + seats) {
          context.become(eventBehavior(committed + seats))
          sender() ! SuccessReservation(DateTime.now().getMillis,
                                        DateTime.now.toDateTimeISO.toString())
        } else {
          sender() ! InvalidReservation
        }
    }
  }

  override def preStart(): Unit = {
    log.debug("Starting PublicEventEntity ID [{}]", eventID)
    if (initialStock > 0) {
      context.become(eventBehavior(0))
    }
    super.preStart()
  }
}
