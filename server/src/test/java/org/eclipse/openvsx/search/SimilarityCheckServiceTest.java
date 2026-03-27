/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.search;

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.PublishCheck;
import org.eclipse.openvsx.util.TempFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for SimilarityCheckService which handles enforcement policy.
 */
@ExtendWith(MockitoExtension.class)
class SimilarityCheckServiceTest {

    @Mock
    SimilarityConfig config;

    @Mock
    SimilarityService similarityService;

    @Mock
    RepositoryService repositories;

    @InjectMocks
    SimilarityCheckService similarityCheckService;

    @Mock
    TempFile extensionFile;

    UserData user;

    @BeforeEach
    void setUp() {
        user = new UserData();
        user.setLoginName("testuser");
    }

    /** Helper to create a ValidationCheck.Context for testing check() method */
    private PublishCheck.Context createContext(String namespaceName, String extensionName, String displayName) {
        var scan = new ExtensionScan();
        scan.setNamespaceName(namespaceName);
        scan.setExtensionName(extensionName);
        scan.setExtensionDisplayName(displayName);
        return new PublishCheck.Context(scan, extensionFile, user);
    }

    @Test
    void isEnabled_shouldDelegateToConfig() {
        when(config.isEnabled()).thenReturn(true);
        assertThat(similarityCheckService.isEnabled()).isTrue();

        when(config.isEnabled()).thenReturn(false);
        assertThat(similarityCheckService.isEnabled()).isFalse();
    }

    @Test
    void shouldExcludeOwnerNamespacesWhenConfigured() {
        // When allow-similarity-to-own-names is enabled, we should build a list of owner namespaces to exclude.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(true);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);

        var namespace1 = new Namespace();
        namespace1.setName("owned-ns");
        var membership1 = new NamespaceMembership();
        membership1.setNamespace(namespace1);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);

