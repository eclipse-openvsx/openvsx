/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.annotation.Observed;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.AfterCommitExecutor;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

/**
 * Drops what the caches hold about rows that changed.
 * <p>
 * Every eviction here happens once the surrounding transaction has committed - see
 * {@link AfterCommitExecutor} for why one sent before the commit can leave a cache staler than no
 * eviction at all. It still happens on the calling thread. Callers hand over the names they are
 * evicting by rather than the entities, because by the time the work runs it is outside the
 * transaction, where an entity may be detached.
 * <p>
 * The file caches below are the exception: they are keyed by the file just written rather than by a
 * row, and their callers are not in a transaction.
 */
@Component
public class CacheService {

    public static final String CACHE_DATABASE_SEARCH = "database.search";
    public static final String CACHE_WEB_RESOURCE_FILES = "files.webresource";
    public static final String CACHE_BROWSE_EXTENSION_FILES = "files.browse";
    public static final String CACHE_EXTENSION_FILES = "files.extension";
    public static final String CACHE_EXTENSION_JSON = "extension.json";
    public static final String CACHE_LATEST_EXTENSION_VERSION = "latest.extension.version";
    public static final String CACHE_LATEST_EXTENSION_VERSION_VSCODE = "latest.extension.version.vscode";
    public static final String CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM = "latest.extension.versions.by.platform";
    public static final String CACHE_NAMESPACE_DETAILS_JSON = "namespace.details.json";
    public static final String CACHE_AVERAGE_REVIEW_RATING = "average.review.rating";
    public static final String CACHE_SITEMAP = "sitemap";
    public static final String CACHE_MALICIOUS_EXTENSIONS = "malicious.extensions";
    public static final String CACHE_SETTING = "settings";

    public static final String GENERATOR_EXTENSION_JSON = "extensionJsonCacheKeyGenerator";
    public static final String GENERATOR_LATEST_EXTENSION_VERSION = "latestExtensionVersionCacheKeyGenerator";
    public static final String GENERATOR_LATEST_EXTENSION_VERSIONS_BY_PLATFORM = "latestExtensionVersionsByPlatformCacheKeyGenerator";
    public static final String GENERATOR_FILES = "filesCacheKeyGenerator";

    private final CacheManager cacheManager;
    private final CacheManager fileCacheManager;
    private final RepositoryService repositories;
    private final ExtensionJsonCacheKeyGenerator extensionJsonCacheKey;
    private final LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKey;
    private final LatestExtensionVersionsByPlatformCacheKeyGenerator latestExtensionVersionsByPlatformCacheKeyGenerator;
    private final FilesCacheKeyGenerator filesCacheKeyGenerator;
    private final AfterCommitExecutor afterCommit;

    public CacheService(
            CacheManager cacheManager,
            @Qualifier("fileCacheManager") CacheManager fileCacheManager,
            RepositoryService repositories,
            ExtensionJsonCacheKeyGenerator extensionJsonCacheKey,
            LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKey,
            LatestExtensionVersionsByPlatformCacheKeyGenerator latestExtensionVersionsByPlatformCacheKeyGenerator,
            FilesCacheKeyGenerator filesCacheKeyGenerator,
            AfterCommitExecutor afterCommit
    ) {
        this.cacheManager = cacheManager;
        this.fileCacheManager = fileCacheManager;
        this.repositories = repositories;
        this.extensionJsonCacheKey = extensionJsonCacheKey;
        this.latestExtensionVersionCacheKey = latestExtensionVersionCacheKey;
        this.latestExtensionVersionsByPlatformCacheKeyGenerator = latestExtensionVersionsByPlatformCacheKeyGenerator;
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
        this.afterCommit = afterCommit;
    }

    public void evictSitemap() {
        afterCommit.execute(() -> invalidateCache(CACHE_SITEMAP));
    }

    public void evictNamespaceDetails() {
        afterCommit.execute(() -> invalidateCache(CACHE_NAMESPACE_DETAILS_JSON));
    }

    public void evictNamespaceDetails(Namespace namespace) {
        var namespaceName = namespace.getName();
        afterCommit.execute(() -> evictNamespaceDetails(namespaceName));
    }

    public void evictNamespaceDetails(Extension extension) {
        var namespaceName = extension.getNamespace().getName();
        afterCommit.execute(() -> evictNamespaceDetails(namespaceName));
    }

    private void evictNamespaceDetails(String namespaceName) {
        var cache = cacheManager.getCache(CACHE_NAMESPACE_DETAILS_JSON);
        if (cache == null) {
            return; // cache is not created
        }

        cache.evictIfPresent(namespaceName);
    }

