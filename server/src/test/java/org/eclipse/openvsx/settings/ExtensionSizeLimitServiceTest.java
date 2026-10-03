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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ExtensionSizeLimitServiceTest {

    private static final long DEFAULT_LIMIT = 512L * 1024 * 1024;

    private SettingsService settings;
    private ExtensionSizeOverrideRepository overrides;
    private RepositoryService repositories;
    private ExtensionSizeLimitService limits;

    @BeforeEach
    void setUp() {
        settings = Mockito.mock(SettingsService.class);
        overrides = Mockito.mock(ExtensionSizeOverrideRepository.class);
        repositories = Mockito.mock(RepositoryService.class);
        when(settings.getMaxExtensionSize()).thenReturn(DEFAULT_LIMIT);
        limits = new ExtensionSizeLimitService(settings, overrides, repositories);
    }

    @Test
    void ceilingIsTheDefaultWhenNoOverridesExist() {
        when(overrides.findHighestMaxSize()).thenReturn(null);

        assertThat(limits.getCeiling()).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void ceilingRisesToTheHighestOverride() {
        when(overrides.findHighestMaxSize()).thenReturn(2L * DEFAULT_LIMIT);

        assertThat(limits.getCeiling()).isEqualTo(2L * DEFAULT_LIMIT);
    }

    @Test
    void ceilingStaysAtTheDefaultWhenEveryOverrideIsSmaller() {
        when(overrides.findHighestMaxSize()).thenReturn(1024L);

        assertThat(limits.getCeiling()).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void resolveFallsBackToTheDefaultForAnUnknownNamespace() {
        when(repositories.findNamespace("nope")).thenReturn(null);

        assertThat(limits.resolveLimit("nope", "ext")).isEqualTo(DEFAULT_LIMIT);
    }

    @Test
    void resolveUsesTheNamespaceWideOverrideWhenNoExtensionOverrideExists() {
        var namespace = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(null);
        when(overrides.findByScope(eq(1L), eq(null))).thenReturn(List.of(override(null, 100L)));

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(100L);
    }

    @Test
    void resolvePrefersTheExtensionOverrideOverTheNamespaceOne() {
        var namespace = namespace("foo", 1L);
        var extension = extension(7L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(extension);
        when(overrides.findByScope(eq(1L), eq(7L)))
                .thenReturn(List.of(override(null, 100L), override(extension, 300L)));

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(300L);
    }

    @Test
    void resolveFallsBackToTheDefaultWhenTheNamespaceHasNoOverrides() {
        var namespace = namespace("foo", 1L);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(repositories.findExtension(eq("ext"), any(Namespace.class))).thenReturn(null);
        when(overrides.findByScope(anyLong(), eq(null))).thenReturn(List.of());

        assertThat(limits.resolveLimit("foo", "ext")).isEqualTo(DEFAULT_LIMIT);
    }

    private Namespace namespace(String name, long id) {
        var namespace = Mockito.mock(Namespace.class);
        when(namespace.getId()).thenReturn(id);
        when(namespace.getName()).thenReturn(name);
        return namespace;
    }

    private Extension extension(long id) {
        var extension = Mockito.mock(Extension.class);
        when(extension.getId()).thenReturn(id);
        return extension;
    }

    private ExtensionSizeOverride override(@Nullable Extension scopeExtension, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeExtension(scopeExtension);
        override.setMaxSize(maxSize);
        return override;
    }
}
