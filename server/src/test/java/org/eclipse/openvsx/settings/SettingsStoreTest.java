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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.eclipse.openvsx.entities.Setting;
import org.eclipse.openvsx.repositories.SettingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The store is jsonb, so a value's JSON type is what tells a setting whether it read a flag or a
 * string. These are the decoding and encoding rules every setting depends on.
 */
class SettingsStoreTest {

    private final SettingRepository repository = mock(SettingRepository.class);
    private final SettingsStore store = new SettingsStore(repository);

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
        stored(setting(ReadOnlySetting.KEY, "true"), setting(BannerSetting.KEY_MESSAGE, "\"Maintenance tonight\""));

        var rows = store.snapshot().rows();

        assertThat(rows).containsEntry(ReadOnlySetting.KEY, true);
        assertThat(rows).containsEntry(BannerSetting.KEY_MESSAGE, "Maintenance tonight");
    }

    @Test
    void aStoredJsonNullIsSkipped() {
        // No setting is meaningfully absent-but-present, so a null row reads as no row at all.
        stored(setting(ReadOnlySetting.KEY, "null"));

        assertThat(store.snapshot().rows()).isEmpty();
    }

    @Test
    void theSnapshotCannotBeMutatedByItsReaders() {
        stored(setting(ReadOnlySetting.KEY, "true"));

        assertThat(store.snapshot().rows()).isUnmodifiable();
    }

    @Test
    void writingEncodesTheValueAsJson() {
        store.setAll(Map.of(BannerSetting.KEY_MESSAGE, "Maintenance tonight"));

        var value = ArgumentCaptor.forClass(String.class);
        verify(repository).upsert(eq(BannerSetting.KEY_MESSAGE), value.capture(), any());
        assertThat(value.getValue()).isEqualTo("\"Maintenance tonight\"");
    }

    @Test
    void writingStoresEveryRowWithOneTimestamp() {
        store.setAll(Map.of(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "Hi"));

        var now = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(repository, times(2)).upsert(any(), any(), now.capture());
        assertThat(now.getAllValues()).containsOnly(now.getValue());
    }
}
