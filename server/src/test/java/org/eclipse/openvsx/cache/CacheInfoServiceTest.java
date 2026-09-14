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
package org.eclipse.openvsx.cache;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Reads real cache managers rather than mocks, because what this service does is precisely to cope
 * with each implementation exposing its size and statistics somewhere different.
 */
class CacheInfoServiceTest {

    private javax.cache.CacheManager jcacheManager;
    private javax.cache.CacheManager otherJCacheManager;

    @AfterEach
    void closeJCache() {
        for (var manager : new javax.cache.CacheManager[] { jcacheManager, otherJCacheManager }) {
            if (manager != null && !manager.isClosed()) {
                manager.close();
            }
        }
    }

    @Test
    void readsSizeAndStatisticsFromACaffeineBackedCache() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("files.browse", Caffeine.newBuilder().recordStats().build());
        var cache = manager.getCache("files.browse");
        cache.put("a", 1);
        cache.get("a");
        cache.get("a");
        cache.get("absent");

        var info = only(service(Map.of("fileCacheManager", manager)).getCaches());

        assertThat(info.manager()).isEqualTo("fileCacheManager");
        assertThat(info.name()).isEqualTo("files.browse");
        assertThat(info.implementation()).isEqualTo("caffeine");
        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isEqualTo(2);
        assertThat(info.misses()).isEqualTo(1);
        assertThat(info.hitRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(info.evictions()).isZero();
    }

    @Test
    void reportsNoHitRateUntilSomethingHasAskedTheCache() {
        // Caffeine answers a hit rate of 1.0 while the request count is zero, so a cache that has
        // only ever been written to would be shown as one that never misses.
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("quiet", Caffeine.newBuilder().recordStats().build());
        manager.getCache("quiet").put("a", 1);

        var info = only(service(Map.of("localCacheManager", manager)).getCaches());

        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isZero();
        assertThat(info.misses()).isZero();
        assertThat(info.hitRate()).isNull();
    }

    @Test
    void reportsNoStatisticsWhenTheCaffeineCacheIsNotRecording() {
        // The distinction that matters: a cache nobody counts must not read as a cache nobody hits.
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("quiet", Caffeine.newBuilder().build());
        manager.getCache("quiet").put("a", 1);

        var info = only(service(Map.of("localCacheManager", manager)).getCaches());

        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isNull();
        assertThat(info.misses()).isNull();
        assertThat(info.hitRate()).isNull();
        assertThat(info.evictions()).isNull();
    }

