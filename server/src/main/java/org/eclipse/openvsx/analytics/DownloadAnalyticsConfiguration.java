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
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.cache.CacheConfig;
import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

import static org.eclipse.openvsx.analytics.DownloadAnalyticsService.CACHE_MANAGER;
import static org.eclipse.openvsx.analytics.DownloadAnalyticsService.CACHE_SERIES;

/**
 * Wires download analytics when {@code ovsx.analytics.enabled=true}. The download_event schema
 * lives in its own database, migrated and pooled separately from the registry, so the registry
 * database image needs nothing beyond plain PostgreSQL.
 */
@Configuration
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
class DownloadAnalyticsConfiguration {

    private final Logger logger = LoggerFactory.getLogger(DownloadAnalyticsConfiguration.class);

    @Value("${ovsx.caching.statistics.enabled:false}")
    boolean statisticsEnabled;

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
    @Qualifier(CACHE_MANAGER)
    @ConditionalOnProperty(value = "ovsx.redis.enabled", havingValue = "false", matchIfMissing = true)
    CacheManager downloadAnalyticsCaffeineCacheManager() {
        logger.info("Configure download analytics cache manager (caffeine)");
        var builder = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(settledCacheTtl)
                .scheduler(Scheduler.systemScheduler());
        if (statisticsEnabled) {
            builder.recordStats();
        }

        var caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.registerCustomCache(CACHE_SERIES, builder.build());
        return caffeineCacheManager;
    }

    @Bean
    @Qualifier(CACHE_MANAGER)
    @ConditionalOnProperty(value = "ovsx.redis.enabled", havingValue = "true")
    CacheManager downloadAnalyticsRedisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        logger.info("Configure download analytics cache manager (redis)");
        var mapper = JsonMapper.shared();
        var valueType = mapper.getTypeFactory().constructParametricType(List.class, DownloadSeriesRow.class);
        var serializer = new JacksonJsonRedisSerializer<>(mapper, valueType);
        var builder = RedisCacheManager.builder(CacheConfig.redisCacheWriter(redisConnectionFactory))
                .withCacheConfiguration(CACHE_SERIES, CacheConfig.redisCacheConfig(serializer, settledCacheTtl));
        if (statisticsEnabled) {
            builder.enableStatistics();
        }

        return builder.build();
    }

    @Bean
    DownloadAnalyticsService downloadAnalyticsService(
            DownloadAnalyticsRepository repository,
            @Qualifier(CACHE_MANAGER) CacheManager cacheManager
    ) {
        var cache = settledCacheTtl.isZero() ? null : cacheManager.getCache(CACHE_SERIES);
        return new DownloadAnalyticsService(repository, settlingMargin, cache, Clock.systemUTC());
    }
}
