/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code X-Forwarded-Host}, {@code X-Forwarded-Proto} and {@code X-Forwarded-Prefix} before
 * anything reads them, so that the ~40 callers of {@code UrlUtil.getBaseUrl} cannot be handed a host
 * the client chose - those responses are cached under keys that do not include it.
 * <p>
 * {@code ovsx.server.url} replaces the headers outright; otherwise they are read only from a peer in
 * {@code ovsx.server.trusted-proxies}. See doc/configuration.md, Server URL.
 */
public class TrustedForwardedHeaderFilter extends OncePerRequestFilter {

    static final String HOST = "X-Forwarded-Host";
    static final String PROTO = "X-Forwarded-Proto";
    static final String PREFIX = "X-Forwarded-Prefix";

    private static final List<String> FORWARDED_HEADERS = List.of(HOST, PROTO, PREFIX);

    private static final Logger logger = LoggerFactory.getLogger(TrustedForwardedHeaderFilter.class);

    private final ServerUrlConfig config;
    private final AtomicBoolean untrustedLogged = new AtomicBoolean();
    private final AtomicBoolean appendedLogged = new AtomicBoolean();

    public TrustedForwardedHeaderFilter(ServerUrlConfig config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(resolveForwardedHeaders(request), response);
    }

    /**
     * The wrapper has to be in place on an async or error dispatch as well, or a response rendered on one
     * would go back to reading the headers as they arrived.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private HttpServletRequest resolveForwardedHeaders(HttpServletRequest request) {
        // A null value hides the header rather than setting it.
        var resolved = new HashMap<String, @Nullable String>();
        if (config.hasServerUrl()) {
            resolved.put(HOST, config.getHostAndPort());
            resolved.put(PROTO, config.getScheme());
            resolved.put(PREFIX, configuredPrefix(request));
        } else if (config.isTrustedProxy(request.getRemoteAddr())) {
            resolved.put(HOST, lastValue(request, HOST));
            resolved.put(PROTO, lastValue(request, PROTO));
            // Not comma separated: a path may contain a comma, so only whole header lines are candidates.
            resolved.put(PREFIX, lastLine(request, PREFIX));
        } else {
            FORWARDED_HEADERS.forEach(header -> resolved.put(header, null));
            logUntrusted(request);
        }

        return new ForwardedHeaderRequest(request, resolved);
    }

    /**
      * {@code UrlUtil.getBaseUrl} appends the context path after the prefix, so a configured URL that
      * already includes it would otherwise emit it twice.
      */
    private String configuredPrefix(HttpServletRequest request) {
        var prefix = config.getPrefix();
        if (prefix == null) {
            return "";
        }

        var contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && prefix.endsWith(contextPath)) {
            return prefix.substring(0, prefix.length() - contextPath.length());
        }

        return prefix;
    }

    /**
     * The last non-blank comma separated value across every line of {@code header}, or {@code null} when
     * it is absent.
     */
    private @Nullable String lastValue(HttpServletRequest request, String header) {
        String last = null;
        var lines = request.getHeaders(header);
        var appended = false;
        while (lines != null && lines.hasMoreElements()) {
            for (var value : lines.nextElement().split(",")) {
                var trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    appended = last != null;
                    last = trimmed;
                }
            }
        }

        if (appended) {
            logAppended(header);
        }

        return last;
    }

    /**
     * The last non-blank line of {@code header}, or {@code null} when it is absent.
     */
    private @Nullable String lastLine(HttpServletRequest request, String header) {
        String last = null;
        var lines = request.getHeaders(header);
        while (lines != null && lines.hasMoreElements()) {
            var line = lines.nextElement().trim();
            if (!line.isEmpty()) {
                last = line;
            }
        }

        return last;
    }

    private void logUntrusted(HttpServletRequest request) {
        var present = FORWARDED_HEADERS.stream().anyMatch(header -> request.getHeader(header) != null);
        if (present && untrustedLogged.compareAndSet(false, true)) {
            logger.warn(
                    "Ignoring the X-Forwarded-Host/-Proto/-Prefix headers of a request from {}, which is not"
                            + " in ovsx.server.trusted-proxies. If a reverse proxy in front of this server"
                            + " reaches it from that address, set ovsx.server.url to the URL this registry is"
                            + " served at, or add the address to ovsx.server.trusted-proxies. Logged once.",
                    request.getRemoteAddr());
        }
    }

    private void logAppended(String header) {
        if (appendedLogged.compareAndSet(false, true)) {
            logger.warn(
                    "A request carried more than one {} value, so the proxy in front of this server appends"
                            + " to that header rather than overwriting it, and the values before the last one"
                            + " may come from the client. Using the last. Set ovsx.server.url to take the base"
                            + " URL out of the request entirely. Logged once.",
                    header);
        }
    }

    private static class ForwardedHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, @Nullable String> resolved;

        ForwardedHeaderRequest(HttpServletRequest request, Map<String, @Nullable String> resolved) {
            super(request);
            var byLowerCaseName = new HashMap<String, @Nullable String>();
            resolved.forEach((header, value) -> byLowerCaseName.put(header.toLowerCase(Locale.ROOT), value));
            this.resolved = byLowerCaseName;
        }

        @Override
        public @Nullable String getHeader(String name) {
            var key = name.toLowerCase(Locale.ROOT);
            return resolved.containsKey(key) ? resolved.get(key) : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            var key = name.toLowerCase(Locale.ROOT);
            if (!resolved.containsKey(key)) {
                return super.getHeaders(name);
            }

            var value = resolved.get(key);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            var names = new LinkedHashSet<String>();
            var reported = new ArrayList<String>();
            var original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                var name = original.nextElement();
                var key = name.toLowerCase(Locale.ROOT);
                if (!resolved.containsKey(key)) {
                    names.add(name);
                } else if (resolved.get(key) != null) {
                    names.add(name);
                    reported.add(key);
                }
            }

            resolved.forEach((key, value) -> {
                if (value != null && !reported.contains(key)) {
                    names.add(key);
                }
            });

            return Collections.enumeration(names);
        }
    }
}
