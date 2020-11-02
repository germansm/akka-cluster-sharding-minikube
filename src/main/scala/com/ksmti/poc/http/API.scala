/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.http

import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.cluster.sharding.ShardRegion.{
  ClusterShardingStats,
  CurrentShardRegionState
}
import akka.cluster.sharding.typed.{
  ClusterShardingQuery,
  GetClusterShardingStats,
  GetShardRegionState,
  ShardingEnvelope
}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.util.Timeout
import com.ksmti.poc.actor.PublicEventEntity.{
  AvailableStock,
  InvalidEvent,
  InvalidReservation,
  PublicEventStock,
  RequestMessage,
  ResponseMessage,
  SeatsReservation,
  SuccessReservation
}

import scala.util.{Failure, Success}
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.ksmti.poc.{EventsProgram, PublicEventsDirectory}
import com.ksmti.poc.PublicEventsDirectory.PublicEvent
import com.ksmti.poc.actor.PublicEventEntity

trait MessageProtocols extends DefaultJsonProtocol {

  implicit val publicEventFormat: RootJsonFormat[PublicEvent] = jsonFormat5(
    PublicEvent.apply)

  implicit val eventsProgramFormat: RootJsonFormat[EventsProgram] = jsonFormat2(
    EventsProgram)

  implicit val successReservationFormat: RootJsonFormat[SuccessReservation] =
    jsonFormat2(SuccessReservation)

  implicit val availableStockFormat: RootJsonFormat[AvailableStock] =
    jsonFormat2(AvailableStock)
}

trait API extends MessageProtocols {

  type ResponseFunction = PartialFunction[Any, StandardRoute]

  protected def log: LoggingAdapter

  protected implicit def scheduler: Scheduler

  protected implicit val timeout: Timeout = Timeout(10L, TimeUnit.SECONDS)

  private lazy val program = EventsProgram(
    PublicEventsDirectory.mspProgram.values.toSeq)

  protected def shardRegion: ActorRef[ShardingEnvelope[RequestMessage]]

  protected def shardState: ActorRef[ClusterShardingQuery]

  private val commonResponse: ResponseFunction => ResponseFunction = {
    _.orElse({

      case InvalidReservation =>
        complete(StatusCodes.EnhanceYourCalm)

      case InvalidEvent =>
        complete(StatusCodes.ImATeapot)

      case whatever =>
        log.warning("Unexpected response [{}]", whatever)
        complete(StatusCodes.InternalServerError)
    })
  }

  private def requestAndThen(
      entityId: String,
      command: ActorRef[ResponseMessage] => RequestMessage)(
      responseFunction: => ResponseFunction): Route = {
    onComplete(shardRegion.ask[ResponseMessage](sdr =>
      ShardingEnvelope(entityId, command(sdr)))) {
      case Success(result) =>
        commonResponse(responseFunction)(result)
      case Failure(th) =>
        th.printStackTrace()
        complete(StatusCodes.InternalServerError)
    }
  }

  protected def routes: Route =
    path("status") {
      get {
        onComplete(shardState.ask[CurrentShardRegionState](replyTo =>
          GetShardRegionState(PublicEventEntity.TypeKey, replyTo))) {
          case Success(currentShardRegionState) =>
            complete(JsArray(currentShardRegionState.shards.map { s =>
              JsObject(("shardId", JsString(s.shardId)),
                       ("entityIds",
                        JsArray(s.entityIds.map(JsString(_)).toSeq: _*)))
            }.toSeq: _*))
          case Failure(th) =>
            th.printStackTrace()
            complete(StatusCodes.InternalServerError)
        }
      }
    } ~
      path("stats") {
        get {
          onComplete(
            shardState.ask[ClusterShardingStats](replyTo =>
              GetClusterShardingStats(PublicEventEntity.TypeKey,
                                      timeout.duration,
                                      replyTo))) {
            case Success(clusterShardingStats) =>
              complete(JsArray(clusterShardingStats.regions.map { region =>
                JsObject(
                  ("Address", JsString(region._1.toString)),
                  ("Shards", JsArray(region._2.stats.map { s =>
                    JsObject(("ShardId", JsString(s._1)),
                             ("Entities", JsNumber(s._2)))
                  }.toSeq: _*))
                )
              }.toSeq: _*))
            case Failure(th) =>
              th.printStackTrace()
              complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path("upcomingEvents") {
        get(complete(program.stamp))
      } ~
      path("ticketsStock") {
        parameters("event") { event =>
          get {
            requestAndThen(event, PublicEventStock) {
              case stock: AvailableStock =>
                complete(stock)
            }
          }
        }
      } ~
      path("reserveTickets") {
        parameters(("event", "seats".as[Int] ? 1)) { (id, seats) =>
          post {
            requestAndThen(id, SeatsReservation(seats, _)) {
              case resp: SuccessReservation =>
                complete(resp)
            }
          }
        }
      }
}
