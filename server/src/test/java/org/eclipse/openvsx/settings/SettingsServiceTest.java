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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.util.AfterCommitExecutor;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private static final long MB = 1024L * 1024;

    private final SettingsCache cache = mock(SettingsCache.class);
    private final SettingsUpdateChannel channel = mock(SettingsUpdateChannel.class);
    private final ReadOnlySetting readOnly = new ReadOnlySetting(cache);
    private final PublishingConfig publishingConfig = mock(PublishingConfig.class);
    private final MaxExtensionSizeSetting maxExtensionSize = new MaxExtensionSizeSetting(cache, publishingConfig);
    private final SettingsService settings = new SettingsService(
            List.of(readOnly, maxExtensionSize, new BannerSetting()),
            readOnly,
            maxExtensionSize,
            publishingConfig,
            cache,
            channel,
            new AfterCommitExecutor());

    @BeforeEach
    void setUp() {
        when(publishingConfig.getMaxContentSize()).thenReturn(512L * MB);
        when(publishingConfig.getMaxOverrideSize()).thenReturn(1024L * MB);
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stored(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        when(cache.snapshot()).thenReturn(new SettingRows(map));
    }

    /** The rows the save handed to {@code setAll}; empty when it wrote nothing. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> written() {
        return mockingDetails(cache).getInvocations()
                .stream()
                .filter(call -> call.getMethod().getName().equals("setAll"))
                .findFirst()
                .map(call -> (Map<String, Object>) call.getArgument(0))
                .orElse(Map.of());
    }

    /** Builds an update the way a client does: only the settings it means to change. */
    private static SettingsJson update(Object... keyValues) {
        var json = new SettingsJson();
        for (var i = 0; i < keyValues.length; i += 2) {
            var key = (String) keyValues[i];
            var value = keyValues[i + 1];
            switch (key) {
                case ReadOnlySetting.KEY -> json.setReadOnly((Boolean) value);
                case MaxExtensionSizeSetting.KEY -> json.setMaxExtensionSize((Long) value);
                case BannerSetting.KEY_ENABLED -> json.setBannerEnabled((Boolean) value);
                case BannerSetting.KEY_MESSAGE -> json.setBannerMessage((String) value);
                case BannerSetting.KEY_SEVERITY -> json.setBannerSeverity((String) value);
                case BannerSetting.KEY_DISMISS_ID -> json.setBannerDismissId((String) value);
                default -> throw new IllegalArgumentException("unknown setting " + key);
            }
        }
        return json;
    }

    @Test
    void siteSettingsHoldBackReadOnlyMode() {
        stored(ReadOnlySetting.KEY, true, BannerSetting.KEY_ENABLED, true, BannerSetting.KEY_MESSAGE, "Hi");

        // SiteSettingsJson has no read-only property at all; the banner still comes through
        assertThat(settings.getSiteSettings().getBannerMessage()).isEqualTo("Hi");
    }

    @Test
    void siteSettingsHoldBackADraftedBanner() {
        stored(BannerSetting.KEY_ENABLED, false, BannerSetting.KEY_MESSAGE, "Security incident");

        var site = settings.getSiteSettings();
        assertThat(site.getBannerMessage()).isNull();
        assertThat(site.getBannerEnabled()).isNull();
    }

    @Test
    void theAdminViewKeepsADraftedBanner() {
        stored(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "Security incident");

        var json = settings.getCurrentSettings();
        assertThat(json.getReadOnly()).isTrue();
        assertThat(json.getBannerMessage()).isEqualTo("Security incident");
    }

    @Test
    void aSettingLeftOutOfTheRequestIsNotTouched() {
        stored(ReadOnlySetting.KEY, true);

        settings.updateFromJson(update(BannerSetting.KEY_MESSAGE, "Maintenance tonight"));

        assertThat(written()).doesNotContainKey(ReadOnlySetting.KEY);
    }

    @Test
    void updateWritesEveryRowTheRequestCarriesAndNothingElse() {
        stored(
                BannerSetting.KEY_ENABLED,
                true,
                BannerSetting.KEY_MESSAGE,
                "tonigth",
                BannerSetting.KEY_SEVERITY,
                "warning",
                BannerSetting.KEY_DISMISS_ID,
                "token-1");

        settings.updateFromJson(
                update(
                        BannerSetting.KEY_ENABLED,
                        true,
                        BannerSetting.KEY_MESSAGE,
                        "tonight",
                        BannerSetting.KEY_DISMISS_ID,
                        "token-1"));

        assertThat(written()).containsEntry(BannerSetting.KEY_MESSAGE, "tonight");
        assertThat(written()).containsEntry(BannerSetting.KEY_ENABLED, true);
        assertThat(written()).containsEntry(BannerSetting.KEY_DISMISS_ID, "token-1");
        assertThat(written()).doesNotContainKey(BannerSetting.KEY_SEVERITY);
    }

    // The snapshot a node reads can be a minute behind: if another node just switched read-only on,
    // this one still reads "off", and an admin switching it off must not be mistaken for a no-op.
    @Test
    void aValueEqualToAStaleSnapshotIsStillWritten() {
        stored(ReadOnlySetting.KEY, false);

        settings.updateFromJson(update(ReadOnlySetting.KEY, false));

        assertThat(written()).containsEntry(ReadOnlySetting.KEY, false);
    }

    @Test
    void aSaveWritesAllItsRowsInOneCall() {
        stored(BannerSetting.KEY_ENABLED, false, BannerSetting.KEY_MESSAGE, "Drafted");

        settings.updateFromJson(update(BannerSetting.KEY_ENABLED, true, BannerSetting.KEY_MESSAGE, "Published"));

        // One transaction, so no reader can see the switch on beside the old message.
        verify(cache, times(1)).setAll(any());
        assertThat(written()).containsEntry(BannerSetting.KEY_ENABLED, true)
                .containsEntry(BannerSetting.KEY_MESSAGE, "Published");
    }

    @Test
    void theCacheIsClearedAndPublishedOnlyAfterTheRowsAreStored() {
        stored(ReadOnlySetting.KEY, false);

        settings.updateFromJson(update(ReadOnlySetting.KEY, true));

        var order = Mockito.inOrder(cache, channel);
        order.verify(cache).setAll(any());
        order.verify(cache).clear();
        order.verify(channel).publish();
    }

    @Test
    void aFailedWriteClearsAndPublishesNothing() {
        stored(ReadOnlySetting.KEY, false);
        Mockito.doThrow(new IllegalStateException("db down")).when(cache).setAll(any());

        assertThatThrownBy(() -> settings.updateFromJson(update(ReadOnlySetting.KEY, true)))
                .isInstanceOf(IllegalStateException.class);

        verify(cache, never()).clear();
        verify(channel, never()).publish();
    }

    @Test
    void aRejectedValueLeavesNothingWritten() {
        stored(ReadOnlySetting.KEY, false);

        assertThatThrownBy(
                () -> settings.updateFromJson(
                        update(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "<script>alert(1)</script>")))
                .isInstanceOf(ErrorResultException.class);

        verify(cache, never()).setAll(any());
        verify(channel, never()).publish();
    }

    @Test
    void aSettingRefusingAfterAnotherOneChangedStillLeavesNothingWritten() {
        // The rows a setting plans are held back until every setting has validated - checked with a
        // refusing setting that sorts last, since the banner sorts ahead of every real setting.
        var refusing = new WritableSetting<String>() {

            @Override
            public String getName() {
                return "zz-refusing";
            }

            @Override
            public String read(SettingRows stored) {
                return "";
            }

            @Override
            public String merge(String current, SettingRows update) {
                return current;
            }

            @Override
            public Map<String, Object> toRows(String value) {
                return Map.of(getName(), value);
            }

            @Override
            public void validate(String value, String current) {
                throw WritableSetting.reject("nope");
            }
        };
        var service = new SettingsService(
                List.of(readOnly, refusing),
                readOnly,
                maxExtensionSize,
                publishingConfig,
                cache,
                channel,
                new AfterCommitExecutor());
        stored(ReadOnlySetting.KEY, false);

        assertThatThrownBy(() -> service.updateFromJson(update(ReadOnlySetting.KEY, true)))
                .isInstanceOf(ErrorResultException.class);

        verify(cache, never()).setAll(any());
    }

    @Test
    void theAdminViewFillsEveryFieldFromAnEmptyStore() {
        // SettingsJson is NON_NULL, so a field a setting stops reporting vanishes off the wire.
        stored();

        var json = settings.getCurrentSettings();
        assertThat(json.getReadOnly()).isFalse();
        assertThat(json.getBannerEnabled()).isFalse();
        assertThat(json.getBannerMessage()).isEmpty();
        assertThat(json.getBannerSeverity()).isEqualTo("info");
        assertThat(json.getBannerDismissId()).isEmpty();
    }

    @Test
    void theAuditLineNamesEveryChangedRowAndQuotesNoMessage() {
        stored(ReadOnlySetting.KEY, false, BannerSetting.KEY_MESSAGE, "tonigth");

        var changes = settings
                .updateFromJson(update(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "tonight"));

        // Settings in name order, and the message named rather than quoted: the log column caps at 512.
        assertThat(changes).isEqualTo("banner-message, read-only -> true");
        verify(channel).publish();
    }

    @Test
    void aCorrectedMessageKeepsTheDismissToken() {
        stored(BannerSetting.KEY_MESSAGE, "tonigth", BannerSetting.KEY_DISMISS_ID, "token-1");

        // A client that doesn't know about the token can't reset dismissals by leaving it out.
        settings.updateFromJson(update(BannerSetting.KEY_MESSAGE, "tonight"));

        assertThat(written()).doesNotContainKey(BannerSetting.KEY_DISMISS_ID);
    }

    @Test
    void aSuppliedDismissTokenIsStoredAsGiven() {
        stored(BannerSetting.KEY_MESSAGE, "tonight", BannerSetting.KEY_DISMISS_ID, "token-1");

        settings.updateFromJson(update(BannerSetting.KEY_MESSAGE, "tonight", BannerSetting.KEY_DISMISS_ID, "token-2"));

        assertThat(written()).containsEntry(BannerSetting.KEY_DISMISS_ID, "token-2");
    }

    @Test
    void aBannerReplacingAClearedOneGetsAFreshToken() {
        // The admin page echoes the stored token back, so reusing it would hide the new banner
        // from everyone who dismissed the old one.
        stored(BannerSetting.KEY_MESSAGE, "", BannerSetting.KEY_DISMISS_ID, "token-1");

        settings.updateFromJson(
                update(BannerSetting.KEY_MESSAGE, "Something new", BannerSetting.KEY_DISMISS_ID, "token-1"));

        assertThat(written().get(BannerSetting.KEY_DISMISS_ID)).asString().isNotBlank().isNotEqualTo("token-1");
    }

    @Test
    void anEmptyBannerMintsNoDismissToken() {
        stored();

        settings.updateFromJson(update(BannerSetting.KEY_MESSAGE, "", BannerSetting.KEY_SEVERITY, "info"));

        assertThat(written()).doesNotContainKey(BannerSetting.KEY_DISMISS_ID);
    }

    @Test
    void readOnlyModeReadsTheStoredFlag() {
        stored(ReadOnlySetting.KEY, true);
        assertThat(settings.isReadOnly()).isTrue();

        stored();
        assertThat(settings.isReadOnly()).isFalse();
    }

    // A clear issued before the commit lets any node refill the ceiling from the rows as they still
    // are, leaving an entry staler than if nothing had been evicted.
    @Test
    void invalidateCacheWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        settings.invalidateCache();
        verify(cache, never()).clear();

        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(cache).clear();
        verify(channel).publish();
    }

    @Test
    void invalidateCacheClearsStraightAwayWithoutATransaction() {
        settings.invalidateCache();

        verify(cache).clear();
    }

    @Test
    void maxExtensionSizeFallsBackToPublishingConfigWhenNothingIsStored() {
        stored();

        assertThat(settings.getMaxExtensionSize()).isEqualTo(512L * MB);
    }

    @Test
    void maxExtensionSizeReturnsTheStoredValue() {
        stored(MaxExtensionSizeSetting.KEY, 1024L * MB);

        assertThat(settings.getMaxExtensionSize()).isEqualTo(1024L * MB);
    }

    @Test
    void updateStoresNewMaxExtensionSizeAndReportsTheChange() {
        stored(MaxExtensionSizeSetting.KEY, 512L * MB);

        var changes = settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 1024L * MB));

        assertThat(written()).containsEntry(MaxExtensionSizeSetting.KEY, 1024L * MB);
        assertThat(changes).isEqualTo("max-extension-size -> 1073741824");
    }

    // With nothing stored the value read back is the configuration fallback, so storing that same
    // number is the act of pinning it against a later change to the configuration file.
    @Test
    void updateStoresMaxExtensionSizeEvenWhenItMatchesWhatIsReadBack() {
        stored();

        var changes = settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 512L * MB));

        assertThat(written()).containsEntry(MaxExtensionSizeSetting.KEY, 512L * MB);
        assertThat(changes).isEqualTo("max-extension-size -> 536870912");
    }

    @Test
    void updateLeavesAnOmittedMaxExtensionSizeAlone() {
        stored();

        settings.updateFromJson(update(ReadOnlySetting.KEY, true));

        assertThat(written()).doesNotContainKey(MaxExtensionSizeSetting.KEY);
    }

    @Test
    void updateLeavesAnOmittedReadOnlyAlone() {
        stored(ReadOnlySetting.KEY, true);

        settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 1024L * MB));

        assertThat(written()).doesNotContainKey(ReadOnlySetting.KEY);
    }

    @Test
    void updateAcceptsARequestThatCarriesNothing() {
        stored();

        assertThatCode(() -> settings.updateFromJson(new SettingsJson())).doesNotThrowAnyException();
        verify(cache, never()).setAll(any());
    }

    @Test
    void updateRejectsNonPositiveMaxExtensionSize() {
        stored();

        assertThatThrownBy(() -> settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 0L)))
                .isInstanceOf(ErrorResultException.class);
        verify(cache, never()).setAll(any());
    }

    @Test
    void updateRejectsMaxExtensionSizeAboveTheOverrideCeiling() {
        stored();
        when(publishingConfig.getMaxOverrideSize()).thenReturn(1000L);

        assertThatThrownBy(() -> settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 1001L)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("exceeds the maximum");
        verify(cache, never()).setAll(any());
    }

    // The ceiling can be lowered below a default stored earlier; that default stays in force until
    // edited, and must not stop an admin saving something else.
    @Test
    void aStoredSizeAboveALoweredCeilingDoesNotBlockAnUnrelatedSave() {
        stored(MaxExtensionSizeSetting.KEY, 1024L * MB);
        when(publishingConfig.getMaxOverrideSize()).thenReturn(100L * MB);

        settings.updateFromJson(update(ReadOnlySetting.KEY, true));

        assertThat(written()).containsEntry(ReadOnlySetting.KEY, true);
    }

    @Test
    void aChangedSizeIsStillHeldToTheLoweredCeiling() {
        stored(MaxExtensionSizeSetting.KEY, 1024L * MB);
        when(publishingConfig.getMaxOverrideSize()).thenReturn(100L * MB);

        assertThatThrownBy(() -> settings.updateFromJson(update(MaxExtensionSizeSetting.KEY, 200L * MB)))
                .isInstanceOf(ErrorResultException.class);
    }

    @Test
    void theAdminViewReportsTheMaxExtensionSizeAndTheOverrideCeiling() {
        stored();

        var json = settings.getCurrentSettings();
        assertThat(json.getMaxExtensionSize()).isEqualTo(512L * MB);
        assertThat(json.getMaxOverrideSize()).isEqualTo(1024L * MB);
    }
}
