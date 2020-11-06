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
import akka.cluster.ddata.typed.scaladsl.Replicator.GetReplicaCount
import akka.cluster.ddata.typed.scaladsl.Replicator.ReplicaCount
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpecCallbacks
import akka.util.Timeout
import com.ksmti.poc.PublicEventsDirectory
import com.ksmti.poc.PublicEventsDirectory.PublicEvent
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
import com.typesafe.config.ConfigFactory

import scala.language.implicitConversions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.remote.testkit.MultiNodeConfig

trait STMultiNodeSpec
    extends MultiNodeSpecCallbacks
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  self: MultiNodeSpec =>

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = multiNodeSpecAfterAll()

  // Might not be needed anymore if we find a nice way to tag all logging from a node
  override implicit def convertToWordSpecStringWrapper(
      s: String): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}

object PublicEventsConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")

  commonConfig(
    ConfigFactory.parseString(
      """
         akka.loglevel = INFO
         akka.actor.provider = "cluster"
         akka.log-dead-letters-during-shutdown = off
         akka.cluster.sharding.distributed-data.durable.keys = []
         akka.cluster.sharding.remember-entities = on
         akka.actor.serializers.kryo = "com.twitter.chill.akka.AkkaSerializer"
         akka.actor.serialization-bindings {
           "com.ksmti.poc.actor.PublicEventEntity$InvalidEvent$" = kryo
           "com.ksmti.poc.actor.PublicEventEntity$InvalidReservation$" = kryo
           "com.ksmti.poc.actor.PublicEventEntity$AvailableStock" = kryo
           "com.ksmti.poc.actor.PublicEventEntity$SuccessReservation" = kryo
           "com.ksmti.poc.actor.PublicEventEntity$PublicEventStock" = kryo
           "com.ksmti.poc.actor.PublicEventEntity$SeatsReservation" = kryo
         }
         akka.cluster {
           shutdown-after-unsuccessful-join-seed-nodes = 60s
           sharding.distributed-data.durable.keys = []
           min-nr-of-members = 1
           sharding.number-of-shards = 100
           downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
         }
         """))
}

class PublicEventsSpecMultiJvmNode1 extends PublicEventsSpec
class PublicEventsSpecMultiJvmNode2 extends PublicEventsSpec

class PublicEventsSpec
    extends MultiNodeSpec(PublicEventsConfig)
    with STMultiNodeSpec
    with ImplicitSender {

  import PublicEventsConfig._

  def initialParticipants: Int = roles.size

  protected implicit val timeout: Timeout = Timeout(10L, TimeUnit.SECONDS)

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  private val sharding: ClusterSharding = ClusterSharding(typedSystem)

  private val replicator: ActorRef[Replicator.Command] = DistributedData(
    typedSystem).replicator

  val shardRegion: ActorRef[ShardingEnvelope[RequestMessage]] =
    sharding.init(
      Entity(PublicEventEntity.TypeKey)(
        createBehavior = entityContext =>
          PublicEventEntity(
            PublicEventsDirectory.idGenerator(entityContext.entityId),
            replicator: ActorRef[Replicator.Command]
        )))

  val cluster: Cluster = Cluster(typedSystem)

  private lazy val event: PublicEvent =
    PublicEventsDirectory.mspProgram.headOption
      .map(_._2)
      .getOrElse(
        PublicEvent("Undefined", "Undefined", "Undefined", 0, 0.0)
      )

  "PublicEvents" must {
    "cluster setup" in {
      runOn(node1) {
        cluster.manager ! Join(node(node1).address)
      }
      enterBarrier(node1.name + "-joined")

      runOn(node2) {
        cluster.manager ! Join(node(node1).address)
      }
      enterBarrier(node2.name + "-joined")

      awaitAssert {
        val p = TestProbe[ReplicaCount]()
        replicator ! GetReplicaCount(p.ref)
        p.receiveMessage(10.second) should matchPattern {
          case ReplicaCount(2) =>
        }
      }
      enterBarrier("after-1")
    }

    "reject non-existing events (InvalidEvent)" in {
      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope("sample", PublicEventStock(p.ref))
          p.receiveMessage(10.second) should matchPattern {
            case InvalidEvent =>
          }
        }
      }
    }

    "queries the public event stock (PublicEventStock)" in {
      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name, PublicEventStock(p.ref))
          p.receiveMessage(10.seconds) should matchPattern {
            case AvailableStock(event.stock, _) =>
          }
        }
      }
    }

    "performs the public event reservation (SeatsReservation)" in {
      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name,
                                         SeatsReservation(event.stock + 1,
                                                          p.ref))
          p.receiveMessage(3.seconds) should matchPattern {
            case InvalidReservation =>
          }
        }
      }

      runOn(node1) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name,
                                         SeatsReservation(event.stock - 1,
                                                          p.ref))
          p.receiveMessage(3.seconds) should matchPattern {
            case _: SuccessReservation =>
          }
        }
      }

//      runOn(node2) {
//        val p = TestProbe[ResponseMessage]()
//        awaitAssert {
//          shardRegion ! ShardingEnvelope(event.name,
//                                         SeatsReservation(event.stock - 1,
//                                                          p.ref))
//          p.receiveMessage(3.seconds) should matchPattern {
//            case InvalidReservation =>
//          }
//        }
//      }

      runOn(node1, node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name, PublicEventStock(p.ref))
          p.receiveMessage(3.seconds) should matchPattern {
            case AvailableStock(1, _) =>
          }
        }
      }

      runOn(node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name, SeatsReservation(1, p.ref))
          p.receiveMessage(3.seconds) should matchPattern {
            case _: SuccessReservation =>
          }
        }
      }

      runOn(node2) {
        val p = TestProbe[ResponseMessage]()
        awaitAssert {
          shardRegion ! ShardingEnvelope(event.name, PublicEventStock(p.ref))
          p.receiveMessage(3.seconds) should matchPattern {
            case AvailableStock(0, _) =>
          }
        }
      }
    }
  }
}
