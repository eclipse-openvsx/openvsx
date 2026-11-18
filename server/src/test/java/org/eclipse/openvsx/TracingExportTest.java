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
package org.eclipse.openvsx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration;
import org.springframework.boot.micrometer.tracing.brave.autoconfigure.zipkin.ZipkinWithBraveTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production exports traces to Zipkin through Boot's auto-configuration alone, so a dependency change
 * that disables any link of that chain silently stops tracing. This sends a span through the real chain
 * to a stand-in Zipkin endpoint.
 */
class TracingExportTest {

    private HttpServer zipkin;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startZipkin() throws IOException {
        zipkin = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        zipkin.createContext("/api/v2/spans", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        zipkin.start();
    }

    @AfterEach
    void stopZipkin() {
        zipkin.stop(0);
    }

    @Test
    void exportsSpansToZipkin() {
        var traceId = new String[1];
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                ObservationAutoConfiguration.class,
                                MicrometerTracingAutoConfiguration.class,
                                BraveAutoConfiguration.class,
                                ZipkinAutoConfiguration.class,
                                ZipkinWithBraveTracingAutoConfiguration.class))
                .withPropertyValues(
                        "management.tracing.sampling.probability=1.0",
                        "management.tracing.export.zipkin.endpoint=http://localhost:"
                                + zipkin.getAddress().getPort() + "/api/v2/spans")
                .run(context -> {
                    var span = context.getBean(Tracer.class).nextSpan().name("tracing-export-test").start();
                    traceId[0] = span.context().traceId();
                    span.end();
                });

        // closing the context flushes the asynchronous reporter
        assertThat(received).isNotEmpty();
        assertThat(String.join("", received)).contains("tracing-export-test").contains(traceId[0]);
    }
}
