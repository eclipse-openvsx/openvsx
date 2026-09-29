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
package org.eclipse.openvsx.ratelimit.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringUtils;

import org.eclipse.openvsx.ratelimit.filter.RateLimitServletFilter;
import org.eclipse.openvsx.ratelimit.filter.RateLimitServletFilterFactory;

@Configuration
@ConditionalOnBean(RateLimitConfig.class)
public class RateLimitAutoConfiguration implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final Logger logger = LoggerFactory.getLogger(RateLimitAutoConfiguration.class);

    private final GenericApplicationContext context;
    private final RateLimitProperties properties;
    private final RateLimitServletFilterFactory filterFactory;

    public RateLimitAutoConfiguration(
            GenericApplicationContext context,
            RateLimitProperties properties,
            RateLimitServletFilterFactory filterFactory
    ) {
        this.context = context;
        this.properties = properties;
        this.filterFactory = filterFactory;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        var filterCount = new AtomicInteger(0);
        properties
                .getFilters()
                .forEach(filter -> {
                    setDefaults(filter);
                    filterCount.incrementAndGet();

                    var beanName = ("RateLimitServletRequestFilter" + filterCount);
                    context.registerBean(
                            beanName,
                            RateLimitServletFilter.class,
                            () -> filterFactory.create(filter));

                    logger.info("rate-limit:create-servlet-filter;{};{}", filterCount, filter.getUrl());
                });
    }

    private void setDefaults(RateLimitFilterProperties filterProperties) {
        if (!StringUtils.hasLength(filterProperties.getHttpResponseBody())) {
            filterProperties.setHttpResponseBody(properties.getDefaultHttpResponseBody());
        }
        if (!StringUtils.hasLength(filterProperties.getHttpContentType())) {
            filterProperties.setHttpContentType(properties.getDefaultHttpContentType());
        }
        if (filterProperties.getHttpStatusCode() == null) {
            filterProperties.setHttpStatusCode(properties.getDefaultHttpStatusCode());
        }
    }
}
