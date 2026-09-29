/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.search;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

@Service
public class SimilarityService {
    private static final Logger logger = LoggerFactory.getLogger(SimilarityService.class);

    private final RepositoryService repositories;

    public SimilarityService(RepositoryService repositories) {
        this.repositories = repositories;
    }

    /**
     * Find extensions similar to the given fields using Levenshtein distance.
     */
    public List<Extension> findSimilarExtensions(
            @Nullable String extensionName,
            @Nullable String namespaceName,
            @Nullable String displayName,
            @NonNull List<String> excludeNamespaces,
            double threshold,
            boolean verifiedOnly,
            int limit
    ) {

        if (extensionName == null && namespaceName == null && displayName == null) {
            return List.of();
        }

        try {
            return repositories.findSimilarExtensionsByLevenshtein(
                    extensionName,
                    namespaceName,
                    displayName,
                    excludeNamespaces,
                    threshold,
                    verifiedOnly,
                    limit);
        } catch (Exception e) {
            logger.error(
                    "Similarity check failed for extension='{}', namespace='{}', displayName='{}': {}",
                    extensionName,
                    namespaceName,
                    displayName,
                    e.getMessage(),
                    e);

            throw new ErrorResultException(
                    "Unable to verify extension name uniqueness due to system error. " +
                            "Please try again later or contact support if the problem persists.");
        }
    }

    /**
     * Find namespaces similar to the given namespace name using Levenshtein distance.
     */
    public List<Namespace> findSimilarNamespaces(
            @NonNull String namespaceName,
            @NonNull List<String> excludeNamespaces,
            double threshold,
            boolean verifiedOnly,
            int limit
    ) {

        if (namespaceName.isEmpty()) {
            return List.of();
        }

        try {
            return repositories.findSimilarNamespacesByLevenshtein(
                    namespaceName,
                    excludeNamespaces,
                    threshold,
                    verifiedOnly,
                    limit);
        } catch (Exception e) {
            logger.error(
                    "Similarity check failed for namespace='{}': {}",
                    namespaceName,
                    e.getMessage(),
                    e);

            throw new ErrorResultException(
                    "Unable to verify namespace name uniqueness due to system error. " +
                            "Please try again later or contact support if the problem persists.");
        }
    }
}
