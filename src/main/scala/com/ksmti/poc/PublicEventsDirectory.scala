/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

import scala.jdk.CollectionConverters._

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
      val config: Config = ConfigFactory.parseResources("program.conf")
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
