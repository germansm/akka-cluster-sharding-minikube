/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{PNCounter, PNCounterKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.ksmti.poc.PublicEventsDirectory
import akka.cluster.ddata.typed.scaladsl.Replicator.{
  Get,
  GetResponse,
  GetSuccess,
  NotFound,
  ReadAll,
  Update,
  UpdateResponse,
  UpdateSuccess,
  WriteAll
}
import akka.util.Timeout
import org.joda.time.DateTime

import scala.util.Success

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

  private case class CommittedSeats(value: Int = 0) extends RequestMessage {
    override def replyTo: ActorRef[ResponseMessage] = None.get
  }

  private case class DDataUpdateResponse(replyTo: ActorRef[ResponseMessage],
                                         committed: Int,
                                         response: UpdateResponse[PNCounter])
      extends RequestMessage

  def apply(entityId: String,
            replicator: ActorRef[Replicator.Command],
            committed: Int = 0,
            ready: Boolean = false): Behavior[RequestMessage] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup { context =>
        implicit val timeout: Timeout = Timeout(10L, TimeUnit.SECONDS)

        implicit val node: SelfUniqueAddress =
          DistributedData(context.system).selfUniqueAddress

        val initialStock: Int = PublicEventsDirectory.mspProgram
          .get(entityId)
          .map(_.stock)
          .getOrElse(0)

        lazy val EntityKey: PNCounterKey = PNCounterKey(entityId)

        context.ask[Get[PNCounter], GetResponse[PNCounter]](
          replicator,
          askReplyTo => Get(EntityKey, ReadAll(timeout.duration), askReplyTo)) {
          case Success(rsp @ GetSuccess(_)) =>
            CommittedSeats(rsp.dataValue.getValue.intValue)
          case Success(_: NotFound[_]) =>
            CommittedSeats()
          case msg =>
            context.log.warn("Unexpected message [{}]", msg)
            CommittedSeats(initialStock + 1)
        }

        Behaviors.receiveMessage[RequestMessage] {

          case CommittedSeats(seats) =>
            buffer.unstashAll(apply(entityId, replicator, seats, ready = true))

          case request if !ready =>
            buffer.stash(request)
            Behaviors.same

          case rq if initialStock == 0 || initialStock < committed =>
            rq.replyTo ! InvalidEvent
            Behaviors.same

          case PublicEventStock(replyTo) =>
            context.log.debug("COMMITTED [{}]", committed)
            replyTo ! AvailableStock(initialStock - committed,
                                     DateTime.now.toDateTimeISO.toString())
            Behaviors.same

          case SeatsReservation(seats, replyTo) =>
            context.log.debug("COMMITTED [{}]", committed)
            if (initialStock >= committed + seats) {
              context.ask[Update[PNCounter], UpdateResponse[PNCounter]](
                replicator,
                askReplyTo =>
                  Update(EntityKey,
                         PNCounter.empty,
                         WriteAll(timeout.duration),
                         askReplyTo)(_ :+ seats)
              ) {
                case Success(rsp) =>
                  DDataUpdateResponse(replyTo, committed + seats, rsp)
                case msg =>
                  context.log.warn("Unexpected message [{}]", msg)
                  CommittedSeats(initialStock + 1)
              }
            } else {
              replyTo ! InvalidReservation
            }
            Behaviors.same

          case DDataUpdateResponse(replyTo, committed, _: UpdateSuccess[_]) =>
            replyTo ! SuccessReservation(DateTime.now().getMillis,
                                         DateTime.now.toDateTimeISO.toString())
            apply(entityId, replicator, committed, ready)
        }
      }
    }
}
