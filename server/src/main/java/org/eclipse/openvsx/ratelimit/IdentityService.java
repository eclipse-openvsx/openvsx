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

import com.giffing.bucket4j.spring.boot.starter.context.ExpressionParams;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnBean(RateLimitConfig.class)
public class IdentityService {
    private final Logger logger = LoggerFactory.getLogger(IdentityService.class);
    private final static String HEADER_RATE_LIMIT_TOKEN = "X-RateLimit-Token";

    private final ExpressionParser expressionParser;
    private final ConfigurableBeanFactory beanFactory;

    private final TierService tierService;
    private final CustomerService customerService;
    private final AccessTokenService tokenService;
    private final RateLimitProperties rateLimitProperties;

    public IdentityService(
            ExpressionParser expressionParser,
            ConfigurableBeanFactory beanFactory,
            TierService tierService,
            CustomerService customerService,
            AccessTokenService tokenService,
            RateLimitProperties rateLimitProperties
    ) {
        this.expressionParser = expressionParser;
        this.beanFactory = beanFactory;
        this.tierService = tierService;
        this.customerService = customerService;
        this.tokenService = tokenService;
        this.rateLimitProperties = rateLimitProperties;
    }

    public ResolvedIdentity resolveIdentity(HttpServletRequest request) {
        String ipAddress = getIPAddress(request);
        String cacheKey = null;

        Optional<Customer> customer = Optional.empty();

        var rateLimitToken = request.getHeader(HEADER_RATE_LIMIT_TOKEN);
        if (rateLimitToken != null) {
            var customerId = customerService.getCustomerIdByRateLimitToken(rateLimitToken);
            if (customerId.isPresent()) {
                customer = customerService.getCustomerById(customerId.get());
                if (customer.isPresent()) {
                    cacheKey = "customer_" + customer.get().getName();
                }
            }
        }

        if (cacheKey == null) {
            var token = request.getParameter("token");
            if (token != null) {
                // This will update the database with the time the token is last accessed,
                // but we need to ensure that we only take valid tokens into account for rate limiting.
                // If this turns out to be a bottleneck, we need to cache the token hashcode.
                var tokenEntity = tokenService.useAccessToken(token);
                if (tokenEntity != null) {
                    // if a valid token is present we use it as a cache key
                    cacheKey = "token_" + tokenEntity.getUser().getId();
                }
            }
        }

        if (customer.isEmpty()) {
            customer = customerService.getCustomerByIpAddress(ipAddress);
            if (customer.isPresent() && cacheKey == null) {
                cacheKey = "customer_" + customer.get().getName();
            }
        }

        if (cacheKey == null) {
            var session = request.getSession(false);
            var sessionId = session != null ? session.getId() : null;
            if (sessionId != null) {
                // we use the session id as a cache key
                cacheKey = "session_" + sessionId.hashCode();
            }
        }

        if (cacheKey == null) {
            cacheKey = "ip_" + ipAddress;
        }

        return new ResolvedIdentity(
                ipAddress,
                cacheKey,
                customer.orElse(null),
                tierService.getFreeTier().orElse(null),
                tierService.getSafetyTier().orElse(null)
        );
    }

    private String getIPAddress(HttpServletRequest request) {
        var ipAddressFunction = rateLimitProperties.getIpAddressFunction();
        var params = new ExpressionParams<>(request);
        var context = getContext(params.getParams());
        var expr = expressionParser.parseExpression(ipAddressFunction);
        String result = expr.getValue(context, params.getRootObject(), String.class);
        logger.trace("GetIPAddress -> result:{};expression:{}", result, ipAddressFunction);
        return result;
    }

    private StandardEvaluationContext getContext(Map<String, Object> params) {
        var context = new StandardEvaluationContext();
        params.forEach(context::setVariable);
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        return context;
    }
}
