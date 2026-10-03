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
package org.eclipse.openvsx.settings;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

@Service
@CacheConfig(cacheManager = "localCacheManager")
public class ExtensionSizeLimitService {

    private static final String CACHE_KEY_CEILING = "'extension-size-ceiling'";

    private final SettingsService settings;
    private final ExtensionSizeOverrideRepository overrides;
    private final RepositoryService repositories;
    private final PublishingConfig publishingConfig;

    public ExtensionSizeLimitService(
            SettingsService settings,
            ExtensionSizeOverrideRepository overrides,
            RepositoryService repositories,
            PublishingConfig publishingConfig
    ) {
        this.settings = settings;
        this.overrides = overrides;
        this.repositories = repositories;
        this.publishingConfig = publishingConfig;
    }

    public long getDefaultLimit() {
        return settings.getMaxExtensionSize();
    }

    /**
     * Largest package any namespace may publish. Used as the stream-time cap, before the namespace is
     * known. Evicted by {@link SettingsCache#clear()}, which every settings write triggers.
     */
    @Cacheable(value = CacheService.CACHE_SETTING, key = CACHE_KEY_CEILING)
    public long getCeiling() {
        var defaultLimit = getDefaultLimit();
        var highest = overrides.findHighestMaxSize();
        return highest == null ? defaultLimit : Math.max(defaultLimit, highest);
    }

    /** Applicable limit for one package: extension override, else namespace override, else default. */
    public long resolveLimit(String namespaceName, String extensionName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            return getDefaultLimit();
        }

        var extension = repositories.findExtension(extensionName, namespace);
        var extensionId = extension == null ? null : extension.getId();

        long namespaceWide = -1;
        for (var override : overrides.findByScope(namespace.getId(), extensionId)) {
            if (override.getScopeExtension() != null) {
                return override.getMaxSize();
            }
            namespaceWide = override.getMaxSize();
        }
        return namespaceWide >= 0 ? namespaceWide : getDefaultLimit();
    }

    public List<ExtensionSizeOverride> listOverrides() {
        return overrides.findAllByOrderByIdAsc();
    }

    public ExtensionSizeOverride createOverride(
            String namespaceName,
            @Nullable String extensionName,
            long maxSize
    ) {
        var namespace = requireNamespace(namespaceName);
        var extension = extensionName == null ? null : requireExtension(namespace, extensionName);
        requireValidSize(maxSize);

        var extensionId = extension == null ? null : extension.getId();
        for (var existing : overrides.findByScope(namespace.getId(), extensionId)) {
            var sameScope = extension == null
                    ? existing.getScopeExtension() == null
                    : existing.getScopeExtension() != null;
            if (sameScope) {
                throw new ErrorResultException(
                        "A size override already exists for " + describeScope(namespaceName, extensionName) + ".",
                        HttpStatus.BAD_REQUEST);
            }
        }

        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setScopeExtension(extension);
        override.setMaxSize(maxSize);
        var saved = overrides.save(override);
        settings.invalidateCache();
        return saved;
    }

    public ExtensionSizeOverride updateOverride(long id, long maxSize) {
        var override = requireOverride(id);
        requireValidSize(maxSize);
        override.setMaxSize(maxSize);
        var saved = overrides.save(override);
        settings.invalidateCache();
        return saved;
    }

    public ExtensionSizeOverride deleteOverride(long id) {
        var override = requireOverride(id);
        overrides.delete(override);
        settings.invalidateCache();
        return override;
    }

    private Namespace requireNamespace(String namespaceName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Unknown namespace: " + namespaceName, HttpStatus.BAD_REQUEST);
        }
        return namespace;
    }

    private Extension requireExtension(Namespace namespace, String extensionName) {
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null) {
            throw new ErrorResultException(
                    "Unknown extension: " + describeScope(namespace.getName(), extensionName),
                    HttpStatus.BAD_REQUEST);
        }
        return extension;
    }

    private ExtensionSizeOverride requireOverride(long id) {
        return overrides.findById(id)
                .orElseThrow(() -> new ErrorResultException("Unknown size override: " + id, HttpStatus.NOT_FOUND));
    }

    private void requireValidSize(long maxSize) {
        if (maxSize <= 0) {
            throw new ErrorResultException("The size override must be greater than zero.", HttpStatus.BAD_REQUEST);
        }
        var ceiling = publishingConfig.getMaxOverrideSize();
        if (maxSize > ceiling) {
            throw new ErrorResultException(
                    "The size override exceeds the maximum of " + ceiling + " bytes.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static String describeScope(String namespaceName, @Nullable String extensionName) {
        return extensionName == null ? namespaceName : namespaceName + "." + extensionName;
    }
}
