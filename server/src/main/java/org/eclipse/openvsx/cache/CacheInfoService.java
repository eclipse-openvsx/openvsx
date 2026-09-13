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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.cache.management.CacheStatisticsMXBean;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.jcache.JCacheCache;
import org.springframework.stereotype.Component;

/**
 * Reports on the caches registered with every {@link CacheManager} in the context, and clears them.
 * <p>
 * There is no single way to ask a Spring {@code Cache} how big it is or how often it was hit, so
 * each implementation is read differently:
 * <ul>
 * <li>a Caffeine-backed cache answers both from its native cache, provided it was built with
 * {@code recordStats()};</li>
 * <li>a JCache-backed cache gives its size through the Caffeine cache it unwraps to, but counts
 * statistics in its own JCache layer, which is only reachable over JMX and only when the cache was
 * configured with {@code statisticsEnabled};</li>
 * <li>a Redis-backed cache answers neither without scanning the keyspace, which is not something to
 * do behind an admin page.</li>
 * </ul>
 * Anything unavailable is reported as null rather than as zero, so the dashboard can say "not
 * measured here" instead of claiming an empty cache that is not empty.
 */
@Component
public class CacheInfoService {

    protected final Logger logger = LoggerFactory.getLogger(CacheInfoService.class);

    private final Map<String, CacheManager> cacheManagers;

    public CacheInfoService(Map<String, CacheManager> cacheManagers) {
        this.cacheManagers = cacheManagers;
    }

    /**
     * Every cache of every manager, ordered by manager then name so the dashboard is stable across
     * refreshes.
     */
    public List<CacheInfo> getCaches() {
        // Collected before describing, because the same cache can be registered with more than one
        // manager and reporting it twice would show one cache as two and double its numbers.
        var found = new ArrayList<FoundCache>();
        for (var managerEntry : cacheManagers.entrySet()) {
            var managerName = managerEntry.getKey();
            var manager = managerEntry.getValue();
            for (var cacheName : manager.getCacheNames()) {
                var cache = manager.getCache(cacheName);
                if (cache != null) {
                    merge(found, managerName, cacheName, cache);
                }
            }
        }

        var caches = found.stream()
                .map(entry -> describe(entry.managers().stream().sorted().toList(), entry.name(), entry.cache()))
                .sorted(Comparator.comparing(CacheInfo::name).thenComparing(info -> String.join(",", info.managers())))
                .toList();
        return caches;
    }

    /**
     * Adds a cache to the collection, or records another way in to one already there.
     * <p>
     * Two entries are the same cache only when the name and the native cache instance both match.
     * Identity alone would not do: some implementations hand out a shared object as their native
     * cache - a Redis cache's writer belongs to its manager rather than to the cache - which would
     * collapse unrelated caches into one row. Requiring the name to match as well keeps that safe,
     * since distinct caches of one manager have distinct names by construction.
     */
    private void merge(
            List<FoundCache> found,
            String managerName,
            String cacheName,
            org.springframework.cache.Cache cache
    ) {
        for (var entry : found) {
            if (entry.name().equals(cacheName) && entry.cache().getNativeCache() == cache.getNativeCache()) {
                entry.managers().add(managerName);
                return;
            }
        }

        var managers = new ArrayList<String>();
        managers.add(managerName);
        found.add(new FoundCache(cacheName, cache, managers));
    }

    private record FoundCache(String name, org.springframework.cache.Cache cache, List<String> managers) {}

    /**
     * Clears one cache. Returns false when no such cache is registered with that manager, which the
     * caller reports as a 404 rather than as a silent success.
     */
    public boolean clear(String managerName, String cacheName) {
        var manager = cacheManagers.get(managerName);
        if (manager == null) {
            return false;
        }

        // getCache alone is not a membership test: CaffeineCacheManager creates a cache on demand
        // for any name it is asked about, so a typo would register an empty cache and report having
        // cleared it. Ask what is actually registered first.
        if (!manager.getCacheNames().contains(cacheName)) {
            return false;
        }

        var cache = manager.getCache(cacheName);
        if (cache == null) {
            return false;
        }

        logger.info("admin cleared cache {} of {}", cacheName, managerName);
        cache.clear();
        return true;
    }

    /**
     * Clears every cache of every manager, and returns how many were cleared.
     */
    public int clearAll() {
        var cleared = 0;
        for (var managerEntry : cacheManagers.entrySet()) {
            var manager = managerEntry.getValue();
            for (var cacheName : manager.getCacheNames()) {
                var cache = manager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    cleared++;
                }
            }
        }

