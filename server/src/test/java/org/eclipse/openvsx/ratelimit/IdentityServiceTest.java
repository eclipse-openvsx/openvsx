/**
 * Copyright (c) 2026 Eclipse Foundation AISBL
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.openvsx.ratelimit;

import java.util.Optional;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(SpringExtension.class)
@MockitoBean(types = {
    ConfigurableBeanFactory.class,
    AccessTokenService.class
})
public class IdentityServiceTest {

    @MockitoBean
    CustomerService customerService;

    @MockitoBean
    UserService users;

    @MockitoBean
    TierService tierService;

    @Autowired
    IdentityService service;

    @Test
    public void testResolveIdentityAuthenticatedUser() {
        var request = Mockito.mock(HttpServletRequest.class);
        var userData = mockUserData();

        Mockito.when(customerService.getCustomerByIpAddress(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        Mockito.when(tierService.getFreeTier()).thenReturn(Optional.empty());
        Mockito.when(tierService.getSafetyTier()).thenReturn(Optional.empty());

        var resolvedIdentity = service.resolveIdentity(request);

        assertTrue(resolvedIdentity.cacheKey().startsWith("user_"), "Cache key should start with 'user_'");
        assertEquals("user_" + userData.getAuthId(), resolvedIdentity.cacheKey(), "Cache key should be based on user auth ID");
    }

    private UserData mockUserData() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        userData.setFullName("Test User");
        userData.setAuthId("test_auth_id");
        userData.setProviderUrl("http://example.com/test");
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public IdentityService identityService(
                ExpressionParser expressionParser,
                ConfigurableBeanFactory beanFactory,
                TierService tierService,
                CustomerService customerService,
                AccessTokenService tokenService,
                RateLimitProperties rateLimitProperties,
                UserService userService
        ) {
            return new IdentityService(expressionParser, beanFactory, tierService, customerService, tokenService, rateLimitProperties, userService);
        }

        @Bean
        public ExpressionParser expressionParser() {
            return new SpelExpressionParser();
        }

        @Bean
        public RateLimitProperties rateLimitProperties() {
            return new RateLimitProperties();
        }
    }
}
