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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the Web UI is served from, and which routes it serves.
 * <p>
 * Read in one place rather than in each class that needs it. {@code ovsx.webui.url} was declared in
 * six, and {@code ovsx.webui.frontendRoutes} in two with their long default spelled out both times -
 * a default that has to agree between them, since one forwards those routes to the Web UI and the
 * other decides they may be served without authentication.
 * <p>
 * Bound rather than read with {@code @Value}, so the defaults live in the fields and the class is
 * usable as it is - in a test, say - rather than only once Spring has injected into it. It is
 * registered by {@link WebConfig}, which every consumer's context already has.
 */
@ConfigurationProperties("ovsx.webui")
public class WebUiProperties {

    /**
     * Base URL of the Web UI, {@code ovsx.webui.url}. Empty where the Web UI is served from the same
     * origin as the API, which is why callers check before building a URL from it.
     */
    String url = "";

    /**
     * The routes the Web UI serves itself, {@code ovsx.webui.frontendRoutes}.
     */
    String[] frontendRoutes = {
        "/extension/**",
        "/namespace/**",
        "/search",
        "/user-settings/**",
        "/publish",
        "/admin-dashboard/**"
    };

    /**
     * Further paths the deployment serves without authentication, {@code ovsx.webui.additional-routes},
     * for a Web UI that adds pages of its own beyond {@link #getFrontendRoutes()}.
     */
    String[] additionalRoutes = {};

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String[] getFrontendRoutes() {
        return frontendRoutes;
    }

    public void setFrontendRoutes(String[] frontendRoutes) {
        this.frontendRoutes = frontendRoutes;
    }

    public String[] getAdditionalRoutes() {
        return additionalRoutes;
    }

    public void setAdditionalRoutes(String[] additionalRoutes) {
        this.additionalRoutes = additionalRoutes;
    }
}