        logger.info("admin cleared all {} cache(s)", cleared);
        return cleared;
    }

    private CacheInfo describe(List<String> managers, String cacheName, org.springframework.cache.Cache cache) {
        if (cache instanceof CaffeineCache caffeineCache) {
            var native_ = caffeineCache.getNativeCache();
            var stats = native_.stats();
            // Caffeine hands back a zeroed CacheStats when the cache was built without recordStats,
            // which would read as a cache that is never hit rather than one that is not counting.
            var recording = native_.policy().isRecordingStats();
            return new CacheInfo(
                    managers,
                    cacheName,
                    "caffeine",
                    native_.estimatedSize(),
                    recording ? stats.hitCount() : null,
                    recording ? stats.missCount() : null,
                    recording ? stats.hitRate() : null,
                    recording ? stats.evictionCount() : null);
        }

        if (cache instanceof JCacheCache) {
            return describeJCache(managers, cacheName, cache);
        }

        return new CacheInfo(managers, cacheName, implementationOf(cache), null, null, null, null, null);
    }

    private CacheInfo describeJCache(List<String> managers, String cacheName, org.springframework.cache.Cache cache) {
        Long entries = null;
        var native_ = cache.getNativeCache();
        if (native_ instanceof javax.cache.Cache<?, ?> jcache) {
            try {
                // caffeine-jcache wraps a Caffeine cache, which is the only way to its size; other
                // JCache providers need not support this, hence the guard rather than a cast.
                entries = jcache.unwrap(com.github.benmanes.caffeine.cache.Cache.class).estimatedSize();
            } catch (RuntimeException e) {
                logger.debug("cache {} does not unwrap to a Caffeine cache, reporting no size", cacheName, e);
            }
        }

        var stats = jcacheStatistics(cacheName, managerUri(native_));
        if (stats == null) {
            return new CacheInfo(managers, cacheName, "jcache", entries, null, null, null, null);
        }

        var hits = stats.getCacheHits();
        var misses = stats.getCacheMisses();
        return new CacheInfo(
                managers,
                cacheName,
                "jcache",
                entries,
                hits,
                misses,
                // getCacheHitPercentage is a percentage and reads 0 for an untouched cache; derive
                // the rate instead so an untouched cache reports no rate at all.
                hits + misses == 0 ? null : (double) hits / (hits + misses),
                stats.getCacheEvictions());
    }

    /**
     * The URI of the JCache manager a native cache belongs to, which is what the statistics MXBean
     * is registered under.
     */
    private @Nullable String managerUri(Object nativeCache) {
        if (nativeCache instanceof javax.cache.Cache<?, ?> jcache) {
            var manager = jcache.getCacheManager();
            if (manager != null && manager.getURI() != null) {
                return manager.getURI().toString();
            }
        }

        return null;
    }

    /**
     * The JCache statistics MXBean for a cache, or null when there is no unambiguous one.
     * <p>
     * JCache only registers the bean when {@code statisticsEnabled} is set, so its absence is the
     * normal way a cache says it is not counting. The manager's URI has to be part of the match
     * because the bean is registered per manager and cache name: two managers can hold a cache of
     * the same name, and matching on the name alone would report one manager's numbers under the
     * other's cache. When the URI cannot be established and more than one bean carries the name,
     * report nothing rather than pick one.
     */
    private @Nullable CacheStatisticsMXBean jcacheStatistics(String cacheName, @Nullable String managerUri) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            var candidates = new ArrayList<ObjectName>();
            for (var pattern : List.of(
                    "javax.cache:type=CacheStatistics,*,Cache=" + ObjectName.quote(cacheName),
                    "javax.cache:type=CacheStatistics,*,Cache=" + cacheName)) {
                try {
                    candidates.addAll(server.queryNames(new ObjectName(pattern), null));
                } catch (MalformedObjectNameException e) {
                    logger.debug("not a usable object name pattern: {}", pattern, e);
                }
            }

            if (managerUri != null) {
                // The provider registers the URI verbatim when it needs no quoting, and quoted when
                // it does, so accept either spelling of the same manager.
                var quoted = ObjectName.quote(managerUri);
                candidates.removeIf(name -> {
                    var property = name.getKeyProperty("CacheManager");
                    return !managerUri.equals(property) && !quoted.equals(property);
                });
            }

            if (candidates.size() != 1) {
                if (candidates.size() > 1) {
                    logger.debug("{} statistics beans match cache {}, reporting none", candidates.size(), cacheName);
                }
                return null;
            }

            return JMX.newMBeanProxy(server, candidates.getFirst(), CacheStatisticsMXBean.class);
        } catch (RuntimeException e) {
            logger.debug("could not read JCache statistics for {}", cacheName, e);
            return null;
        }
    }

    private String implementationOf(org.springframework.cache.Cache cache) {
        var name = cache.getClass().getSimpleName().toLowerCase();
        if (name.contains("redis")) {
            return "redis";
        }

        return cache.getClass().getSimpleName();
    }
}
