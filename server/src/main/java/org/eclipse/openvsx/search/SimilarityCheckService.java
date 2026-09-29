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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.PublishCheck;

/**
 * Central entry point for enforcing similarity rules with configuration-based policy.
 *
 * This service reads {@link SimilarityConfig} and applies all policy decisions.
 * Implements ValidationCheck to be auto-discovered by ExtensionScanService.
 */
@Service
@Order(1) // Run first: name squatting check
@ConditionalOnProperty(name = "ovsx.scanning.similarity.enabled", havingValue = "true")
public class SimilarityCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "NAME_SQUATTING";

    private static final int LIMIT = 10;

    private final SimilarityConfig config;
    private final SimilarityService similarityService;
    private final RepositoryService repositories;

    public SimilarityCheckService(
            SimilarityConfig config,
            SimilarityService similarityService,
            RepositoryService repositories
    ) {
        this.config = config;
        this.similarityService = similarityService;
        this.repositories = repositories;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public boolean isEnforced() {
        return config.isEnforced();
    }

    @Override
    public boolean isRequired() {
        return config.isRequired();
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public PublishCheck.Result check(PublishCheck.Context context) {
        var scan = context.scan();
        var namespaceName = scan.getNamespaceName();
        var extensionName = scan.getExtensionName();
        var displayName = scan.getExtensionDisplayName();

        // if at least a version exists for that extension (regardless of active state),
        // do not consider it as a new extension anymore
        if (config.isOnlyCheckNewExtensions() && repositories.countVersions(namespaceName, extensionName) > 0) {
            return PublishCheck.Result.pass();
        }

        if (config.isSkipIfPublisherVerified()) {
            var namespace = repositories.findNamespace(namespaceName);
            if (namespace != null && repositories.isVerified(namespace)) {
                return PublishCheck.Result.pass();
            }
        }

        var similarExtensions = findSimilarExtensionsForPublishing(
                extensionName,
                namespaceName,
                displayName,
                context.user());

        if (similarExtensions.isEmpty()) {
            return PublishCheck.Result.pass();
        }

        var similarExt = similarExtensions.getFirst();
        var latestVersion = repositories.findLatestVersion(similarExt, null, false, true);
        String similarDisplayName = latestVersion != null ? latestVersion.getDisplayName() : null;

        var reason = String.format(
                "Extension '%s.%s' (display name: '%s') is too similar to existing extension '%s.%s' (display name: '%s')",
                namespaceName,
                extensionName,
                displayName,
                similarExt.getNamespace().getName(),
                similarExt.getName(),
                similarDisplayName != null ? similarDisplayName : "");

        return PublishCheck.Result.fail("Levenshtein Distance", reason);
    }

    /**
     * Enforce configured similarity rules for publishing an extension.
     * Callers should check {@link #isEnabled()} before invoking this method.
     */
    public List<Extension> findSimilarExtensionsForPublishing(
            @Nullable String extensionName,
            @Nullable String namespaceName,
            @Nullable String displayName,
            @NonNull UserData publishingUser
    ) {
        return similarityService.findSimilarExtensions(
                extensionName,
                namespaceName,
                displayName,
                getExcludedNamespaces(publishingUser),
                config.getSimilarityThreshold(),
                config.isOnlyProtectVerifiedNames(),
                LIMIT);
    }

    /**
     * Enforce configured similarity rules for namespace creation.
     * Callers should check {@link #isEnabled()} before invoking this method.
     */
    public List<Namespace> findSimilarNamespacesForCreation(
            @NonNull String namespaceName,
            @NonNull UserData publishingUser
    ) {
        return similarityService.findSimilarNamespaces(
                namespaceName,
                getExcludedNamespaces(publishingUser),
                config.getSimilarityThreshold(),
                config.isOnlyProtectVerifiedNames(),
                LIMIT);
    }

    /**
     * Get the list of namespaces to exclude from similarity checks.
     * When configured, excludes namespaces where the user is a member (owner OR contributor).
     * This prevents false positives when uploading to namespaces the user legitimately controls.
     */
    private List<String> getExcludedNamespaces(@NonNull UserData user) {
        if (!config.isAllowSimilarityToOwnNames()) {
            return List.of();
        }
        // Exclude ALL namespaces where user is a member (owner or contributor)
        // Both roles should be able to upload new versions without similarity blocking
        return repositories.findMemberships(user)
                .stream()
                .map(m -> m.getNamespace().getName())
                .toList();
    }
}
