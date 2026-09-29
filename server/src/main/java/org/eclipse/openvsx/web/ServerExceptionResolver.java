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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.util.DisconnectedClientHelper;

@RestControllerAdvice
public class ServerExceptionResolver extends DefaultHandlerExceptionResolver {

    public ServerExceptionResolver() {
        super();
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected void logException(@NonNull Exception ex, @NonNull HttpServletRequest request) {
        // do not log HttpMediaTypeNotSupportedException, see https://github.com/eclipse/openvsx/issues/1505
        // this just pollutes the server logs but bringing no added value
        if (!(ex instanceof HttpMediaTypeNotSupportedException)) {
            super.logException(ex, request);
        }
    }

    @Override
    protected ModelAndView handleHttpMessageNotWritable(
            @NonNull HttpMessageNotWritableException ex,
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            Object handler
    ) throws IOException {
        if (!response.isCommitted()) {
            return super.handleHttpMessageNotWritable(ex, request, response, handler);
        }

        // The response is already committed, so - same as the superclass - there is nothing
        // left to send back. Most of these are simply a client that disconnected mid-response
        // (closed browser tab, VS Code request timeout, ...), surfaced as a broken-pipe/
        // connection-reset IOException while writing the JSON body. Jackson 3 wraps that
        // IOException into its own (unchecked) exception hierarchy, unlike Jackson 2, so
        // Spring's message converter now wraps it into HttpMessageNotWritableException too -
        // which this resolver recognizes and, same as the superclass, logs at WARN. That means
        // the exact same, ordinary client-disconnect traffic this app always had now produces
        // a WARN per occurrence where it used to be silent. Downgrade the recognized cases to
        // DEBUG; anything else stays at WARN, since it may be a genuine bug.
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring exception, client disconnected: " + ex);
            }
        } else if (logger.isWarnEnabled()) {
            logger.warn("Ignoring exception, response committed already: " + ex);
        }
        return new ModelAndView();
    }
}