        var namespace2 = new Namespace();
        namespace2.setName("contributor-ns");
        var membership2 = new NamespaceMembership();
        membership2.setNamespace(namespace2);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);

        when(repositories.findMemberships(user)).thenReturn(Streamable.of(membership1, membership2));
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of("owned-ns", "contributor-ns"), 0.15, false, 10))
                .thenReturn(List.of());

        var result = similarityCheckService.findSimilarExtensionsForPublishing(
            "ext", "ns", "Display", user
        );

        assertThat(result).isEmpty();
        verify(repositories).findMemberships(user);
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of("owned-ns", "contributor-ns"), 0.15, false, 10);
    }

    @Test
    void shouldNotExcludeNamespacesWhenConfigDisabled() {
        // When allow-similarity-to-own-names is disabled, pass an empty exclude list.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10))
                .thenReturn(List.of());

        var result = similarityCheckService.findSimilarExtensionsForPublishing(
            "ext", "ns", "Display", user
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(repositories);
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10);
    }

    @Test
    void shouldDelegateSimilarExtensionsToService() {
        // Happy path: delegate to SimilarityService with proper parameters.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);
        var expected = List.of(new Extension());
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10))
                .thenReturn(expected);

        var result = similarityCheckService.findSimilarExtensionsForPublishing(
            "ext", "ns", "Display", user
        );

        assertThat(result).isSameAs(expected);
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10);
    }

    @Test
    void shouldSkipCheckForExistingExtensionWhenConfiguredForNewOnly() {
        // When configured for new extensions only, skip if extension already has versions (>1 means existing).
        when(config.isOnlyCheckNewExtensions()).thenReturn(true);
        when(repositories.countVersions("ns", "ext")).thenReturn(2);

        var context = createContext("ns", "ext", "Display");
        var result = similarityCheckService.check(context);

        assertThat(result.passed()).isTrue();
        verify(repositories).countVersions("ns", "ext");
        verifyNoInteractions(similarityService);
    }

    @Test
    void shouldCheckNewExtensionEvenWhenConfiguredForNewOnly() {
        // When configured for new extensions only, still check if extension has 0 or 1 version.
        when(config.isOnlyCheckNewExtensions()).thenReturn(true);
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);
        when(repositories.countVersions("ns", "ext")).thenReturn(0);
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10))
                .thenReturn(List.of());

        var context = createContext("ns", "ext", "Display");
        var result = similarityCheckService.check(context);

        assertThat(result.passed()).isTrue();
        verify(repositories).countVersions("ns", "ext");
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10);
    }

    @Test
    void shouldSkipCheckForVerifiedPublisherWhenConfigured() {
        // When configured to skip verified publishers, check if namespace has owner memberships.
        when(config.isOnlyCheckNewExtensions()).thenReturn(false);
        when(config.isSkipIfPublisherVerified()).thenReturn(true);
        var namespace = new Namespace();
        when(repositories.findNamespace("ns")).thenReturn(namespace);
        when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER)).thenReturn(true);

        var context = createContext("ns", "ext", "Display");
        var result = similarityCheckService.check(context);

        assertThat(result.passed()).isTrue();
        verify(repositories).findNamespace("ns");
        verify(repositories).hasMemberships(namespace, NamespaceMembership.ROLE_OWNER);
        verifyNoInteractions(similarityService);
    }

    @Test
    void shouldCheckVerifiedPublisherWhenSkipIsDisabled() {
        // When skip verified publishers is disabled, check even if namespace has owner memberships.
        when(config.isOnlyCheckNewExtensions()).thenReturn(false);
        when(config.isSkipIfPublisherVerified()).thenReturn(false);
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10))
                .thenReturn(List.of());

        var context = createContext("ns", "ext", "Display");
        var result = similarityCheckService.check(context);

        assertThat(result.passed()).isTrue();
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10);
    }

    @Test
    void shouldPassConfiguredThresholdAndVerifiedOnlyFlag() {
        // Verify that config values are correctly passed to SimilarityService.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.25);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(true);
        when(similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.25, true, 10))
                .thenReturn(List.of());

        var result = similarityCheckService.findSimilarExtensionsForPublishing(
            "ext", "ns", "Display", user
        );

        assertThat(result).isEmpty();
        verify(similarityService).findSimilarExtensions("ext", "ns", "Display", List.of(), 0.25, true, 10);
    }

    @Test
    void shouldDelegateSimilarNamespacesToService() {
        // Delegate to SimilarityService with config parameters.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(false);
        when(config.getSimilarityThreshold()).thenReturn(0.15);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(false);
        var expected = List.of(new Namespace());
        when(similarityService.findSimilarNamespaces("ns", List.of(), 0.15, false, 10))
                .thenReturn(expected);

        var result = similarityCheckService.findSimilarNamespacesForCreation("ns", user);

        assertThat(result).isSameAs(expected);
        verify(similarityService).findSimilarNamespaces("ns", List.of(), 0.15, false, 10);
    }

    @Test
    void shouldExcludeOwnerNamespacesForNamespaceCreation() {
        // When allow-similarity-to-own-names is enabled for namespace creation.
        when(config.isAllowSimilarityToOwnNames()).thenReturn(true);
        when(config.getSimilarityThreshold()).thenReturn(0.2);
        when(config.isOnlyProtectVerifiedNames()).thenReturn(true);

        var namespace1 = new Namespace();
        namespace1.setName("owned-ns");
        var membership1 = new NamespaceMembership();
        membership1.setNamespace(namespace1);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);

        when(repositories.findMemberships(user)).thenReturn(Streamable.of(membership1));
        when(similarityService.findSimilarNamespaces("ns", List.of("owned-ns"), 0.2, true, 10))
                .thenReturn(List.of());

        var result = similarityCheckService.findSimilarNamespacesForCreation("ns", user);

        assertThat(result).isEmpty();
        verify(repositories).findMemberships(user);
        verify(similarityService).findSimilarNamespaces("ns", List.of("owned-ns"), 0.2, true, 10);
    }
}

