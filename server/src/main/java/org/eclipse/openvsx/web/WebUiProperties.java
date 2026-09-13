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
package org.eclipse.openvsx.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The web UI's own URL and the frontend routes it serves, previously declared independently
 * (and identically) in both {@link WebConfig} and {@code SecurityConfig}.
 */
@Component
public class WebUiProperties {

    @Value("${ovsx.webui.url:}")
    String webuiUrl = "";

    @Value("${ovsx.api.url:${ovsx.webui.url:}}")
    String apiUrl = "";

    @Value(
        "${ovsx.webui.frontendRoutes:/extension/**,/namespace/**,/search,/user-settings/**,/publish,/admin-dashboard/**}"
    )
    String[] frontendRoutes = {
        "/extension/**",
        "/namespace/**",
        "/search",
        "/user-settings/**",
        "/publish",
        "/admin-dashboard/**"
    };

    /**
     * The origins allowed to read the public, unauthenticated surface - the registry API, the VS Code
     * gallery adapter and the static documents - from a browser. Comma separated; {@code *} for any.
     * <p>
     * Any by default, because that surface exists to be consumed: a public registry is read by clients
     * that are not its own Web UI, and the ones running in a browser (VS Code for the Web, Gitpod, Theia,
     * anything querying the gallery from page script) get nothing back without these headers. Clients
     * that are not browsers - VS Code desktop fetches the gallery from its node extension host - never
     * consult them and are unaffected by whatever this says.
     * <p>
     * Worth narrowing, or emptying, on a registry that is not meant to be read from the open web. The
     * endpoints are unauthenticated, so this is not what keeps their contents private - anything that can
     * reach them can read them - but on an instance reachable only from an internal network it does stop
     * a page in an employee's browser being used to reach it from outside. Set to an empty value to
     * register no public CORS mappings at all.
     * <p>
     * Deliberately not derived from {@code ovsx.webui.url}: where the Web UI is served from has nothing
     * to do with which third parties may read the API, and tying the two together left a registry serving
     * its UI from the same origin as its API emitting no CORS headers for the public surface at all.
     * <p>
     * Property: {@code ovsx.cors.public-origins}
     * Default: {@code *}
     */
    @Value("${ovsx.cors.public-origins:*}")
    String[] publicCorsOrigins;

    public String getWebuiUrl() {
        return webuiUrl;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String[] getFrontendRoutes() {
        return frontendRoutes;
    }

    public String[] getPublicCorsOrigins() {
        return publicCorsOrigins;
    }
}
