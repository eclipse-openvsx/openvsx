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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.util.Streamable;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.RateLimitToken;
import org.eclipse.openvsx.ratelimit.cache.ConfigurationChanged;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EdgeConfigServiceTest {

    RepositoryService repositories = mock(RepositoryService.class);
    EdgeStateService state = mock(EdgeStateService.class);

    @Test
    void config_listsCidrsAndHashedTokensPerCustomer() {
        var acme = customer("acme", List.of("1.1.1.0/24"));
        var token = new RateLimitToken();
        token.setValue("secret-token");
        when(repositories.findAllCustomers()).thenReturn(List.of(acme));
        when(repositories.findActiveRateLimitTokens(acme)).thenReturn(Streamable.of(token));

        var config = JsonMapper.builder().build().readTree(new EdgeConfigService(repositories, state).buildConfig());

        var customer = config.path("customers").get(0);
        assertThat(customer.path("name").asString()).isEqualTo("acme");
        assertThat(customer.path("cidrs").get(0).asString()).isEqualTo("1.1.1.0/24");
        // sha256("secret-token"); the edge hashes the X-RateLimit-Token header the same way
        assertThat(customer.path("tokenHashes").get(0).asString())
                .isEqualTo(EdgeConfigService.sha256Hex("secret-token"))
                .hasSize(64)
                .doesNotContain("secret-token");
        assertThat(config.path("version").asLong()).isPositive();
    }

    @Test
    void configurationChanged_republishes() {
        when(repositories.findAllCustomers()).thenReturn(List.of());
        try (var context = new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            context.registerBean(EdgeConfigService.class, () -> new EdgeConfigService(repositories, state));
            context.refresh();

            context.publishEvent(new ConfigurationChanged());
        }

        var config = ArgumentCaptor.forClass(String.class);
        verify(state).publishConfig(config.capture());
        assertThat(config.getValue()).contains("\"customers\":[]");
    }

    private static Customer customer(String name, List<String> cidrs) {
        var customer = new Customer();
        customer.setName(name);
        customer.setCidrBlocks(cidrs);
        return customer;
    }
}