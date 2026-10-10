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
package org.eclipse.openvsx.settings;

import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.json.SettingsJson;

/**
 * Public read access to the settings the web UI needs before login. Writing them stays in
 * {@code AdminAPI} behind the admin role. Under the reserved {@code -} namespace, like the other
 * registry-level endpoints, so the path can never shadow a namespace.
 */
@RestController
public class SettingsAPI {

    private final SettingsService settings;

    public SettingsAPI(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping(path = "/api/-/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(summary = "Return the registry's public site settings, with only the settings meant for visitors")
    @ApiResponse(
        responseCode = "200",
        description = "The site settings are returned in JSON format. `bannerMessage` is Markdown an admin wrote; "
                + "sanitize it before rendering it as HTML."
    )
    public ResponseEntity<SettingsJson> getSiteSettings() {
        // Short-lived: an admin changing a setting should reach visitors within the minute.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES).cachePublic().mustRevalidate())
                .body(settings.getSiteSettings());
    }
}
