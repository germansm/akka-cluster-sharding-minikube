/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.typed.{Cluster, Join}
import akka.util.Timeout
import com.ksmti.poc.PublicEventsDirectory
import com.ksmti.poc.PublicEventsDirectory.PublicEvent
import com.ksmti.poc.actor.PublicEventEntity.{
  AvailableStock,
  InvalidEvent,
  InvalidReservation,
  PublicEventStock,
  ResponseMessage,
  SeatsReservation,
  SuccessReservation
}

import scala.concurrent.duration.DurationInt

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender

class PublicEventEntitySpecMultiJvmNode1 extends PublicEventEntitySpec
class PublicEventEntitySpecMultiJvmNode2 extends PublicEventEntitySpec

class PublicEventEntitySpec
    extends MultiNodeSpec(PublicEventsConfig)
    with STMultiNodeSpec
    with ImplicitSender {

  import PublicEventsConfig._

  def initialParticipants: Int = roles.size

  protected implicit val timeout: Timeout = Timeout(10L, TimeUnit.SECONDS)

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  private val replicator: ActorRef[Replicator.Command] = DistributedData(
    typedSystem).replicator

  val cluster: Cluster = Cluster(typedSystem)

  private lazy val event: PublicEvent =
    PublicEventsDirectory.mspProgram.headOption
      .map(_._2)
      .getOrElse(
        PublicEvent("Undefined", "Undefined", "Undefined", 0, 0.0)
      )

  "PublicEventEntity" must {
    "cluster setup" in {
      runOn(node1) {
        cluster.manager ! Join(node(node1).address)
      }
      enterBarrier(node1.name + "-joined")

      runOn(node2) {
        cluster.manager ! Join(node(node1).address)
      }
      enterBarrier(node2.name + "-joined")
    }
    "reject non-existing events (InvalidEvent)" in {
      val entityActor =
        system.spawnAnonymous(PublicEventEntity("sample", replicator))
      runOn(node1) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! PublicEventStock(p.ref)
          p.receiveMessage(10.second) should matchPattern {
            case InvalidEvent =>
          }
        }
      }
    }

    "queries the public event stock (PublicEventStock)" in {
      val entityActor =
        system.spawnAnonymous(
          PublicEventEntity(PublicEventsDirectory.idGenerator(event.name),
                            replicator))
      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! PublicEventStock(p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case AvailableStock(event.stock, _) =>
          }
        }
      }
    }

    "performs the public event reservation (SeatsReservation)" in {
      val entityActor =
        system.spawnAnonymous(
          PublicEventEntity(PublicEventsDirectory.idGenerator(event.name),
                            replicator))
      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! SeatsReservation(event.stock + 1, p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case InvalidReservation =>
          }
        }
      }

      runOn(node1) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! SeatsReservation(event.stock - 1, p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case _: SuccessReservation =>
          }
        }
      }

      runOn(node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! SeatsReservation(event.stock - 1, p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case InvalidReservation =>
          }
        }
      }

      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! PublicEventStock(p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case AvailableStock(1, _) =>
          }
        }
      }

      runOn(node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! SeatsReservation(1, p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case _: SuccessReservation =>
          }
        }
      }

      runOn(node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          entityActor ! PublicEventStock(p.ref)
          p.receiveMessage(3.seconds) should matchPattern {
            case AvailableStock(0, _) =>
          }
        }
      }
    }
  }
}
