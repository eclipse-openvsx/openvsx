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
package org.eclipse.openvsx.ratelimit.edge;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.ratelimit.config.EdgeProperties;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;

/**
 * The target of the Fastly real-time logging endpoint that reports the requests the edge
 * attributed to a customer.
 */
@Hidden
@RestController
@ConditionalOnBean(RateLimitConfig.class)
@ConditionalOnProperty(prefix = RateLimitProperties.PROPERTY_PREFIX, name = "edge.enabled", havingValue = "true")
public class EdgeUsageAPI {

    public static final String PATH = "/internal/edge/usage";

    private final EdgeProperties properties;
    private final EdgeUsageService usage;

    public EdgeUsageAPI(RateLimitProperties rateLimitProperties, EdgeUsageService usage) {
        this.properties = rateLimitProperties.getEdge();
        this.usage = usage;
    }

    @PostConstruct
    void initialize() {
        if (properties.getSharedSecret().isBlank()) {
            throw new IllegalStateException(
                    "ovsx.rate-limit.edge.shared-secret must be set when ovsx.rate-limit.edge.enabled is true");
        }
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> ingest(
            @RequestHeader(value = EdgeProperties.HEADER_SECRET, required = false)
            @Nullable String secret,
            @RequestBody(required = false)
            @Nullable String batch
    ) {
        if (!properties.isTrusted(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (batch != null) {
            usage.ingest(batch);
        }
        return ResponseEntity.noContent().build();
    }
}