    public void evictExtensionJsons() {
        afterCommit.execute(() -> invalidateCache(CACHE_EXTENSION_JSON));
    }

    public void evictExtensionJsons(UserData user) {
        repositories.findExtensions(user).forEach(this::evictExtensionJsons);
    }

    public void evictExtensionJsons(Extension extension) {
        var namespaceName = extension.getNamespace().getName();
        var extensionName = extension.getName();
        // Read now, evict later: what the eviction needs has to be read while there is still a
        // persistence context, because the task runs without one. Only what it needs, though - a
        // cache that clears by key prefix is told the extension's name and nothing else, so the
        // versions, a lazy association, are not loaded at all.
        var versions = clearsByKeyPrefix(cacheManager.getCache(CACHE_EXTENSION_JSON))
                ? List.<String>of()
                : extension.getVersions().stream().map(ExtensionVersion::getVersion).toList();
        afterCommit.execute(() -> evictExtensionJsons(namespaceName, extensionName, versions));
    }

    private void evictExtensionJsons(String namespaceName, String extensionName, List<String> extensionVersions) {
        var cache = cacheManager.getCache(CACHE_EXTENSION_JSON);
        if (cache == null) {
            return; // cache is not created
        }
        // one scan of the keys the cache holds, instead of the (versions x target platforms) guesses
        // below - see clearByKeyPrefix
        if (clearByKeyPrefix(cache, extensionJsonCacheKey.generatePrefix(namespaceName, extensionName))) {
            return;
        }

        var versions = new ArrayList<>(VersionAlias.ALIAS_NAMES);
        versions.addAll(extensionVersions);

        var targetPlatforms = new ArrayList<>(TargetPlatform.TARGET_PLATFORM_NAMES);
        targetPlatforms.add("null");
        for (var version : versions) {
            for (var targetPlatform : targetPlatforms) {
                cache.evictIfPresent(
                        extensionJsonCacheKey.generate(namespaceName, extensionName, targetPlatform, version));
            }
        }
    }

    public void evictExtensionJsons(ExtensionVersion extVersion) {
        var extension = extVersion.getExtension();
        var namespaceName = extension.getNamespace().getName();
        var extensionName = extension.getName();
        var targetPlatform = extVersion.getTargetPlatform();
        var versions = new ArrayList<>(List.of(VersionAlias.LATEST, extVersion.getVersion()));
        if (extVersion.isPreRelease()) {
            versions.add(VersionAlias.PRE_RELEASE);
        }
        if (extVersion.isPreview()) {
            versions.add(VersionAlias.PREVIEW);
        }

        afterCommit.execute(() -> {
            var cache = cacheManager.getCache(CACHE_EXTENSION_JSON);
            if (cache == null) {
                return; // cache is not created
            }
            for (var version : versions) {
                cache.evictIfPresent(
                        extensionJsonCacheKey.generate(namespaceName, extensionName, targetPlatform, version));
            }
        });
    }

    public void evictLatestExtensionVersions() {
        afterCommit.execute(() -> {
            invalidateCache(CACHE_LATEST_EXTENSION_VERSION);
            invalidateCache(CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM);
            invalidateCache(CACHE_LATEST_EXTENSION_VERSION_VSCODE);
        });
    }

    public void evictLatestExtensionVersion(Extension extension) {
        var namespaceName = extension.getNamespace().getName();
        var extensionName = extension.getName();
        afterCommit.execute(() -> {
            evictInternalLatestExtensionVersion(namespaceName, extensionName);
            evictInternalLatestExtensionVersionsByPlatform(namespaceName, extensionName);
            evictInternalLatestExtensionVersionVSCode(namespaceName, extensionName);
        });
    }

