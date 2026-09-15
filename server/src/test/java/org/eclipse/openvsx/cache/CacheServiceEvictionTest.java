/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.cache;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.eclipse.openvsx.cache.CacheService.CACHE_EXTENSION_JSON;
import static org.eclipse.openvsx.cache.CacheService.CACHE_LATEST_EXTENSION_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Which way an eviction goes: one pattern clear where the cache can scan its own keyspace, and the
 * key-by-key fallback where it cannot.
 */
class CacheServiceEvictionTest {

    private final CacheManager cacheManager = Mockito.mock(CacheManager.class);

    private CacheService service() {
        return new CacheService(
                cacheManager,
                Mockito.mock(CacheManager.class),
                Mockito.mock(RepositoryService.class),
                new ExtensionJsonCacheKeyGenerator(),
                new LatestExtensionVersionCacheKeyGenerator(),
                new LatestExtensionVersionsByPlatformCacheKeyGenerator(),
                Mockito.mock(FilesCacheKeyGenerator.class));
    }

    private static Extension extension(int versions) {
        var namespace = new Namespace();
        namespace.setName("Foo");
        var extension = new Extension();
        extension.setName("Bar");
        extension.setNamespace(namespace);
        for (var i = 0; i < versions; i++) {
            var version = new ExtensionVersion();
            version.setVersion("1.0." + i);
            extension.getVersions().add(version);
        }
        return extension;
    }

    @Test
    void dropsEveryExtensionJsonKeyInOneScanWhereRedisCan() {
        var cache = Mockito.mock(RedisCache.class);
        Mockito.when(cacheManager.getCache(CACHE_EXTENSION_JSON)).thenReturn(cache);

        service().evictExtensionJsons(extension(200));

        // lower-cased, because that is how the keys are generated
        verify(cache).clear("foo.bar*");
        verify(cache, never()).evictIfPresent(any());
    }

    // 200 versions x 13 target platforms plus the aliases: the guessing this replaces.
    @Test
    void guessesTheKeysWhereTheCacheCannotScan() {
        var cache = Mockito.mock(Cache.class);
        Mockito.when(cacheManager.getCache(CACHE_EXTENSION_JSON)).thenReturn(cache);

        service().evictExtensionJsons(extension(200));

        verify(cache, atLeast(2600)).evictIfPresent(any());
    }

    @Test
    void dropsEveryLatestVersionKeyInOneScanWhereRedisCan() {
        var cache = Mockito.mock(RedisCache.class);
        Mockito.when(cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION)).thenReturn(cache);

        service().evictLatestExtensionVersion(extension(1));

        verify(cache).clear("foo.bar*");
        verify(cache, never()).evictIfPresent(any());
    }

    // Guessing depends on the versions being loaded; a pattern clear does not.
    @Test
    void clearsByPatternForAnExtensionWithNoVersionsLoaded() {
        var cache = Mockito.mock(RedisCache.class);
        Mockito.when(cacheManager.getCache(CACHE_EXTENSION_JSON)).thenReturn(cache);

        service().evictExtensionJsons(extension(0));

        verify(cache).clear("foo.bar*");
    }
}
