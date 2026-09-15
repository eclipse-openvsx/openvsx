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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.cdn.CdnPurgeService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

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
    private final CdnPurgeService cdnPurge;

    public CacheService(
            CacheManager cacheManager,
            @Qualifier("fileCacheManager") CacheManager fileCacheManager,
            RepositoryService repositories,
            ExtensionJsonCacheKeyGenerator extensionJsonCacheKey,
            LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKey,
            LatestExtensionVersionsByPlatformCacheKeyGenerator latestExtensionVersionsByPlatformCacheKeyGenerator,
            FilesCacheKeyGenerator filesCacheKeyGenerator,
            CdnPurgeService cdnPurge
    ) {
        this.cacheManager = cacheManager;
        this.fileCacheManager = fileCacheManager;
        this.repositories = repositories;
        this.extensionJsonCacheKey = extensionJsonCacheKey;
        this.latestExtensionVersionCacheKey = latestExtensionVersionCacheKey;
        this.latestExtensionVersionsByPlatformCacheKeyGenerator = latestExtensionVersionsByPlatformCacheKeyGenerator;
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
        this.cdnPurge = cdnPurge;
    }

    /**
     * Tells a CDN in front of this registry that the extension changed, alongside evicting what this
     * instance holds itself. Collected and sent once the surrounding transaction commits, so several
     * evictions for one change cost one purge - see {@link CdnPurgeService}.
     */
    private void purgeFromCdn(Extension extension) {
        var namespace = extension.getNamespace();
        if (namespace != null && extension.getName() != null) {
            cdnPurge.purgeExtension(namespace.getName(), extension.getName());
        }
    }

    public void evictSitemap() {
        invalidateCache(CACHE_SITEMAP);
    }

    public void evictNamespaceDetails() {
        invalidateCache(CACHE_NAMESPACE_DETAILS_JSON);
    }

    public void evictNamespaceDetails(Namespace namespace) {
        evictNamespaceDetails(namespace.getName());
    }

    public void evictNamespaceDetails(Extension extension) {
        evictNamespaceDetails(extension.getNamespace().getName());
    }

    private void evictNamespaceDetails(String namespaceName) {
        cdnPurge.purgeNamespace(namespaceName);

        var cache = cacheManager.getCache(CACHE_NAMESPACE_DETAILS_JSON);
        if (cache == null) {
            return; // cache is not created
        }

        cache.evictIfPresent(namespaceName);
    }

    public void evictExtensionJsons() {
        invalidateCache(CACHE_EXTENSION_JSON);
    }

    public void evictExtensionJsons(UserData user) {
        repositories.findExtensions(user).forEach(this::evictExtensionJsons);
    }

    public void evictExtensionJsons(Extension extension) {
        purgeFromCdn(extension);

        var cache = cacheManager.getCache(CACHE_EXTENSION_JSON);
        if (cache == null) {
            return; // cache is not created
        }
        if (extension.getVersions() == null) {
            return;
        }

        // Special optimization in case of a redis cache: evict all keys that match the <namespace>.<extension>* pattern.
        // This uses the redis KEYS command that might take a while but considering the typical size of the EXTENSION_JSON
        // cache its acceptable.
        if (cache instanceof RedisCacheWriter redisCache) {
            redisCache.clear(CACHE_EXTENSION_JSON, extensionJsonCacheKey.generateWildcard(extension).getBytes());
            return;
        }

        var versions = new ArrayList<>(VersionAlias.ALIAS_NAMES);
        extension.getVersions().stream()
                .map(ExtensionVersion::getVersion)
                .forEach(versions::add);

        var namespaceName = extension.getNamespace().getName();
        var extensionName = extension.getName();
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
        purgeFromCdn(extVersion.getExtension());

        var cache = cacheManager.getCache(CACHE_EXTENSION_JSON);
        if (cache == null) {
            return; // cache is not created
        }

        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var versions = new ArrayList<>(List.of(VersionAlias.LATEST, extVersion.getVersion()));
        if (extVersion.isPreRelease()) {
            versions.add(VersionAlias.PRE_RELEASE);
        }
        if (extVersion.isPreview()) {
            versions.add(VersionAlias.PREVIEW);
        }
        for (var version : versions) {
            cache.evictIfPresent(
                    extensionJsonCacheKey.generate(
                            namespace.getName(),
                            extension.getName(),
                            extVersion.getTargetPlatform(),
                            version));
        }
    }

    public void evictLatestExtensionVersions() {
        invalidateCache(CACHE_LATEST_EXTENSION_VERSION);
        invalidateCache(CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM);
        invalidateCache(CACHE_LATEST_EXTENSION_VERSION_VSCODE);
    }

    public void evictLatestExtensionVersion(Extension extension) {
        purgeFromCdn(extension);

        evictInternalLatestExtensionVersion(extension);
        evictInternalLatestExtensionVersionsByPlatform(extension);
        evictInternalLatestExtensionVersionVSCode(extension);
    }

    private void evictInternalLatestExtensionVersion(Extension extension) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION);
        if (cache == null) {
            return;
        }

        // Special optimization in case of a redis cache: evict all keys that match the <namespace>.<extension>* pattern.
        // This uses the redis KEYS command that might take a while but considering the typical size of the EXTENSION_JSON
        // cache its acceptable.
        if (cache instanceof RedisCacheWriter redisCache) {
            redisCache.clear(
                    CACHE_LATEST_EXTENSION_VERSION,
                    latestExtensionVersionCacheKey.generateWildcard(extension).getBytes());
            return;
        }

        var targetPlatforms = new ArrayList<>(TargetPlatform.TARGET_PLATFORM_NAMES);
        targetPlatforms.add(null);
        for (var targetPlatform : targetPlatforms) {
            for (var preRelease : List.of(true, false)) {
                for (var onlyActive : List.of(true, false)) {
                    for (var type : ExtensionVersion.Type.values()) {
                        var key = latestExtensionVersionCacheKey
                                .generate(extension, targetPlatform, preRelease, onlyActive, type);
                        cache.evictIfPresent(key);
                    }
                }
            }
        }
    }

    private void evictInternalLatestExtensionVersionsByPlatform(Extension extension) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM);
        if (cache == null) {
            return;
        }

        for (var preRelease : List.of(true, false)) {
            var key = latestExtensionVersionsByPlatformCacheKeyGenerator.generate(extension, preRelease);
            cache.evictIfPresent(key);
        }
    }

    private void evictInternalLatestExtensionVersionVSCode(Extension extension) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION_VSCODE);
        if (cache == null) {
            return;
        }

        var key = new SimpleKey(extension.getNamespace().getName(), extension.getName());
        cache.evictIfPresent(key);
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
