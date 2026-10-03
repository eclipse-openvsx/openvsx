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

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;
import org.eclipse.openvsx.repositories.RepositoryService;

@Service
@CacheConfig(cacheManager = "localCacheManager")
public class ExtensionSizeLimitService {

    private static final String CACHE_KEY_CEILING = "'extension-size-ceiling'";

    private final SettingsService settings;
    private final ExtensionSizeOverrideRepository overrides;
    private final RepositoryService repositories;

    public ExtensionSizeLimitService(
            SettingsService settings,
            ExtensionSizeOverrideRepository overrides,
            RepositoryService repositories
    ) {
        this.settings = settings;
        this.overrides = overrides;
        this.repositories = repositories;
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
}
