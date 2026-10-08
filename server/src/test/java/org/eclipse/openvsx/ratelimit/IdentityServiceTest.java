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
package org.eclipse.openvsx.ratelimit;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.mock.web.MockHttpServletRequest;

import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdentityServiceTest {

    TierService tiers = mock(TierService.class);
    CustomerService customers = mock(CustomerService.class);
    RateLimitProperties properties = new RateLimitProperties();

    IdentityService service = new IdentityService(
            new SpelExpressionParser(),
            new DefaultListableBeanFactory(),
            tiers,
            customers,
            mock(AccessTokenService.class),
            properties,
            Optional.empty());

    Customer acme = new Customer();

    @BeforeEach
    void setUp() {
        acme.setName("acme");
        when(tiers.getFreeTier()).thenReturn(Optional.empty());
        when(tiers.getSafetyTier()).thenReturn(Optional.empty());
        when(customers.getCustomerByName("acme")).thenReturn(Optional.of(acme));
        when(customers.getCustomerByIpAddress(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void simpleRequest() {
        var identity = service.resolveIdentity(request());

        assertThat(identity.cacheKey()).isEqualTo("ip_1.2.3.4");
        assertThat(identity.customer()).isNull();
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/-/search");
        request.setRemoteAddr("1.2.3.4");
        return request;
    }
}