/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestTemplateConfig.restTemplate() binds ovsx.foregroundHttpConnPool.socketTimeout through
 * ConnectionConfig.setSocketTimeout (GHSA-fq82-m65g-jfrh: this was previously accepted and stored but
 * never applied, leaving every RestTemplate's read unbounded). This drives a real request against a
 * server that accepts the connection and never responds, so removing that one setter call would make
 * this test hang instead of pass.
 */
class RestTemplateConfigTest {

    @Test
    void appliesTheConfiguredSocketTimeoutToARealRequest() throws Exception {
        try (var serverSocket = new ServerSocket(0)) {
            var acceptThread = new Thread(() -> {
                try (var socket = serverSocket.accept()) {
                    var in = socket.getInputStream();
                    var buffer = new byte[4096];
                    // Keep draining whatever the client sends but never respond - closing the socket
                    // here (e.g. after a single read()) would let the client see EOF immediately
                    // instead of the client's own read timeout actually firing.
                    while (in.read(buffer) >= 0) {
                        // discard
                    }
                } catch (Exception ignored) {
                    // client closed on its own timeout, or the test is tearing down
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            var httpConnPoolConfig = new RestTemplateConfig.HttpConnPoolConfig(
                    new PoolingHttpClientConnectionManager(),
                    2_000,
                    2_000,
                    500); // the timeout under test, kept short so a passing test stays fast
            var restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder(), httpConnPoolConfig);
            var url = "http://127.0.0.1:" + serverSocket.getLocalPort() + "/stalls-forever";

            var failure = new AtomicReference<Throwable>();
            var caller = new Thread(() -> {
                try {
                    restTemplate.getForObject(url, String.class);
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            caller.setDaemon(true);
            var start = System.currentTimeMillis();
            caller.start();
            caller.join(5_000); // generous upper bound; the configured socket timeout is 500ms
            var elapsed = System.currentTimeMillis() - start;

            assertThat(caller.isAlive())
                    .as("the configured socket timeout must bound the read, not hang indefinitely")
                    .isFalse();
            assertThat(failure.get()).isInstanceOf(ResourceAccessException.class);
            assertThat(elapsed)
                    .as("should fail close to the configured 500ms timeout, not the 5s test watchdog")
                    .isLessThan(5_000);
        }
    }
}
