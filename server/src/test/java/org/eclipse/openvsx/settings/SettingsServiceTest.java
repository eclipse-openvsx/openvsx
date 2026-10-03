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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private SettingsCache cache;
    private PublishingConfig publishingConfig;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        cache = Mockito.mock(SettingsCache.class);
        publishingConfig = Mockito.mock(PublishingConfig.class);
        // isReadOnly() unboxes SettingsCache#getBoolean's Boolean return value; an unstubbed mock
        // would hand back null here and NPE on every call that reaches it.
        when(cache.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        settings = new SettingsService(null, cache, publishingConfig);
    }

    @Test
    void getMaxExtensionSizeFallsBackToPublishingConfigWhenNoOverrideStored() {
        when(publishingConfig.getMaxContentSize()).thenReturn(512L * 1024 * 1024);
        // SettingsCache#getLong returns its own defaultValue argument when no row exists;
        // simulate that by echoing it back.
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        assertThat(settings.getMaxExtensionSize()).isEqualTo(512L * 1024 * 1024);
    }

    @Test
    void getMaxExtensionSizeReturnsStoredOverrideWhenPresent() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(1024L * 1024 * 1024);

        assertThat(settings.getMaxExtensionSize()).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    void updateFromJsonStoresNewMaxExtensionSizeAndReportsTheChange() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(1024L * 1024 * 1024);

        var changes = settings.updateFromJson(newSettings);

        verify(cache).setLong(SettingsService.SETTING_MAX_EXTENSION_SIZE, 1024L * 1024 * 1024);
        assertThat(changes).contains("maxExtensionSize -> 1073741824");
    }

    @Test
    void updateFromJsonSkipsWritingWhenMaxExtensionSizeIsUnchanged() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(512L * 1024 * 1024);

        var changes = settings.updateFromJson(newSettings);

        verify(cache, Mockito.never()).setLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong());
        assertThat(changes).doesNotContain("maxExtensionSize");
    }

    @Test
    void updateFromJsonRejectsNonPositiveMaxExtensionSize() {
        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(0);

        assertThatThrownBy(() -> settings.updateFromJson(newSettings)).isInstanceOf(ErrorResultException.class);
    }
}
