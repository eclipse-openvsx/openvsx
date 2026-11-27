/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration.DurationInt

import org.eclipse.openvsx.Scenarios.conf

/** Stepped open-model load of read-only registry API and VS Code adapter requests, for comparing
  * the same workload across deployments. Each virtual user sends one request from a weighted mix.
  */
class ReadMixStressSimulation extends Simulation {
  private def setting(path: String, default: Int): Int =
    if (conf.hasPath(path)) conf.getInt(path) else default

  private val baseUrl = conf.getString("baseUrl")
  private val peakRps = setting("stress.peakRps", 50)
  private val steps = setting("stress.steps", 5)
  private val stepSeconds = setting("stress.stepSeconds", 60)
  private val rampSeconds = setting("stress.rampSeconds", 10)
  private val cacheBust = conf.hasPath("stress.cacheBust") && conf.getBoolean("stress.cacheBust")

  // Shared connections: real clients keep them open, and a TLS handshake per request would
  // dominate the latencies being compared.
  private val protocol = {
    val base = http.baseUrl(baseUrl).shareConnections.disableCaching.acceptHeader("application/json")
    if (conf.hasPath("auth")) base.authorizationHeader(conf.getString("auth")) else base
  }

  // A unique query parameter keeps every cache layer from answering, exposing origin latency.
  private def bust(request: HttpRequestBuilder): HttpRequestBuilder =
    if (cacheBust) request.queryParam("cb", "#{randomUuid()}") else request

  private def get(name: String, feeder: String, path: String): ChainBuilder =
    feed(csv(feeder).circular).exec(bust(http(name).get(path)))

  private val extensionQuery =
    """{"filters":[{"criteria":[{"filterType":8,"value":"Microsoft.VisualStudio.Code"},""" +
      """{"filterType":10,"value":"#{query}"},{"filterType":12,"value":"4096"}],""" +
      """"pageNumber":1,"pageSize":50,"sortBy":0,"sortOrder":0}],"assetTypes":[],"flags":950}"""

  private val readMix = scenario(s"Read mix: $baseUrl").randomSwitch(
    20.0 -> get("RegistryAPI.getExtension", "extensions.csv", "/api/#{namespace}/#{name}"),
    10.0 -> get("RegistryAPI.getExtensionVersion", "extension-versions.csv", "/api/#{namespace}/#{name}/#{version}"),
    5.0 -> get("RegistryAPI.getVersionReferences", "extensions.csv", "/api/#{namespace}/#{name}/version-references"),
    5.0 -> get("RegistryAPI.getNamespace", "namespaces.csv", "/api/#{namespace}"),
    10.0 -> get("RegistryAPI.getQueryV2", "query-v2-strings.csv", "/api/v2/-/query?#{query}"),
    15.0 -> get("RegistryAPI.search", "adapter/queries.csv", "/api/-/search?query=#{query}&size=50"),
    10.0 -> get("VSCodeAdapter.browse", "extension-versions.csv",
      "/vscode/unpkg/#{namespace}/#{name}/#{version}/extension.vsixmanifest"),
    25.0 -> feed(csv("adapter/queries-xl.csv").circular)
      .exec(bust(http("VSCodeAdapter.extensionQuery")
        .post("/vscode/gallery/extensionquery")
        .body(StringBody(extensionQuery)).asJson))
  )

  private val increment = peakRps.toDouble / steps

  // levels are startingFrom + n * increment, so starting at one increment ends at peakRps
  setUp(
    readMix.inject(
      incrementUsersPerSec(increment)
        .times(steps)
        .eachLevelLasting(stepSeconds.seconds)
        .separatedByRampsLasting(rampSeconds.seconds)
        .startingFrom(increment)
    )
  ).protocols(protocol)
}
