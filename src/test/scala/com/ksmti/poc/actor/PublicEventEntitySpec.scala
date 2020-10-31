/*  Copyright (C) 2015-2019 KSMTI
 *
 *  <http://www.ksmti.com>
 */

package com.ksmti.poc.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import com.ksmti.poc.PublicEventsDirectory
import com.ksmti.poc.PublicEventsDirectory.PublicEvent
import com.ksmti.poc.actor.PublicEventEntity.{
  AvailableStock,
  InvalidEvent,
  InvalidReservation,
  PublicEventStock,
  RequestMessage,
  SeatsReservation,
  SuccessReservation
}
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class PublicEventEntitySpec
    extends AsyncWordSpecLike
    with BeforeAndAfterAll
    with Matchers {

  private lazy val testKit = ActorTestKit()

  implicit lazy val timeOut: Timeout = 3.second

  implicit lazy val scheduler: Scheduler = testKit.system.scheduler

  implicit lazy val ec: ExecutionContext = testKit.system.executionContext

  private val stopActorRef: ActorRef[RequestMessage] => Future[Assertion] = {
    ref =>
      testKit.stop(ref)
      Future(succeed)
  }

  private def processResponse[T](
      responseFunction: PartialFunction[T, Future[Assertion]])
    : T => Future[Assertion] = {
    responseFunction.orElse({
      case whatever =>
        fail(new UnsupportedOperationException(whatever.toString))
    }: PartialFunction[T, Future[Assertion]])(_)
  }

  private lazy val event: PublicEvent =
    PublicEventsDirectory.mspProgram.headOption
      .map(_._2)
      .getOrElse(
        PublicEvent("Undefined", "Undefined", "Undefined", 0, 0.0)
      )

  "PublicEventEntity " should {
    " InvalidEvent " in {
      val entityActor =
        testKit.spawn(PublicEventEntity("sample"))

      (entityActor ? PublicEventStock).flatMap {
        processResponse {
          case InvalidEvent =>
            stopActorRef(entityActor)
        }
      }
    }

    " ScalaEventStock " in {
      val entityActor = testKit.spawn(
        PublicEventEntity(PublicEventsDirectory.idGenerator(event.name)))

      (entityActor ? PublicEventStock).flatMap {
        processResponse {
          case AvailableStock(stk, _) =>
            stk shouldBe event.stock
            stopActorRef(entityActor)
        }
      }
    }

    " SeatsReservation" in {
      val entityActor = testKit.spawn(
        PublicEventEntity(PublicEventsDirectory.idGenerator(event.name)))

      (entityActor ? (SeatsReservation(event.stock + 1, _))).map {
        case InvalidReservation =>
          succeed
        case whatever =>
          fail(new UnsupportedOperationException(whatever.toString))
      }

      (entityActor ? (SeatsReservation(event.stock - 1, _))).map {
        case _: SuccessReservation =>
          succeed
        case whatever =>
          fail(new UnsupportedOperationException(whatever.toString))
      }
      stopActorRef(entityActor)
    }

    " Serial Operations " in {
      val entityActor =
        testKit.spawn(
          PublicEventEntity(PublicEventsDirectory.idGenerator(event.name)))

      def testReservation(attempts: Long,
                          attempt: Long = 1): Future[Assertion] = {

        if (attempt <= attempts) {
          (entityActor ? (SeatsReservation(1, _))).flatMap {
            processResponse {
              case _: SuccessReservation =>
                (entityActor ? PublicEventStock).flatMap {
                  processResponse {
                    case AvailableStock(stock, _)
                        if event.stock - attempt == stock =>
                      testReservation(attempts, attempt + 1)
                  }
                }
            }
          }
        } else {
          (entityActor ? (SeatsReservation(1, _))).flatMap {
            processResponse {
              case InvalidReservation =>
                succeed
            }
          }
        }
      }
      testReservation(event.stock).flatMap { _ =>
        stopActorRef(entityActor)
      }
    }

    " Parallel Operations " in {
      val entityActor =
        testKit.spawn(
          PublicEventEntity(PublicEventsDirectory.idGenerator(event.name)))

      Future
        .traverse(1 to event.stock) { _ =>
          (entityActor ? (SeatsReservation(1, _))).flatMap {
            case _: SuccessReservation =>
              (entityActor ? PublicEventStock).map {
                case AvailableStock(stk, _) if stk <= event.stock =>
                  succeed
              }
          }
        }
        .flatMap { response =>
          (entityActor ? PublicEventStock).flatMap {
            case _ if response.size == event.stock =>
              stopActorRef(entityActor)
          }
        }
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
