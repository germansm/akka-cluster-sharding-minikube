/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import akka.actor.ActorSystem

import scala.concurrent.Future

class PublicEventsHealthCheck(system: ActorSystem)
    extends (() => Future[Boolean]) {

  override def apply(): Future[Boolean] = {
    system.log.debug("PublicEventsHealthCheck ...")
    Future.successful(true)
  }
}
