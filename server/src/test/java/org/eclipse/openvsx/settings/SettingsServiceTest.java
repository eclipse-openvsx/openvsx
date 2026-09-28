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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.openvsx.settings.SettingsService.SETTING_BANNER_DISMISS_ID;
import static org.eclipse.openvsx.settings.SettingsService.SETTING_BANNER_ENABLED;
import static org.eclipse.openvsx.settings.SettingsService.SETTING_BANNER_MESSAGE;
import static org.eclipse.openvsx.settings.SettingsService.SETTING_BANNER_SEVERITY;
import static org.eclipse.openvsx.settings.SettingsService.SETTING_REGISTRY_READ_ONLY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private final SettingsCache cache = mock(SettingsCache.class);
    private final SettingsService settings = new SettingsService(null, cache);

    private void stored(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        when(cache.getAll()).thenReturn(map);
    }

    /** Builds an update the way a client does: only the settings it means to change. */
    private static SettingsJson update(Object... keyValues) {
        var json = new SettingsJson();
        for (var i = 0; i < keyValues.length; i += 2) {
            var key = (String) keyValues[i];
            var value = keyValues[i + 1];
            switch (key) {
                case SETTING_REGISTRY_READ_ONLY -> json.setReadOnly((Boolean) value);
                case SETTING_BANNER_ENABLED -> json.setBannerEnabled((Boolean) value);
                case SETTING_BANNER_MESSAGE -> json.setBannerMessage((String) value);
                case SETTING_BANNER_SEVERITY -> json.setBannerSeverity((String) value);
                case SETTING_BANNER_DISMISS_ID -> json.setBannerDismissId((String) value);
                default -> throw new IllegalArgumentException("unknown setting " + key);
            }
        }
        return json;
    }

    @Test
    void siteSettingsHoldBackReadOnlyMode() {
        stored(SETTING_REGISTRY_READ_ONLY, true, SETTING_BANNER_ENABLED, true, SETTING_BANNER_MESSAGE, "Hi");

        assertThat(settings.getSiteSettings()).doesNotContainKey(SETTING_REGISTRY_READ_ONLY);
        assertThat(settings.getSiteSettings()).containsEntry(SETTING_BANNER_MESSAGE, "Hi");
    }

    @Test
    void siteSettingsHoldBackADraftedBanner() {
        stored(SETTING_BANNER_ENABLED, false, SETTING_BANNER_MESSAGE, "Security incident");

        assertThat(settings.getSiteSettings()).isEmpty();
    }

    @Test
    void theAdminViewKeepsADraftedBanner() {
        stored(SETTING_REGISTRY_READ_ONLY, true, SETTING_BANNER_MESSAGE, "Security incident");

        var json = settings.getCurrentSettings();
        assertThat(json.isReadOnly()).isTrue();
        assertThat(json.getBannerMessage()).isEqualTo("Security incident");
    }

    @Test
    void aSettingLeftOutOfTheRequestIsNotTouched() {
        stored(SETTING_REGISTRY_READ_ONLY, true);

        settings.updateFromJson(update(SETTING_BANNER_MESSAGE, "Maintenance tonight"));

        verify(cache, never()).set(eq(SETTING_REGISTRY_READ_ONLY), any());
    }

    @Test
    void updateWritesOnlyTheKeysThatChanged() {
        stored(SETTING_BANNER_ENABLED, true, SETTING_BANNER_MESSAGE, "tonigth", SETTING_BANNER_DISMISS_ID, "token-1");

        settings.updateFromJson(
                update(
                        SETTING_BANNER_ENABLED,
                        true,
                        SETTING_BANNER_MESSAGE,
                        "tonight",
                        SETTING_BANNER_DISMISS_ID,
                        "token-1"));

        verify(cache).set(SETTING_BANNER_MESSAGE, "tonight");
        verify(cache, never()).set(eq(SETTING_BANNER_ENABLED), any());
        verify(cache, never()).set(eq(SETTING_BANNER_DISMISS_ID), any());
    }

    @Test
    void enablingABannerWritesTheSwitchAfterTheMessage() {
        // Otherwise a reader between the two writes sees the switch on beside the drafted message.
        stored(SETTING_BANNER_ENABLED, false, SETTING_BANNER_MESSAGE, "Drafted", SETTING_BANNER_DISMISS_ID, "token-1");

        settings.updateFromJson(
                update(
                        SETTING_BANNER_ENABLED,
                        true,
                        SETTING_BANNER_MESSAGE,
                        "Published",
                        SETTING_BANNER_DISMISS_ID,
                        "token-1"));

        InOrder order = Mockito.inOrder(cache);
        order.verify(cache).set(SETTING_BANNER_MESSAGE, "Published");
        order.verify(cache).set(SETTING_BANNER_ENABLED, true);
    }

    @Test
    void disablingABannerWritesTheSwitchFirst() {
        stored(SETTING_BANNER_ENABLED, true, SETTING_BANNER_MESSAGE, "Published", SETTING_BANNER_DISMISS_ID, "token-1");

        settings.updateFromJson(
                update(
                        SETTING_BANNER_ENABLED,
                        false,
                        SETTING_BANNER_MESSAGE,
                        "Drafted next one",
                        SETTING_BANNER_DISMISS_ID,
                        "token-1"));

        InOrder order = Mockito.inOrder(cache);
        order.verify(cache).set(SETTING_BANNER_ENABLED, false);
        order.verify(cache).set(SETTING_BANNER_MESSAGE, "Drafted next one");
    }

    @Test
    void aRejectedValueLeavesNothingWritten() {
        stored(SETTING_REGISTRY_READ_ONLY, false);

        assertThatThrownBy(
                () -> settings.updateFromJson(
                        update(SETTING_REGISTRY_READ_ONLY, true, SETTING_BANNER_MESSAGE, "<script>alert(1)</script>")))
                .isInstanceOf(ErrorResultException.class);

        verify(cache, never()).set(any(), any());
    }

    @Test
    void aCorrectedMessageKeepsTheDismissToken() {
        stored(SETTING_BANNER_MESSAGE, "tonigth", SETTING_BANNER_DISMISS_ID, "token-1");

        // A client that doesn't know about the token can't reset dismissals by leaving it out.
        settings.updateFromJson(update(SETTING_BANNER_MESSAGE, "tonight"));

        verify(cache, never()).set(eq(SETTING_BANNER_DISMISS_ID), any());
    }

    @Test
    void aSuppliedDismissTokenIsStoredAsGiven() {
        stored(SETTING_BANNER_MESSAGE, "tonight", SETTING_BANNER_DISMISS_ID, "token-1");

        settings.updateFromJson(update(SETTING_BANNER_MESSAGE, "tonight", SETTING_BANNER_DISMISS_ID, "token-2"));

        verify(cache).set(SETTING_BANNER_DISMISS_ID, "token-2");
    }

    @Test
    void aBannerReplacingAClearedOneGetsAFreshToken() {
        // The admin page echoes the stored token back, so reusing it would hide the new banner
        // from everyone who dismissed the old one.
        stored(SETTING_BANNER_MESSAGE, "", SETTING_BANNER_DISMISS_ID, "token-1");

        settings.updateFromJson(update(SETTING_BANNER_MESSAGE, "Something new", SETTING_BANNER_DISMISS_ID, "token-1"));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(cache).set(eq(SETTING_BANNER_DISMISS_ID), captor.capture());
        assertThat(captor.getValue()).isNotBlank().isNotEqualTo("token-1");
    }

    @Test
    void anEmptyBannerMintsNoDismissTokenAndWritesNoMessage() {
        stored();

        settings.updateFromJson(update(SETTING_BANNER_MESSAGE, "", SETTING_BANNER_SEVERITY, "info"));

        verify(cache, never()).set(eq(SETTING_BANNER_DISMISS_ID), any());
        verify(cache, never()).set(eq(SETTING_BANNER_MESSAGE), any());
    }

    @Test
    void readOnlyModeReadsTheStoredFlag() {
        stored(SETTING_REGISTRY_READ_ONLY, true);
        assertThat(settings.isReadOnly()).isTrue();

        stored();
        assertThat(settings.isReadOnly()).isFalse();
    }
}
