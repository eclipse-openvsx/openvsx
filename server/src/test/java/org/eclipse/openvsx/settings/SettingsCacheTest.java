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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.eclipse.openvsx.entities.Setting;
import org.eclipse.openvsx.repositories.SettingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The store is jsonb, so a value's JSON type is what tells a setting whether it read a flag or a
 * string. These are the decoding and encoding rules every setting depends on.
 */
class SettingsCacheTest {

    private final SettingRepository repository = mock(SettingRepository.class);
    private final SettingsCache cache = new SettingsCache(repository);

    private static Setting setting(String key, String json) {
        var stored = mock(Setting.class);
        when(stored.getKey()).thenReturn(key);
        when(stored.getValue()).thenReturn(json);
        return stored;
    }

    /** Stubbing the rows has to finish before {@code findAll} is stubbed with them. */
    private void stored(Setting... settings) {
        when(repository.findAll()).thenReturn(List.of(settings));
    }

    @Test
    void valuesKeepTheirStoredJsonType() {
        stored(setting("read-only", "true"), setting("banner-message", "\"Maintenance tonight\""));

        var rows = cache.snapshot().rows();

        assertThat(rows).containsEntry("read-only", true);
        assertThat(rows).containsEntry("banner-message", "Maintenance tonight");
    }

    @Test
    void aStoredJsonNullIsSkipped() {
        // No setting is meaningfully absent-but-present, so a null row reads as no row at all.
        stored(setting("read-only", "null"));

        assertThat(cache.snapshot().rows()).isEmpty();
    }

    @Test
    void theSnapshotCannotBeMutatedByItsReaders() {
        stored(setting("read-only", "true"));

        assertThat(cache.snapshot().rows()).isUnmodifiable();
    }

    @Test
    void writingEncodesTheValueAsJson() {
        cache.set("banner-message", "Maintenance tonight");

        var value = ArgumentCaptor.forClass(String.class);
        verify(repository).upsert(eq("banner-message"), value.capture(), any());
        assertThat(value.getValue()).isEqualTo("\"Maintenance tonight\"");
    }
}
