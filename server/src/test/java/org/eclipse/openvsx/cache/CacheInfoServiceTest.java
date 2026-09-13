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

import static org.assertj.core.api.Assertions.assertThat;

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

        var info = only(new CacheInfoService(Map.of("fileCacheManager", manager)).getCaches());

        assertThat(info.managers()).containsExactly("fileCacheManager");
        assertThat(info.name()).isEqualTo("files.browse");
        assertThat(info.implementation()).isEqualTo("caffeine");
        assertThat(info.entries()).isEqualTo(1);
        assertThat(info.hits()).isEqualTo(2);
        assertThat(info.misses()).isEqualTo(1);
        assertThat(info.hitRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(info.evictions()).isZero();
    }

    @Test
    void reportsNoStatisticsWhenTheCaffeineCacheIsNotRecording() {
        // The distinction that matters: a cache nobody counts must not read as a cache nobody hits.
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("quiet", Caffeine.newBuilder().build());
        manager.getCache("quiet").put("a", 1);

        var info = only(new CacheInfoService(Map.of("localCacheManager", manager)).getCaches());

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

        var info = only(new CacheInfoService(Map.of("caffeineCacheManager", manager)).getCaches());

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

        var info = only(new CacheInfoService(Map.of("caffeineCacheManager", manager)).getCaches());

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

        var info = only(new CacheInfoService(Map.of("caffeineCacheManager", mine)).getCaches());

        // Untouched, so its own statistics are zero rather than the other manager's three hits.
        assertThat(info.hits()).isZero();
    }

    @Test
    void aSharedCacheIsOneCacheBehindTwoManagers() {
        // CacheConfig registers the same settingCache bean with both the file and the local cache
        // manager, so `settings` is not two caches: clearing either empties the other.
        var shared = Caffeine.newBuilder().recordStats().build();
        var fileManager = new CaffeineCacheManager();
        var localManager = new CaffeineCacheManager();
        fileManager.registerCustomCache("settings", shared);
        localManager.registerCustomCache("settings", shared);
        fileManager.getCache("settings").put("a", 1);

        assertThat(localManager.getCache("settings").get("a")).isNotNull();

        var service = new CacheInfoService(Map.of("fileCacheManager", fileManager, "localCacheManager", localManager));

        // Reported once, naming both ways in, rather than as two caches with the same numbers.
        var info = only(service.getCaches());
        assertThat(info.name()).isEqualTo("settings");
        assertThat(info.managers()).containsExactly("fileCacheManager", "localCacheManager");

        service.clear("fileCacheManager", "settings");
        assertThat(localManager.getCache("settings").get("a")).isNull();
    }

    @Test
    void clearingOneCacheLeavesTheOthersAlone() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("one", Caffeine.newBuilder().recordStats().build());
        manager.registerCustomCache("two", Caffeine.newBuilder().recordStats().build());
        manager.getCache("one").put("a", 1);
        manager.getCache("two").put("b", 2);
        var service = new CacheInfoService(Map.of("fileCacheManager", manager));

        assertThat(service.clear("fileCacheManager", "one")).isTrue();

        assertThat(manager.getCache("one").get("a")).isNull();
        assertThat(manager.getCache("two").get("b")).isNotNull();
    }

    @Test
    void clearingRejectsAnUnknownManagerOrCache() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("one", Caffeine.newBuilder().build());
        var service = new CacheInfoService(Map.of("fileCacheManager", manager));

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
        var service = new CacheInfoService(Map.of("fileCacheManager", first, "localCacheManager", second));

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

        var caches = new CacheInfoService(Map.of("bManager", first, "aManager", second)).getCaches();

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

    private CacheInfo only(List<CacheInfo> caches) {
        assertThat(caches).hasSize(1);
        return caches.getFirst();
    }
}
