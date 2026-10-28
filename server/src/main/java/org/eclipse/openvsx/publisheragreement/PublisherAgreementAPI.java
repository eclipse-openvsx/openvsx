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
package org.eclipse.openvsx.publisheragreement;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the agreement text from {@code ovsx.publisher-agreement.text}, any Spring resource location
 * (e.g. {@code classpath:agreement.md}, {@code file:/etc/agreement.md}). The web UI shows
 * it from there by default.
 */
@RestController
public class PublisherAgreementAPI {

    private final ResourceLoader resourceLoader;
    private final String location;

    public PublisherAgreementAPI(
            ResourceLoader resourceLoader,
            @Value("${ovsx.publisher-agreement.text:}") String location
    ) {
        this.resourceLoader = resourceLoader;
        this.location = location;
    }

    // No `produces` condition: it would answer 406 to a client accepting only text/plain, as the webui does.
    @GetMapping("/api/publisher-agreement")
    public ResponseEntity<Resource> getText() {
        if (location.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        var text = resourceLoader.getResource(location);
        if (!text.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic().mustRevalidate())
                .body(text);
    }
}
