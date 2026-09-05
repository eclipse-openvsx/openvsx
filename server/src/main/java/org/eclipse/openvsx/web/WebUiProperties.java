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
    private String url;

    @Value(
        "${ovsx.webui.frontendRoutes:/extension/**,/namespace/**,/search,/user-settings/**,/publish,/admin-dashboard/**}"
    )
    private String[] frontendRoutes;

    public String getUrl() {
        return url;
    }

    public String[] getFrontendRoutes() {
        return frontendRoutes;
    }
}
