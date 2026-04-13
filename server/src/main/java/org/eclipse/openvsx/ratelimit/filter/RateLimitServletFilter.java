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
package org.eclipse.openvsx.ratelimit.filter;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.ratelimit.IdentityService;
import org.eclipse.openvsx.ratelimit.config.RateLimitFilterProperties;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RateLimitServletFilter extends OncePerRequestFilter implements Ordered {

    private final Logger logger = LoggerFactory.getLogger(RateLimitServletFilter.class);

    private final static String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private final static String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private final static String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private final RateLimitFilterProperties filterProperties;
    private final UsageStatsService usageStatsService;
    private final IdentityService identityService;
    private final RateLimitService rateLimitService;

    public RateLimitServletFilter(
        RateLimitFilterProperties filterProperties,
        UsageStatsService usageStatsService,
        IdentityService identityService,
        RateLimitService rateLimitService
    ) {
        this.filterProperties = filterProperties;
        this.usageStatsService = usageStatsService;
        this.identityService = identityService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().matches(filterProperties.getUrl());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        var identity = identityService.resolveIdentity(request);
        logger.debug("Rate limit filter: {}: {}", request.getRequestURI(), identity.ipAddress());

        if (identity.isCustomer()) {
            var customer = identity.getCustomer();
            logger.debug("Increasing usage stats for customer {}", customer.getName());
            usageStatsService.incrementUsage(customer);
        }

        var bucketPair = rateLimitService.getBucket(identity);
        var bucket = bucketPair.bucket();
        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader(HEADER_RATE_LIMIT_LIMIT, Long.toString(bucketPair.availableTokens()));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        logger.debug("Remaining tokens for {}: {}", identity.cacheKey(), probe.getRemainingTokens());
        if (probe.isConsumed()) {
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            handleHttpResponseOnRateLimiting(response, probe);
        }
    }

    private void handleHttpResponseOnRateLimiting(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        response.setStatus(filterProperties.getHttpStatusCode().value());

        response.setHeader(HEADER_RATE_LIMIT_REMAINING, "0");
        response.setHeader(HEADER_RATE_LIMIT_RESET, "" + TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForReset()));
        var refillInSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        response.setHeader("Retry-After", Long.toString(refillInSeconds));

        filterProperties.getHttpResponseHeaders().forEach(response::setHeader);
        if (filterProperties.getHttpResponseBody() != null) {
            response.setContentType(filterProperties.getHttpContentType());
            response.getWriter().append(filterProperties.getHttpResponseBody());
        }
    }

    @Override
    public int getOrder() {
        return filterProperties.getFilterOrder();
    }
}
