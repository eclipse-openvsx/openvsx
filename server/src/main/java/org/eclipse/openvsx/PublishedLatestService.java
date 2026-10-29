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
package org.eclipse.openvsx;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.VersionAlias;

@Service
public class PublishedLatestService {

    private final RepositoryService repositories;
    private final UpstreamRegistryService upstream;

    public PublishedLatestService(RepositoryService repositories, UpstreamRegistryService upstream) {
        this.repositories = repositories;
        this.upstream = upstream;
    }

    public @Nullable String findVersion(String namespace, String extension) {
        var local = repositories.findExtensionVersion(namespace, extension, null, VersionAlias.LATEST);
        if (local != null) {
            return local.getVersion();
        }
        if (upstream.isValid()) {
            try {
                var result = upstream.getExtension(namespace, extension, null);
                return result == null ? null : result.getVersion();
            } catch (NotFoundException ignored) {
                // Match the registry API when neither registry has a published version.
            }
        }
        return null;
    }
}
