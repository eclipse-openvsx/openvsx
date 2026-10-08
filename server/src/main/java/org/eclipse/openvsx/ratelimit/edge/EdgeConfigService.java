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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.RateLimitToken;
import org.eclipse.openvsx.ratelimit.cache.ConfigurationChanged;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;
import org.eclipse.openvsx.repositories.RepositoryService;

/**
 * Publishes what the edge needs to attribute a request to a customer: each customer's CIDR blocks
 * and the hashes of its active rate limit tokens.
 */
@Service
@ConditionalOnBean(RateLimitConfig.class)
@ConditionalOnProperty(prefix = RateLimitProperties.PROPERTY_PREFIX, name = "edge.enabled", havingValue = "true")
public class EdgeConfigService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RepositoryService repositories;
    private final EdgeStateService state;

    public EdgeConfigService(RepositoryService repositories, EdgeStateService state) {
        this.repositories = repositories;
        this.state = state;
    }

    record EdgeConfig(long version, List<EdgeCustomer> customers) {}

    record EdgeCustomer(String name, List<String> cidrs, List<String> tokenHashes) {}

    /**
     * Every node receives {@link ConfigurationChanged} and publishes the same document, which is
     * harmless: the write is idempotent.
     */
    @EventListener({ ApplicationStartedEvent.class, ConfigurationChanged.class })
    public void publish() {
        state.publishConfig(buildConfig());
    }

    String buildConfig() {
        var customers = repositories.findAllCustomers().stream()
                .map(
                        customer -> new EdgeCustomer(
                                customer.getName(),
                                customer.getCidrBlocks(),
                                repositories.findActiveRateLimitTokens(customer).stream()
                                        .map(RateLimitToken::getValue)
                                        // hashed, so a dump of the edge store is not a list of credentials
                                        .map(EdgeConfigService::sha256Hex)
                                        .toList()))
                .toList();
        return MAPPER.writeValueAsString(new EdgeConfig(Instant.now().toEpochMilli(), customers));
    }

    static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}