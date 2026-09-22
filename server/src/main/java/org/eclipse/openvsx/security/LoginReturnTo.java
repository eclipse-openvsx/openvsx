/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.security;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Carries the page a visitor logged in from across the OAuth2 dance, so a successful login can
 * return them to it.
 * <p>
 * The target rides along as a {@code redirect} query parameter on the authorization request and is
 * kept in the session, because the only request the success handler sees is the OAuth2 callback,
 * whose URL is pinned by the registered {@code redirect_uri}.
 * <p>
 * Only a path matching one of the Web UI's own routes is accepted, and it is always resolved
 * against {@code ovsx.webui.url} rather than taken as a URL, so this cannot be used to bounce a
 * visitor off the registry to somewhere else.
 */
public class LoginReturnTo {

    static final String SESSION_ATTRIBUTE = "ovsx.login.return-to";
    static final String PARAMETER = "redirect";

    private static final int MAX_LENGTH = 512;

    private final List<PathPattern> routes;

    public LoginReturnTo(String[]... routePatterns) {
        var parser = PathPatternParser.defaultInstance;
        this.routes = Arrays.stream(routePatterns)
                .flatMap(Arrays::stream)
                .filter(StringUtils::isNotBlank)
                .map(parser::parse)
                .toList();
    }

    /**
     * Remembers where to return to after {@code request} has logged in, or forgets a target
     * remembered for an earlier attempt when this request carries none that is usable.
     */
    public void capture(HttpServletRequest request) {
        var target = sanitize(request.getParameter(PARAMETER));
        var session = request.getSession(target != null);
        if (session == null) {
            return;
        }
        if (target != null) {
            session.setAttribute(SESSION_ATTRIBUTE, target);
        } else {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    /**
     * The remembered target, removed from the session as it is read: a login returns to a page
     * once, and an abandoned attempt must not redirect a later one.
     */
    @Nullable
    public static String take(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return null;
        }
        var target = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return target instanceof String path ? path : null;
    }

    /**
     * The path to return to, or {@code null} for anything this must not redirect to. Rejects rather
     * than repairs: a target that does not look exactly like one of our own routes is not one.
     */
    @Nullable
    String sanitize(@Nullable String target) {
        if (StringUtils.isEmpty(target) || target.length() > MAX_LENGTH) {
            return null;
        }
        // a relative path of our own, so: no scheme, no authority - including the protocol-relative
        // "//host" and the backslash variants browsers normalise into it - and no control character
        if (!target.startsWith("/") || target.startsWith("//") || target.contains("\\")) {
            return null;
        }
        if (target.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return null;
        }

        var path = StringUtils.substringBefore(StringUtils.substringBefore(target, "#"), "?");
        if (path.contains("..") || path.contains("%2e") || path.contains("%2E")) {
            return null;
        }

        PathContainer pathContainer;
        try {
            pathContainer = PathContainer.parsePath(path);
        } catch (IllegalArgumentException exc) {
            // a malformed escape, e.g. a trailing "%": unparseable is unusable, and must not fail the login
            return null;
        }
        return routes.stream().anyMatch(route -> route.matches(pathContainer)) ? target : null;
    }
}