    @Test
    void readsSizeAndStatisticsFromAJCacheBackedCache() {
        // JCache counts in its own layer, reachable only over JMX, while the size comes from the
        // Caffeine cache it unwraps to - so this covers both halves of that split.
        var manager = jcache("extension.json", true);
        var cache = manager.getCache("extension.json");
        cache.put("a", 1);
        cache.get("a");
        cache.get("a");
        cache.get("absent");

        var info = only(service(Map.of("caffeineCacheManager", manager)).getCaches());

        assertThat(info.implementation()).isEqualTo("jcache");
        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isEqualTo(2);
        assertThat(info.misses()).isEqualTo(1);
        assertThat(info.hitRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void reportsSizeButNoStatisticsWhenJCacheIsNotCountingThem() {
        var manager = jcache("unstatted.cache", false);
        manager.getCache("unstatted.cache").put("a", 1);

        var info = only(service(Map.of("caffeineCacheManager", manager)).getCaches());

        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isNull();
        assertThat(info.hitRate()).isNull();
    }

    @Test
    void doesNotReportAnotherManagersStatisticsForTheSameCacheName() {
        // The statistics bean is registered per manager and cache name. Matching on the name alone
        // reads whichever manager registered first, which is how this first showed up: the test
        // passed alone and failed in the suite, reporting an application context's numbers.
        var mine = jcache("shared.name", true);
        otherJCacheManager = jcacheManager("shared.name", true, "test-other-" + java.util.UUID.randomUUID());
        var theirs = springManager(otherJCacheManager).getCache("shared.name");
        theirs.put("a", 1);
        theirs.get("a");
        theirs.get("a");
        theirs.get("a");

        var info = only(service(Map.of("caffeineCacheManager", mine)).getCaches());

        // Untouched, so its own statistics are zero rather than the other manager's three hits.
        assertThat(info.hits()).isZero();
    }

    @Test
    void aCacheRegisteredWithTwoManagersIsReportedUnderEach() {
        // A wiring bug rather than a supported shape: one instance behind two managers is listed
        // twice with the same numbers, and clearing it through one empties what the other reports.
        // It is reported as it is registered, and the service logs the duplicate.
        var shared = Caffeine.newBuilder().recordStats().build();
        var fileManager = new CaffeineCacheManager();
        var localManager = new CaffeineCacheManager();
        fileManager.registerCustomCache("settings", shared);
        localManager.registerCustomCache("settings", shared);
        fileManager.getCache("settings").put("a", 1);

        var service = service(Map.of("fileCacheManager", fileManager, "localCacheManager", localManager));

        assertThat(service.getCaches())
                .extracting(CacheInfo::manager, CacheInfo::name)
                .containsExactly(tuple("fileCacheManager", "settings"), tuple("localCacheManager", "settings"));

        service.clear("fileCacheManager", "settings");
        assertThat(localManager.getCache("settings").get("a")).isNull();
    }

    @Test
    void twoManagersMayEachHoldADifferentCacheOfTheSameName() {
        // The namesake case the duplicate check must not mistake for a duplicate: same name, but
        // separate instances, which is what makes manager and name together identify a cache.
        var first = new CaffeineCacheManager();
        var second = new CaffeineCacheManager();
        first.registerCustomCache("settings", Caffeine.newBuilder().build());
        second.registerCustomCache("settings", Caffeine.newBuilder().build());
        first.getCache("settings").put("a", 1);

        var service = service(Map.of("aManager", first, "bManager", second));

        assertThat(service.getCaches()).hasSize(2);

        service.clear("bManager", "settings");
        assertThat(first.getCache("settings").get("a")).isNotNull();
    }

    @Test
    void readsStatisticsFromARedisBackedCacheButNotItsSize() {
        // Redis counts in the cache writer, not in Redis, and only when the manager was built with
        // enableStatistics. Size stays absent on purpose: counting entries means scanning the
        // keyspace.
        // Only the transport is faked: the hit and miss counting under test is Spring's own, in the
        // statistics-collecting writer that enableStatistics() installs.
        var stored = new java.util.HashMap<String, byte[]>();
        var connection = org.mockito.Mockito.mock(
                org.springframework.data.redis.connection.RedisConnection.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var factory = org.mockito.Mockito
                .mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        org.mockito.Mockito.when(factory.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.stringCommands().get(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> stored.get(new String((byte[]) invocation.getArgument(0))));
        // This cache carries no TTL, so the writer uses the two-argument set rather than the one
        // that takes an expiration.
        org.mockito.Mockito
                .when(
                        connection.stringCommands()
                                .set(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    stored.put(new String((byte[]) invocation.getArgument(0)), invocation.getArgument(1));
                    return true;
                });

        var manager = RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(factory))
                .initialCacheNames(java.util.Set.of("sitemap"))
                .enableStatistics()
                .build();
        manager.afterPropertiesSet();
        var cache = manager.getCache("sitemap");
        cache.put("a", "1");
        cache.get("a");
        cache.get("a");
        cache.get("absent");

        var info = only(service(Map.of("redisCacheManager", manager)).getCaches());

        assertThat(info.implementation()).isEqualTo("redis");
        assertThat(info.entries()).isNull();
        assertThat(info.hits()).isEqualTo(2);
        assertThat(info.misses()).isEqualTo(1);
        assertThat(info.hitRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.0001));
        // Redis expires by TTL rather than evicting under pressure, so there is nothing to report.
        assertThat(info.evictions()).isNull();
    }

    @Test
    void reportsNoStatisticsAtAllWhenCollectionIsDisabled() {
        // Off is the default, and the dashboard has to be able to say so: a Redis cache with no
        // collector answers getStatistics with zeros rather than failing, which would otherwise show
        // as a cache that is never hit.
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("files.browse", Caffeine.newBuilder().recordStats().build());
        manager.getCache("files.browse").put("a", 1);
        manager.getCache("files.browse").get("a");
        var disabled = new CacheInfoService(Map.of("fileCacheManager", manager), false);

        var info = only(disabled.getCaches());

        assertThat(disabled.isStatisticsEnabled()).isFalse();
        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isNull();
        assertThat(info.misses()).isNull();
        assertThat(info.hitRate()).isNull();
        assertThat(info.evictions()).isNull();
    }

    @Test
    void clearingOneCacheLeavesTheOthersAlone() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("one", Caffeine.newBuilder().recordStats().build());
        manager.registerCustomCache("two", Caffeine.newBuilder().recordStats().build());
        manager.getCache("one").put("a", 1);
        manager.getCache("two").put("b", 2);
        var service = service(Map.of("fileCacheManager", manager));

        assertThat(service.clear("fileCacheManager", "one")).isTrue();

        assertThat(manager.getCache("one").get("a")).isNull();
        assertThat(manager.getCache("two").get("b")).isNotNull();
    }

    @Test
    void clearingRejectsAnUnknownManagerOrCache() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("one", Caffeine.newBuilder().build());
        var service = service(Map.of("fileCacheManager", manager));

        assertThat(service.clear("noSuchManager", "one")).isFalse();
        assertThat(service.clear("fileCacheManager", "noSuchCache")).isFalse();
    }

