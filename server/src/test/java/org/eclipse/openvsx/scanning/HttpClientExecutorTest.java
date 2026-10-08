/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientExecutorTest {

    private HttpServer server;
    private HttpClientExecutor executor;
    private RemoteScannerProperties.HttpOperation operation;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
            int status = "/redirect".equals(exchange.getRequestURI().getPath()) ? 300 : 429;
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        executor = HttpClientExecutor.createWithDefaults("test");
        operation = new RemoteScannerProperties.HttpOperation();
        operation.setMethod("GET");
        operation.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void lenientExecute_returnsBodyOf4xx() throws Exception {
        assertEquals("{\"status\":\"error\"}", executor.execute(operation, null));
    }

    @Test
    void strictExecute_throwsOn4xxWithBody() {
        assertThrows(ScannerException.class, () -> executor.execute(operation, null, true));
    }

    @Test
    void strictExecute_throwsOn3xxWithBody() {
        operation.setUrl(operation.getUrl() + "redirect");

        assertThrows(ScannerException.class, () -> executor.execute(operation, null, true));
    }
}
