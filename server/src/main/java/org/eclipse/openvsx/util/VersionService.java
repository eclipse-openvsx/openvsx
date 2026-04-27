/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.util;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.eclipse.openvsx.cache.CacheService.*;

@Service
public class VersionService {

    private final RepositoryService repositories;

    public VersionService(RepositoryService repositories) {
        this.repositories = repositories;
    }

    /**
     * This returns the latest version of an {@code Extension}, grouped by target platform.
     *
     * @param extension the extension to query
     * @param preReleases whether pre-release or regular versions should be considered, this is an exclusive or
     *                    parameter, either only pre-releases are considered or not at all
     * @return a list of the latest {@code ExtensionVersion} for the given {@code Extension} grouped by target platform
     */
    @Cacheable(value = CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM, keyGenerator = GENERATOR_LATEST_EXTENSION_VERSIONS_BY_PLATFORM)
    public List<ExtensionVersion> getLatestByTargetPlatform(Extension extension, boolean preReleases) {
        return repositories.findLatestVersionByTargetPlatform(extension, preReleases, true);
    }

    // groupedByTargetPlatform is used by cache key generator, don't remove this parameter
    @Cacheable(value = CACHE_LATEST_EXTENSION_VERSION, keyGenerator = GENERATOR_LATEST_EXTENSION_VERSION)
    public ExtensionVersion getLatest(List<ExtensionVersion> versions, boolean groupedByTargetPlatform) {
        return getLatest(versions, groupedByTargetPlatform, false);
    }

    // groupedByTargetPlatform is used by cache key generator, don't remove this parameter
    @Cacheable(value = CACHE_LATEST_EXTENSION_VERSION, keyGenerator = GENERATOR_LATEST_EXTENSION_VERSION)
    public ExtensionVersion getLatest(List<ExtensionVersion> versions, boolean groupedByTargetPlatform, boolean onlyPreRelease) {
        if(versions == null || versions.isEmpty()) {
            return null;
        }

        var stream = versions.stream();
        if(onlyPreRelease) {
            stream = stream.filter(ExtensionVersion::isPreRelease);
        }

        return stream.min(ExtensionVersion.SORT_COMPARATOR).orElse(null);
    }
}
