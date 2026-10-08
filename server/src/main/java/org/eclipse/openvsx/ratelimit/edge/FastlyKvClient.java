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

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.eclipse.openvsx.ratelimit.config.EdgeProperties;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;

/**
 * Writes to a Fastly KV Store through the Fastly API.
 *
 * @see <a href="https://www.fastly.com/documentation/reference/api/services/resources/kv-store-item/">KV store item API</a>
 */
@Component
@ConditionalOnBean(RateLimitConfig.class)
@ConditionalOnProperty(prefix = RateLimitProperties.PROPERTY_PREFIX, name = "edge.enabled", havingValue = "true")
public class FastlyKvClient implements EdgeKvClient {

    private static final String KEY_PATH = "/resources/stores/kv/{store}/keys/{key}";

    private final EdgeProperties properties;
    private RestClient restClient;

    public FastlyKvClient(RateLimitProperties rateLimitProperties) {
        this.properties = rateLimitProperties.getEdge();
    }

    @PostConstruct
    void initialize() {
        if (properties.getFastlyApiToken().isBlank() || properties.getKvStoreId().isBlank()) {
            throw new IllegalStateException(
                    "ovsx.rate-limit.edge.fastly-api-token and ovsx.rate-limit.edge.kv-store-id must be set"
                            + " when ovsx.rate-limit.edge.enabled is true");
        }

        restClient = RestClient.builder()
                .baseUrl(properties.getFastlyApiUrl())
                .defaultHeader("Fastly-Key", properties.getFastlyApiToken())
                .build();
    }

    @Override
    public void put(String key, String value) {
        restClient.put()
                .uri(KEY_PATH, properties.getKvStoreId(), key)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(value)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void delete(String key) {
        restClient.delete()
                .uri(KEY_PATH, properties.getKvStoreId(), key)
                .retrieve()
                .toBodilessEntity();
    }
}