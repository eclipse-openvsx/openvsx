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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ratelimit.IdentityService;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitFilterProperties;

@Component
@ConditionalOnBean(RateLimitConfig.class)
public class RateLimitServletFilterFactory {

    private final UsageStatsService usageStatsService;
    private final IdentityService identityService;
    private final RateLimitService rateLimitService;

    public RateLimitServletFilterFactory(
            UsageStatsService usageStatsService,
            IdentityService identityService,
            RateLimitService rateLimitService
    ) {
        this.usageStatsService = usageStatsService;
        this.identityService = identityService;
        this.rateLimitService = rateLimitService;
    }

    public RateLimitServletFilter create(RateLimitFilterProperties filterProperties) {
        return new RateLimitServletFilter(filterProperties, usageStatsService, identityService, rateLimitService);
    }
}
