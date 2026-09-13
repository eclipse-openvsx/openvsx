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
package org.eclipse.openvsx.analytics;

import java.time.Clock;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

/**
 * Wires download analytics when {@code ovsx.analytics.enabled=true}. The download_event schema
 * lives in its own database, migrated and pooled separately from the registry, so the registry
 * database image needs nothing beyond plain PostgreSQL.
 */
@Configuration
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
class DownloadAnalyticsConfiguration {

    @Bean
    DownloadAnalyticsRepository downloadAnalyticsRepository(@Qualifier("timeseriesDsl") DSLContext dsl) {
        return new DownloadAnalyticsRepository(dsl);
    }

    /**
     * How far back from now the series is treated as settled. Bound here rather than read from the
     * Environment inside the bean method so that the property is visible to the configuration
     * reference check, which scans for @Value and @ConditionalOnProperty.
     */
    @Value("${ovsx.analytics.settling-margin:PT2H}")
    Duration settlingMargin;

    /**
     * How long a settled range is held before being read again. Nothing invalidates that cache, so a
     * backfill or a manual refresh of the time-series aggregate stays invisible until it expires;
     * zero turns it off, which is what a development setup wants.
     */
    @Value("${ovsx.analytics.settled-cache.ttl:PT1H}")
    Duration settledCacheTtl;

    /**
     * A negative margin would put the settled boundary in the future, so the series would be answered
     * entirely from the settled cache including buckets that have not happened yet. Fail at startup
     * rather than serve that.
     */
    @PostConstruct
    void validateConfiguration() {
        if (settlingMargin.isNegative()) {
            throw new IllegalStateException(
                    "ovsx.analytics.settling-margin must not be negative, but was " + settlingMargin);
        }
        if (settledCacheTtl.isNegative()) {
            throw new IllegalStateException(
                    "ovsx.analytics.settled-cache.ttl must not be negative, but was " + settledCacheTtl);
        }
    }

    @Bean
    DownloadAnalyticsService downloadAnalyticsService(DownloadAnalyticsRepository repository) {
        return new DownloadAnalyticsService(repository, settlingMargin, settledCacheTtl, Clock.systemUTC());
    }
}
