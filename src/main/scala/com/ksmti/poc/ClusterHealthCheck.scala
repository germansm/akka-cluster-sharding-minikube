/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}

import scala.concurrent.Future

class ClusterHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val cluster = Cluster(system)
  override def apply(): Future[Boolean] = {
    system.log
      .info("ClusterHealthCheck with [{}] ...", cluster.selfMember.status)
    Future.successful(cluster.selfMember.status == MemberStatus.Up)
  }
}
