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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for SimilarityService - the pure computation engine.
 * <p>
 * These tests verify the core similarity calculation logic without configuration.
 * For configuration-based policy tests, see {@link SimilarityCheckServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class SimilarityServiceTest {

    @Mock
    RepositoryService repositories;

    @InjectMocks
    SimilarityService similarityService;

    @Test
    void shouldReturnEmptyWhenNoInputProvided() {
        // When no extension name, namespace, or display name is provided, return empty.
        var result = similarityService.findSimilarExtensions(null, null, null, List.of(), 0.15, false, 10);

        assertThat(result).isEmpty();
        verifyNoInteractions(repositories);
    }

    @Test
    void shouldDelegateToRepositoryWithProvidedParameters() {
        // Happy path: delegates to repository with the provided threshold and verifiedOnly flag.
        var expected = List.of(new Extension());
        when(repositories.findSimilarExtensionsByLevenshtein("ext", "ns", "Display", List.of(), 0.2, true, 10))
                .thenReturn(expected);

        var result = similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.2, true, 10);

        assertThat(result).isSameAs(expected);
        verify(repositories).findSimilarExtensionsByLevenshtein("ext", "ns", "Display", List.of(), 0.2, true, 10);
    }

    @Test
    void shouldWrapRepositoryErrorsWhenCheckingExtensions() {
        // Repository failures should be wrapped with a user-friendly runtime error.
        when(
                repositories.findSimilarExtensionsByLevenshtein(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyDouble(),
                        anyBoolean(),
                        anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(
                () -> similarityService.findSimilarExtensions("ext", "ns", "Display", List.of(), 0.15, false, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to verify extension name uniqueness");
    }

    @Test
    void shouldDelegateNamespaceSimilarityWithProvidedParameters() {
        // Happy path: delegates namespace similarity search with provided parameters.
        var existing = new Namespace();
        when(repositories.findSimilarNamespacesByLevenshtein("ns", List.of("skip"), 0.1, true, 10))
                .thenReturn(List.of(existing));

        var result = similarityService.findSimilarNamespaces("ns", List.of("skip"), 0.1, true, 10);

        assertThat(result).containsExactly(existing);
        verify(repositories).findSimilarNamespacesByLevenshtein("ns", List.of("skip"), 0.1, true, 10);
    }

    @Test
    void shouldReturnEmptyForEmptyNamespaceInput() {
        // Empty namespace names should not trigger database work.
        var result = similarityService.findSimilarNamespaces("", List.of(), 0.15, false, 10);

        assertThat(result).isEmpty();
        verifyNoInteractions(repositories);
    }

    @Test
    void shouldWrapRepositoryErrorsWhenCheckingNamespaces() {
        // Repository failures should be wrapped with a user-friendly runtime error.
        when(repositories.findSimilarNamespacesByLevenshtein(any(), any(), anyDouble(), anyBoolean(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> similarityService.findSimilarNamespaces("ns", List.of(), 0.15, false, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to verify namespace name uniqueness");
    }
}
