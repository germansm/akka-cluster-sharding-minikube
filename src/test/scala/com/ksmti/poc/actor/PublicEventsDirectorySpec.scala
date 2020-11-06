/*
 *  Copyright (C) 2015-2020 KSMTI
 *
 *  <http://www.ksmti.com>
 *
 */

package com.ksmti.poc.actor

import com.ksmti.poc.PublicEventsDirectory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PublicEventsDirectorySpec extends AnyFlatSpec with Matchers {

  "PublicEventsDirectory" should "Return Events by Name" in {
    assert(PublicEventsDirectory.mspProgram.headOption.nonEmpty)
    assert(!PublicEventsDirectory.mspProgram.contains("SCALA.IO"))
    assert(
      PublicEventsDirectory.mspProgram.contains(
        PublicEventsDirectory.idGenerator("SCALA.IO")))
    assert(
      PublicEventsDirectory.mspProgram
        .get(PublicEventsDirectory.idGenerator("SCALA.IO"))
        .map(_.stock)
        .getOrElse(0) > 1)
  }
}
