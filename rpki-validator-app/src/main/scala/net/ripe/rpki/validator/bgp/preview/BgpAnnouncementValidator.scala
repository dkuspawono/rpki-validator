/**
 * The BSD License
 *
 * Copyright (c) 2010, 2011 RIPE NCC
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
package net.ripe.rpki.validator
package bgp.preview

import scala.collection.JavaConverters._
import scala.concurrent.SyncVar
import scala.concurrent.ops._
import scalaz.Reducer
import scalaz.concurrent.Promise
import net.ripe.commons.certification.validation.roa.RouteValidityState
import lib.Process._
import lib.NumberResources._
import models.RtrPrefix
import net.ripe.ipresource.Asn
import net.ripe.ipresource.IpRange
import grizzled.slf4j.Logging

case class AnnouncedRoute(asn: Asn, prefix: IpRange) {
  val interval = NumberResourceInterval(prefix.getStart(), prefix.getEnd())
}
case class ValidatedAnnouncement(route: AnnouncedRoute, validity: RouteValidityState) {
  def asn = route.asn
  def prefix = route.prefix
}

object BgpAnnouncementValidator extends Logging {

  val VISIBILITY_THRESHOLD = 5

  val announcedRoutes: Promise[Set[AnnouncedRoute]] = Promise {
    val bgpEntries =
      RisWhoisParser.parseFromUrl(new java.net.URL("http://www.ris.ripe.net/dumps/riswhoisdump.IPv4.gz")) ++
        RisWhoisParser.parseFromUrl(new java.net.URL("http://www.ris.ripe.net/dumps/riswhoisdump.IPv6.gz"))

    bgpEntries
      .filter(_.visibility >= VISIBILITY_THRESHOLD)
      .map(entry => AnnouncedRoute(entry.origin, entry.prefix))
      .toSet
  }

  @volatile
  var validatedAnnouncements = IndexedSeq.empty[ValidatedAnnouncement]

  private val latestRtrPrefixes = new SyncVar[Set[RtrPrefix]]

  def updateRtrPrefixes(newRtrPrefixes: Set[RtrPrefix]): Unit = latestRtrPrefixes.set(newRtrPrefixes)

  spawnForever("bgp-validator") {
    def validPrefix(prefix: RtrPrefix, announced: AnnouncedRoute): Boolean = {
      prefix.asn == announced.asn &&
        prefix.maxPrefixLength.getOrElse(prefix.prefix.getPrefixLength()) >= announced.prefix.getPrefixLength()
    }

    val newRtrPrefixes = latestRtrPrefixes.take()
    val routes = announcedRoutes.get

    info("Started validating " + routes.size + " BGP announcements with " + newRtrPrefixes.size + " RTR prefixes.")
    val prefixTree = NumberResourceIntervalTree(newRtrPrefixes.toSeq: _*)

    validatedAnnouncements = routes.par.map(
      route => {
        val matchingPrefixes = prefixTree.filterContaining(route.interval)
        val validity = {
          if (matchingPrefixes.isEmpty) RouteValidityState.UNKNOWN
          else if (matchingPrefixes.exists(validPrefix(_, route))) RouteValidityState.VALID
          else RouteValidityState.INVALID
        }
        ValidatedAnnouncement(route, validity)
      }).seq.toIndexedSeq

    info("Completed validating " + routes.size + " BGP announcements with " + newRtrPrefixes.size + " RTR prefixes.")
  }
}
