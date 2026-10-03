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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.settings.ReadOnlySetting.KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadOnlySettingTest {

    private final SettingsCache cache = mock(SettingsCache.class);
    private final ReadOnlySetting readOnly = new ReadOnlySetting(cache);

    private static SettingRows rows(Object... keyValues) {
        return keyValues.length == 0
                ? new SettingRows(Map.of())
                : new SettingRows(Map.of((String) keyValues[0], keyValues[1]));
    }

    @Test
    void anEmptyStoreMeansWritable() {
        assertThat(readOnly.read(rows())).isFalse();
    }

    @Test
    void theStoredFlagIsRead() {
        assertThat(readOnly.read(rows(KEY, true))).isTrue();
    }

    @Test
    void anUpdateWithoutTheFlagLeavesItAlone() {
        assertThat(readOnly.merge(true, rows(BannerSetting.KEY_MESSAGE, "Maintenance tonight"))).isTrue();
    }

    @Test
    void itStoresItselfUnderItsBareLegacyKey() {
        assertThat(readOnly.toRows(true)).containsExactly(Map.entry(KEY, true));
    }

    @Test
    void theAccessorReadsThroughTheCache() {
        when(cache.snapshot()).thenReturn(rows(KEY, true));

        assertThat(readOnly.isEnabled()).isTrue();
    }
}
