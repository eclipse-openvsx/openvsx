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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.AfterCommitExecutor;

import static org.assertj.core.api.Assertions.assertThat;
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
                Mockito.mock(FilesCacheKeyGenerator.class),
                // inline, so these tests stay about which way an eviction goes rather than when
                new AfterCommitExecutor() {
                    @Override
                    public void execute(Runnable task) {
                        task.run();
                    }
                });
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
        verify(cache).clear("foo.bar:*");
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

        verify(cache).clear("foo.bar:*");
        verify(cache, never()).evictIfPresent(any());
    }

    // Guessing depends on the versions being loaded; a pattern clear does not.
    @Test
    void clearsByPatternForAnExtensionWithNoVersionsLoaded() {
        var cache = Mockito.mock(RedisCache.class);
        Mockito.when(cacheManager.getCache(CACHE_EXTENSION_JSON)).thenReturn(cache);

        service().evictExtensionJsons(extension(0));

        verify(cache).clear("foo.bar:*");
    }

    /**
     * What the pattern is allowed to sweep. Matched here rather than in Redis, so this is about the
     * pattern we generate and not about what a particular server does with it.
     */
    @Test
    void sweepsEveryKeyOfTheExtensionAndNotASiblingsKeys() {
        var keys = new ExtensionJsonCacheKeyGenerator();
        var pattern = keys.generateWildcard(extension(0));

        // every shape the key-by-key fallback would evict
        assertThat(keys.generate("foo", "bar", "universal", "1.0.0")).isEqualTo("foo.bar:1.0.0");
        assertThat(keys.generate("foo", "bar", "linux-x64", "1.0.0")).isEqualTo("foo.bar:1.0.0@linux-x64");
        assertThat(matches(pattern, keys.generate("foo", "bar", "universal", "1.0.0"))).isTrue();
        assertThat(matches(pattern, keys.generate("foo", "bar", "linux-x64", "1.0.0"))).isTrue();
        assertThat(matches(pattern, keys.generate("foo", "bar", "universal", "latest"))).isTrue();
        assertThat(matches(pattern, keys.generate("foo", "bar", "universal", "1.0.0-rc.1"))).isTrue();

        // and not the extension next to it, which is what the separator is for
        assertThat(matches(pattern, keys.generate("foo", "bar2", "universal", "1.0.0"))).isFalse();
        assertThat(matches(pattern, keys.generate("other", "bar", "universal", "1.0.0"))).isFalse();
    }

    // The case the terminator exists for: a name may contain a "-" and so may a version, so nothing
    // separated the keys of "bar" from those of "bar-baz" while "-" ended the id.
    @Test
    void leavesAHyphenatedSiblingAlone() {
        var keys = new ExtensionJsonCacheKeyGenerator();

        assertThat(matches(keys.generateWildcard(extension(0)), keys.generate("foo", "bar-baz", "universal", "1.0.0")))
                .isFalse();
    }

    // The character set is what the validator enforces now; rows older than it, or mirrored from
    // elsewhere, were never held to it, and a name carrying the terminator would otherwise end the id
    // early and take a sibling's keys with it.
    @Test
    void escapesATerminatorSmuggledIntoAName() {
        var keys = new ExtensionJsonCacheKeyGenerator();

        assertThat(keys.generatePrefix("foo", "bar:baz")).isEqualTo("foo.bar%3Abaz:");
        assertThat(matches(keys.generateWildcard(extension(0)), keys.generate("foo", "bar:baz", "universal", "1.0.0")))
                .isFalse();
    }

    /**
     * A prefix becomes a Redis glob, where these characters are syntax rather than text: unescaped,
     * a name like {@code bar*} would widen the pattern over its siblings.
     */
    @ParameterizedTest
    @ValueSource(strings = { "bar*", "bar?", "bar[a-z]", "bar\\baz", "bar.baz", "bar baz" })
    void escapesEverythingAGlobWouldReadAsSyntax(String name) {
        var keys = new ExtensionJsonCacheKeyGenerator();

        var prefix = keys.generatePrefix("foo", name);

        // only the safe set, the "." joining namespace and extension, and percent escapes
        assertThat(prefix).matches("[a-z0-9_+$~.-]*(%[0-9A-F]{2}[a-z0-9_+$~.-]*)*:");
        assertThat(matches(keys.generateWildcard(extension(0)), keys.generate("foo", name, "universal", "1.0.0")))
                .isFalse();
    }

    @Test
    void leavesAValidNameAsItIs() {
        var keys = new ExtensionJsonCacheKeyGenerator();

        assertThat(keys.generatePrefix("Foo-Bar", "baz_qux+1~2$3")).isEqualTo("foo-bar.baz_qux+1~2$3:");
    }

    /** Redis glob, as far as these patterns use it: {@code *} is anything, everything else is literal. */
    private static boolean matches(String pattern, String key) {
        var regex = java.util.Arrays.stream(pattern.split("\\*", -1))
                .map(java.util.regex.Pattern::quote)
                .collect(java.util.stream.Collectors.joining(".*"));
        return key.matches(regex);
    }
}
