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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LoginReturnToTest {

    private static final String[] FRONTEND_ROUTES = {
        "/extension/**",
        "/search",
        "/user-settings/**"
    };

    private final LoginReturnTo returnTo = new LoginReturnTo(FRONTEND_ROUTES, new String[] { "/custom-page" });

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/search",
            "/extension/foo/bar",
            "/extension/foo/bar/reviews",
            "/search?query=java&sortBy=relevance",
            "/extension/foo/bar/reviews#reviews",
            "/user-settings/profile",
            "/custom-page"
        }
    )
    void acceptsAPathOfTheWebUi(String target) {
        assertThat(returnTo.sanitize(target)).isEqualTo(target);
    }

    // an absolute URL, the protocol-relative forms a browser reads as one (backslashes included),
    // a path relative to wherever the callback lands, and paths that are not a Web UI route
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
            "https://evil.example.com",
            "//evil.example.com",
            "/\\evil.example.com",
            "\\\\evil.example.com",
            "/extension/foo/bar\\@evil.example.com",
            "extension/foo/bar",
            "/search\r\nSet-Cookie: x=y",
            "/extension/../../admin-dashboard",
            "/extension/%2e%2e/admin",
            "/admin-dashboard/extensions",
            "/api/user",
            "/"
        }
    )
    void rejectsAnythingElse(String target) {
        assertThat(returnTo.sanitize(target)).isNull();
    }

    @Test
    void rejectsAnOverlongTarget() {
        assertThat(returnTo.sanitize("/extension/foo/" + "a".repeat(512))).isNull();
    }

    @Test
    void remembersTheTargetForTheLoginThatFollows() {
        var request = new MockHttpServletRequest();
        request.setParameter(LoginReturnTo.PARAMETER, "/extension/foo/bar/reviews");

        returnTo.capture(request);

        assertThat(LoginReturnTo.take(request)).isEqualTo("/extension/foo/bar/reviews");
    }

    @Test
    void forgetsTheTargetOnceItIsTaken() {
        var request = new MockHttpServletRequest();
        request.setParameter(LoginReturnTo.PARAMETER, "/search");
        returnTo.capture(request);

        LoginReturnTo.take(request);

        assertThat(LoginReturnTo.take(request)).isNull();
    }

    @Test
    void forgetsATargetLeftBehindByAnAbandonedLogin() {
        var request = new MockHttpServletRequest();
        request.setParameter(LoginReturnTo.PARAMETER, "/search");
        returnTo.capture(request);

        var second = new MockHttpServletRequest();
        second.setSession(request.getSession());
        returnTo.capture(second);

        assertThat(LoginReturnTo.take(second)).isNull();
    }

    @Test
    void startsNoSessionForALoginThatCarriesNoTarget() {
        var request = new MockHttpServletRequest();

        returnTo.capture(request);

        assertThat(request.getSession(false)).isNull();
    }
}