    private void evictInternalLatestExtensionVersion(String namespaceName, String extensionName) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION);
        if (cache == null) {
            return;
        }

        if (clearByKeyPrefix(cache, latestExtensionVersionCacheKey.generatePrefix(namespaceName, extensionName))) {
            return;
        }

        var targetPlatforms = new ArrayList<>(TargetPlatform.TARGET_PLATFORM_NAMES);
        targetPlatforms.add(null);
        for (var targetPlatform : targetPlatforms) {
            for (var preRelease : List.of(true, false)) {
                for (var onlyActive : List.of(true, false)) {
                    for (var type : ExtensionVersion.Type.values()) {
                        var key = latestExtensionVersionCacheKey
                                .generate(namespaceName, extensionName, targetPlatform, preRelease, onlyActive, type);
                        cache.evictIfPresent(key);
                    }
                }
            }
        }
    }

    private void evictInternalLatestExtensionVersionsByPlatform(String namespaceName, String extensionName) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM);
        if (cache == null) {
            return;
        }

        for (var preRelease : List.of(true, false)) {
            var key = latestExtensionVersionsByPlatformCacheKeyGenerator
                    .generate(namespaceName, extensionName, preRelease);
            cache.evictIfPresent(key);
        }
    }

    private void evictInternalLatestExtensionVersionVSCode(String namespaceName, String extensionName) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION_VSCODE);
        if (cache == null) {
            return;
        }

        var key = new SimpleKey(namespaceName, extensionName);
        cache.evictIfPresent(key);
    }

    /**
     * Drops the keys of one extension - those starting with {@code prefix} - and says whether it could.
     * <p>
     * This is what replaces guessing. The fallback enumerates every key it can imagine, which is
     * {@code (3 aliases + versions) x 13 target platforms} evictions of keys that mostly do not
     * exist - 2600 of them for an extension with 200 versions - and it can still miss the ones it did
     * not think of, such as a version that was just deleted and is no longer among those the caller
     * can enumerate. Asking a cache what it actually holds is both cheaper and complete.
     * <ul>
     *     <li>Redis scans its own keyspace. {@link RedisCache#clear(String)} rather than the writer
     *     behind {@link Cache#getNativeCache()}, because it runs the pattern through the cache's own
     *     key prefix first; a pattern built here would match nothing.</li>
     *     <li>The local caches are Caffeine, reached either directly or through the JCache API, and
     *     their key set is scanned. Through the native cache in both cases: iterating a
     *     {@code javax.cache.Cache} walks entries rather than keys, and its iterator copies each
     *     value and refreshes each entry's access expiry on the way past - work this has no use for,
     *     over values as large as an extension's JSON.</li>
     * </ul>
     * Scanning is bounded by what the cache holds, where guessing is bounded by versions times target
     * platforms, so which is cheaper depends on the extension. Completeness does not: guessing cannot
     * evict a key it did not think of, such as that of a version which was just deleted.
     */
    private boolean clearByKeyPrefix(Cache cache, String prefix) {
        if (cache instanceof RedisCache redisCache) {
            redisCache.clear(prefix + "*");
            return true;
        }

        var caffeineCache = caffeineCacheOf(cache);
        if (caffeineCache != null) {
            // weakly consistent, which is all this needs: an entry written while it runs belongs to
            // the state after the change, and one removed under it is already gone
            caffeineCache.asMap().keySet().removeIf(key -> startsWith(key, prefix));
            return true;
        }

        return false;
    }

    /**
     * The Caffeine cache behind a Spring one, however it is wrapped: directly for the file and
     * settings caches, and through the JCache API for the ones a publish evicts.
     */
    private static com.github.benmanes.caffeine.cache.@Nullable Cache<Object, Object> caffeineCacheOf(Cache cache) {
        var nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?>) {
            @SuppressWarnings("unchecked")
            var caffeineCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache;
            return caffeineCache;
        }
        if (nativeCache instanceof javax.cache.Cache<?, ?> jCache) {
            @SuppressWarnings("unchecked")
            var caffeineCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) jCache
                    .unwrap(com.github.benmanes.caffeine.cache.Cache.class);
            return caffeineCache;
        }

        return null;
    }

    /**
     * Whether {@link #clearByKeyPrefix} would take this cache - which decides what a caller has to
     * read from the entity before handing the eviction over. Every cache in use here can, so the
     * versions are in practice never loaded for an eviction; the guessing is what is left for a
     * cache that is neither.
     */
    private static boolean clearsByKeyPrefix(@Nullable Cache cache) {
        return cache != null && (cache instanceof RedisCache || caffeineCacheOf(cache) != null);
    }

    private static boolean startsWith(Object key, String prefix) {
        return key instanceof String name && name.startsWith(prefix);
    }

    private void invalidateCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }

        cache.invalidate();
    }

    public void evictExtensionFile(FileResource download) {
        var cache = fileCacheManager.getCache(CACHE_EXTENSION_FILES);
        if (cache == null) {
            return;
        }

        cache.evict(filesCacheKeyGenerator.generate(download));
    }

    @Observed
    public void evictWebResourceFile(
            String namespaceName,
            String extensionName,
            String targetPlatform,
            String version,
            String path
    ) {
        var cache = fileCacheManager.getCache(CACHE_WEB_RESOURCE_FILES);
        if (cache == null) {
            return;
        }

        cache.evict(filesCacheKeyGenerator.generate(namespaceName, extensionName, targetPlatform, version, path));
    }
}
