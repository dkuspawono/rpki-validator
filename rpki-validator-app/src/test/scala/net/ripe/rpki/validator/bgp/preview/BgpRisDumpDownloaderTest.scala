/**
 * The BSD License
 *
 * Copyright (c) 2010-2012 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator.bgp.preview

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.ShouldMatchers
import javax.servlet.http.HttpServletResponse._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.ResponseHandler
import org.apache.http.ProtocolVersion
import org.apache.http.message._
import org.apache.http.HttpEntity
import org.apache.http.entity._
import org.apache.commons.io.IOUtils
import org.apache.http.impl.cookie.DateUtils
import org.joda.time.DateTime
import java.util.Date

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class BgpRisDumpDownloaderTest extends FunSuite with BeforeAndAfterAll with ShouldMatchers {

  val http11Protocol = new ProtocolVersion("http", 1, 1)
  val dump = BgpRisDump(url = "http://no.where/dump.4.gz")

  test("should stick to old dump, if new dump cannot be retrieved") {

    // given
    val responseHandler = BgpRisDumpDownloader.makeResponseHandler(dump)
    val statusLine = new BasicStatusLine(http11Protocol, SC_INTERNAL_SERVER_ERROR, null)
    val response = new BasicHttpResponse(statusLine)

    // when
    val dumpFromResponse = responseHandler.handleResponse(response)

    // then
    dumpFromResponse should equal(dump)
  }

  test("should stick to old dump if it's not modified") {
    // given
    val responseHandler = BgpRisDumpDownloader.makeResponseHandler(dump)
    val statusLine = new BasicStatusLine(http11Protocol, SC_NOT_MODIFIED, null)
    val response = new BasicHttpResponse(statusLine)

    // when
    val dumpFromResponse = responseHandler.handleResponse(response)

    // then
    dumpFromResponse should equal(dump)
  }
  
  test("should stick to old dump if new dump can't be parsed") {
      // given
      val responseHandler = BgpRisDumpDownloader.makeResponseHandler(dump)
              val statusLine = new BasicStatusLine(http11Protocol, SC_NOT_MODIFIED, null)
      val response = new BasicHttpResponse(statusLine)
      
      // when
      val dumpFromResponse = responseHandler.handleResponse(response)
      
      // then
      dumpFromResponse should equal(dump)
  }

  test("should download and unzip new dump") {
    // given
    val responseHandler = BgpRisDumpDownloader.makeResponseHandler(dump)
    val statusLine = new BasicStatusLine(http11Protocol, SC_OK, null)
    val response = new BasicHttpResponse(statusLine)


    val entity = new StringEntity("not gzip format")
    entity.setContentType("application/x-gzip")

    val lastMidnight = new Date(new DateTime().toDateMidnight.getMillis) // Need something truncated at second

    response.setEntity(entity)
    response.setHeader("Last-Modified", DateUtils.formatDate(lastMidnight))

    // when
    val dumpFromResponse = responseHandler.handleResponse(response)

    // then
    dumpFromResponse should equal (dump)
  }

}