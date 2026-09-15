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

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.UUIDService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(SpringExtension.class)
public class CustomerServiceTest {
    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    UUIDService uuidService;

    @MockitoBean
    RateLimitProperties rateLimitProperties;

    @Autowired
    CustomerService service;

    @Test
    public void testGetCustomerByIpAddress() {
        var customer = mockCustomer();

        Mockito.when(repositories.findAllCustomers()).thenReturn(List.of(customer));

        service.refreshCache(null);

        assertSame(customer, service.getCustomerByIpAddress("1.1.1.1").orElse(null));
        assertSame(customer, service.getCustomerByIpAddress("1.1.1.10").orElse(null));
        assertNull(service.getCustomerByIpAddress("2.2.2.2").orElse(null));
    }

    @Test
    public void testGetCustomerByIpAddress_malformedAddressReturnsEmptyInsteadOfThrowing() {
        Mockito.when(repositories.findAllCustomers()).thenReturn(List.of());

        service.refreshCache(null);

        assertNull(service.getCustomerByIpAddress("unknown").orElse(null));
        assertNull(service.getCustomerByIpAddress("").orElse(null));
    }

    @Test
    public void testRefreshCache_skipsCustomerWithMalformedCidrBlockInsteadOfThrowing() {
        var malformed = new Customer();
        malformed.setName("malformed");
        malformed.setCidrBlocks(List.of("not-a-cidr-block"));

        var valid = mockCustomer();

        Mockito.when(repositories.findAllCustomers()).thenReturn(List.of(malformed, valid));

        service.refreshCache(null);

        assertSame(valid, service.getCustomerByIpAddress("1.1.1.1").orElse(null));
    }

    private Customer mockCustomer() {
        var c = new Customer();
        c.setName("test");
        c.setCidrBlocks(List.of("1.1.1.0/24"));
        return c;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public CustomerService customerService(
                EntityManager entityManager,
                RepositoryService repositoryService,
                UUIDService uuidService,
                RateLimitProperties rateLimitProperties
        ) {
            return new CustomerService(entityManager, repositoryService, uuidService, rateLimitProperties);
        }
    }
}
