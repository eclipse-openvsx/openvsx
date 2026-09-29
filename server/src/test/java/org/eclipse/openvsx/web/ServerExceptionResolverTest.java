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
package org.eclipse.openvsx.web;

import java.io.IOException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotWritableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression: Jackson 3 (Spring Boot 4) wraps a raw IOException from a broken client
 * connection into its own (unchecked) exception hierarchy while writing a response, unlike
 * Jackson 2. Spring's message converter then wraps that into HttpMessageNotWritableException -
 * which DefaultHandlerExceptionResolver#handleHttpMessageNotWritable logs at WARN when the
 * response is already committed. Under Spring Boot 3 (Jackson 2), the same client-disconnect
 * event surfaced as a plain, unwrapped IOException that this resolver never saw at all, so it
 * went unlogged. The rate of clients disconnecting mid-response hasn't changed - only Jackson 3
 * made that same, ordinary event newly visible as a per-occurrence WARN.
 */
class ServerExceptionResolverTest {

    private final ServerExceptionResolver resolver = new ServerExceptionResolver();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    private final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

    private Logger logbackLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        logbackLogger = (Logger) LoggerFactory.getLogger(ServerExceptionResolver.class);
        originalLevel = logbackLogger.getLevel();
        logbackLogger.setLevel(Level.DEBUG);

        logAppender = new ListAppender<>();
        logAppender.start();
        logbackLogger.addAppender(logAppender);

        when(response.isCommitted()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(logAppender);
        logbackLogger.setLevel(originalLevel);
    }

    @Test
    void downgradesAClientDisconnectToDebugInsteadOfWarn() throws IOException {
        var ex = new HttpMessageNotWritableException(
                "Could not write JSON",
                new IOException("Connection reset by peer"));

        var modelAndView = resolver.handleHttpMessageNotWritable(ex, request, response, new Object());

        assertThat(modelAndView.isEmpty()).isTrue();
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.WARN);
        assertThat(logAppender.list)
                .anyMatch(
                        event -> event.getLevel() == Level.DEBUG
                                && event.getFormattedMessage().contains("client disconnected"));
    }

    // Same regression, the other likely-common phrasing/exception type for the same event.
    @Test
    void downgradesABrokenPipeToDebugInsteadOfWarn() throws IOException {
        var ex = new HttpMessageNotWritableException(
                "Could not write JSON",
                new IOException("Broken pipe"));

        resolver.handleHttpMessageNotWritable(ex, request, response, new Object());

        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    // A write failure with no sign of being a disconnected client keeps its original WARN -
    // the point is to stop logging expected, benign traffic loudly, not to hide real bugs.
    @Test
    void keepsWarnForAWriteFailureThatIsNotARecognizedClientDisconnect() throws IOException {
        var ex = new HttpMessageNotWritableException(
                "Could not write JSON",
                new RuntimeException("something genuinely broke"));

        var modelAndView = resolver.handleHttpMessageNotWritable(ex, request, response, new Object());

        assertThat(modelAndView.isEmpty()).isTrue();
        assertThat(logAppender.list)
                .anyMatch(
                        event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains("response committed already"));
    }

    // When the response has NOT been committed yet, the superclass's normal behavior (try to
    // send a proper 500) must still apply unchanged - this override only ever changes the
    // logging for the already-committed case.
    @Test
    void stillDelegatesToTheSuperclassWhenTheResponseIsNotCommittedYet() throws IOException {
        when(response.isCommitted()).thenReturn(false);
        var ex = new HttpMessageNotWritableException(
                "Could not write JSON",
                new IOException("Connection reset by peer"));

        resolver.handleHttpMessageNotWritable(ex, request, response, new Object());

        Mockito.verify(request).setAttribute(Mockito.eq("jakarta.servlet.error.exception"), Mockito.eq(ex));
        Mockito.verify(response).sendError(500);
        assertThat(logAppender.list).isEmpty();
    }
}
