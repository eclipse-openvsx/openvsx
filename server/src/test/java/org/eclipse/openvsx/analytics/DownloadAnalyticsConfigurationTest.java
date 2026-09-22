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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;

import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The settling margin is bound with @Value rather than read from the Environment so that the
 * configuration reference check can see it. These cover the validation that binding brought with it,
 * and the cache manager wiring: which backend the settled cache uses, and that a zero ttl skips it.
 */
class DownloadAnalyticsConfigurationTest {

    private static final DownloadSeriesRequest REQUEST = DownloadSeriesRequest.of(
            1L,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-03T00:00:00Z"),
            DownloadSeriesInterval.DAY);
    private static final List<DownloadSeriesRow> ROWS = List.of(
            new DownloadSeriesRow(Instant.parse("2026-07-01T00:00:00Z"), "US", 3),
            new DownloadSeriesRow(Instant.parse("2026-07-02T00:00:00Z"), null, 0));

    @Test
    void acceptsTheDefaults() {
        assertThatCode(config(Duration.ofHours(2), Duration.ofHours(1))::validateConfiguration)
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsZeroForBoth() {
        // Zero is a deliberate setting in each case, not a missing one: no backing off from the
        // current bucket boundary, and no caching of settled ranges.
        assertThatCode(config(Duration.ZERO, Duration.ZERO)::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void rejectsANegativeMargin() {
        // It would put the settled boundary in the future, so the whole series - including buckets
        // that have not happened - would come from the settled cache.
        assertThatThrownBy(config(Duration.ofHours(-1), Duration.ofHours(1))::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.settling-margin");
    }

    @Test
    void rejectsANegativeCacheTtl() {
        assertThatThrownBy(config(Duration.ofHours(2), Duration.ofHours(-1))::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.settled-cache.ttl");
    }

    @Test
    void caffeineManagerRegistersAndRoundTripsTheSeriesCache() {
        var manager = config(Duration.ofHours(2), Duration.ofHours(1)).downloadAnalyticsCaffeineCacheManager();

        var cache = manager.getCache(DownloadAnalyticsService.CACHE_SERIES);

        assertThat(cache).isNotNull();
        cache.put(REQUEST, ROWS);
        assertThat(cache.get(REQUEST, List.class)).isEqualTo(ROWS);
    }

    @Test
    void redisManagerRegistersAndRoundTripsTheSeriesCache() {
        var stored = new HashMap<String, byte[]>();
        var connection = mock(RedisConnection.class, Mockito.RETURNS_DEEP_STUBS);
        var factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.stringCommands().get(any()))
                .thenAnswer(invocation -> stored.get(new String((byte[]) invocation.getArgument(0))));
        when(connection.stringCommands().set(any(), any(), any(Expiration.class), any(SetOption.class)))
                .thenAnswer(invocation -> {
                    stored.put(new String((byte[]) invocation.getArgument(0)), invocation.getArgument(1));
                    return true;
                });

        var manager = (RedisCacheManager) config(Duration.ofHours(2), Duration.ofHours(1))
                .downloadAnalyticsRedisCacheManager(factory);
        manager.afterPropertiesSet();
        var cache = manager.getCache(DownloadAnalyticsService.CACHE_SERIES);

        assertThat(cache).isNotNull();
        cache.put(REQUEST, ROWS);
        assertThat(cache.get(REQUEST, List.class)).isEqualTo(ROWS);
    }

    @Test
    void serviceSkipsTheCacheWhenTtlIsZero() {
        var cacheManager = mock(CacheManager.class);

        config(Duration.ofHours(2), Duration.ZERO)
                .downloadAnalyticsService(mock(DownloadAnalyticsRepository.class), cacheManager);

        verify(cacheManager, never()).getCache(any());
    }

    @Test
    void serviceUsesTheNamedCacheWhenTtlIsPositive() {
        var cacheManager = mock(CacheManager.class);

        config(Duration.ofHours(2), Duration.ofHours(1))
                .downloadAnalyticsService(mock(DownloadAnalyticsRepository.class), cacheManager);

        verify(cacheManager).getCache(DownloadAnalyticsService.CACHE_SERIES);
    }

    private DownloadAnalyticsConfiguration config(Duration settlingMargin, Duration settledCacheTtl) {
        var config = new DownloadAnalyticsConfiguration();
        config.settlingMargin = settlingMargin;
        config.settledCacheTtl = settledCacheTtl;
        return config;
    }
}
