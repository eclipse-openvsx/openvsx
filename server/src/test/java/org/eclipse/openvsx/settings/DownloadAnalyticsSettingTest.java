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

import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.openvsx.settings.DownloadAnalyticsSetting.KEY;

class DownloadAnalyticsSettingTest {

    private final DownloadAnalyticsSetting configured = new DownloadAnalyticsSetting(true);
    private final DownloadAnalyticsSetting unconfigured = new DownloadAnalyticsSetting(false);

    private static SettingRows rows(Object... keyValues) {
        return keyValues.length == 0
                ? new SettingRows(Map.of())
                : new SettingRows(Map.of((String) keyValues[0], keyValues[1]));
    }

    @Test
    void anEmptyStoreMeansOff() {
        assertThat(configured.read(rows())).isFalse();
    }

    @Test
    void theStoredSwitchIsRead() {
        assertThat(configured.read(rows(KEY, true))).isTrue();
    }

    @Test
    void anUpdateWithoutTheSwitchLeavesItAlone() {
        assertThat(configured.merge(true, rows(ReadOnlySetting.KEY, true))).isTrue();
    }

    @Test
    void itStoresItselfUnderItsKey() {
        assertThat(configured.toRows(true)).containsExactly(Map.entry(KEY, true));
    }

    @Test
    void switchingOnNeedsAnalyticsConfigured() {
        assertThatThrownBy(() -> unconfigured.validate(true, false))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("ovsx.analytics.enabled");
        assertThatCode(() -> configured.validate(true, false)).doesNotThrowAnyException();
    }

    @Test
    void aSwitchAlreadyOnDoesNotBlockSavingWhereAnalyticsWentAway() {
        assertThatCode(() -> unconfigured.validate(true, true)).doesNotThrowAnyException();
        assertThatCode(() -> unconfigured.validate(false, true)).doesNotThrowAnyException();
    }

    @Test
    void thePublicViewIsTheEffectiveState() {
        assertThat(configured.publicView(rows())).containsExactly(Map.entry(KEY, false));
        assertThat(configured.publicView(rows(KEY, true))).containsExactly(Map.entry(KEY, true));
        assertThat(unconfigured.publicView(rows(KEY, true))).containsExactly(Map.entry(KEY, false));
    }
}