    @Test
    void clearAllSpansEveryManagerAndCountsWhatItCleared() {
        var first = new CaffeineCacheManager();
        first.registerCustomCache("one", Caffeine.newBuilder().build());
        first.registerCustomCache("two", Caffeine.newBuilder().build());
        var second = new CaffeineCacheManager();
        second.registerCustomCache("three", Caffeine.newBuilder().build());
        first.getCache("one").put("a", 1);
        second.getCache("three").put("c", 3);
        var service = service(Map.of("fileCacheManager", first, "localCacheManager", second));

        assertThat(service.clearAll()).isEqualTo(3);

        assertThat(first.getCache("one").get("a")).isNull();
        assertThat(second.getCache("three").get("c")).isNull();
    }

    @Test
    void ordersByCacheName() {
        var first = new CaffeineCacheManager();
        first.registerCustomCache("zebra", Caffeine.newBuilder().build());
        first.registerCustomCache("aardvark", Caffeine.newBuilder().build());
        var second = new CaffeineCacheManager();
        second.registerCustomCache("middle", Caffeine.newBuilder().build());

        var caches = service(Map.of("bManager", first, "aManager", second)).getCaches();

        // ordered by cache name, since a cache is no longer filed under a single manager
        assertThat(caches).extracting(CacheInfo::name).containsExactly("aardvark", "middle", "zebra");
    }

    /**
     * Its own URI per test: the statistics MXBean is registered per manager and cache name, and the
     * rest of the suite boots application contexts whose caches carry the same names.
     */
    private CacheManager jcache(String cacheName, boolean statistics) {
        jcacheManager = jcacheManager(cacheName, statistics, "test-" + java.util.UUID.randomUUID());
        return springManager(jcacheManager);
    }

    private javax.cache.CacheManager jcacheManager(String cacheName, boolean statistics, String uri) {
        var configuration = new CaffeineConfiguration<>();
        configuration.setMaximumSize(OptionalLong.of(100));
        configuration.setStatisticsEnabled(statistics);
        var provider = javax.cache.Caching.getCachingProvider(CaffeineCachingProvider.class.getName());
        var manager = provider.getCacheManager(java.net.URI.create(uri), provider.getDefaultClassLoader());
        manager.createCache(cacheName, configuration);
        return manager;
    }

    /**
     * afterPropertiesSet, as Spring would: without it getCacheNames is empty until something asks
     * for a cache by name, and the service enumerates by name.
     */
    private CacheManager springManager(javax.cache.CacheManager manager) {
        var springManager = new JCacheCacheManager(manager);
        springManager.afterPropertiesSet();
        return springManager;
    }

    /** Statistics on, which is what every test here but one is about. */
    private CacheInfoService service(Map<String, CacheManager> managers) {
        return new CacheInfoService(managers, true);
    }

    private CacheInfo only(List<CacheInfo> caches) {
        assertThat(caches).hasSize(1);
        return caches.getFirst();
    }
}
