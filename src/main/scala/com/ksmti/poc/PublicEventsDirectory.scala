/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import com.ksmti.poc.PublicEventsDirectory.PublicEvent
import com.ksmti.poc.actor.PublicEventEntity.ResponseMessage
import com.typesafe.config.{Config, ConfigFactory}
import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._

case class EventsProgram(program: Seq[PublicEvent],
                         timeStamp: Option[String] = None)
    extends ResponseMessage {

  def stamp: EventsProgram = {
    copy(timeStamp = Some(DateTime.now().toDateTimeISO.toString()))
  }
}

object PublicEventsDirectory {

  type PublicEventID = String

  case object PublicEvent {
    def apply(config: Config): PublicEvent = PublicEvent(
      config.getString("name"),
      config.getString("location"),
      config.getString("date"),
      config.getInt("stock"),
      config.getDouble("price")
    )
  }

  case class PublicEvent(name: String,
                         location: String,
                         date: String,
                         stock: Int,
                         price: Double)

  lazy val mspProgram: Map[PublicEventID, PublicEvent] = {
    Try {
      val config: Config = ConfigFactory.load()
      config
        .getConfigList("publicEvents.program")
        .asScala
        .map(PublicEvent(_))
        .map(e => (idGenerator(e.name), e))
    } match {
      case Success(events) =>
        events.toMap
      case Failure(th) =>
        th.printStackTrace()
        Map.empty
    }
  }
  val idGenerator: String => String = _.hashCode.toString
}
