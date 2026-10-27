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
import org.springframework.context.ApplicationEventPublisher;
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

    private final SettingsStore store = mock(SettingsStore.class);
    private final SettingsUpdateChannel channel = mock(SettingsUpdateChannel.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReadOnlySetting readOnly = new ReadOnlySetting(store);
    private final PublishingConfig publishingConfig = mock(PublishingConfig.class);
    private final MaxExtensionSizeSetting maxExtensionSize = new MaxExtensionSizeSetting(store, publishingConfig);
    private final SettingsService settings = new SettingsService(
            List.of(readOnly, maxExtensionSize, new BannerSetting()),
            new SettingsJsonMapper(publishingConfig, mock(DownloadAnalyticsSetting.class)),
            store,
            channel,
            new AfterCommitExecutor(),
            events);

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

    /** Also how an update is built: only the rows the client means to change. */
    private static SettingRows rows(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new SettingRows(map);
    }

    private void stored(Object... keyValues) {
        var snapshot = rows(keyValues);
        when(store.snapshot()).thenReturn(snapshot);
        when(store.load()).thenReturn(snapshot);
    }

    /** The rows the save handed to {@code setAll}; empty when it wrote nothing. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> written() {
        return mockingDetails(store).getInvocations()
                .stream()
                .filter(call -> call.getMethod().getName().equals("setAll"))
                .findFirst()
                .map(call -> (Map<String, Object>) call.getArgument(0))
                .orElse(Map.of());
    }

    @Test
    void siteSettingsHoldBackReadOnlyMode() {
        stored(ReadOnlySetting.KEY, true, BannerSetting.KEY_ENABLED, true, BannerSetting.KEY_MESSAGE, "Hi");

        assertThat(settings.siteRows().rows())
                .doesNotContainKey(ReadOnlySetting.KEY)
                .containsEntry(BannerSetting.KEY_MESSAGE, "Hi");
    }

    @Test
    void aPublishedBannerAlwaysCarriesAllFourSiteSettings() {
        stored(BannerSetting.KEY_ENABLED, true, BannerSetting.KEY_MESSAGE, "Hi");

        assertThat(settings.siteRows().rows())
                .containsEntry(BannerSetting.KEY_ENABLED, true)
                .containsEntry(BannerSetting.KEY_MESSAGE, "Hi")
                .containsEntry(BannerSetting.KEY_SEVERITY, "info")
                .containsEntry(BannerSetting.KEY_DISMISS_ID, "");
    }

    @Test
    void siteSettingsHoldBackADraftedBanner() {
        stored(BannerSetting.KEY_ENABLED, false, BannerSetting.KEY_MESSAGE, "Security incident");

        assertThat(settings.siteRows().rows()).isEmpty();
    }

    @Test
    void theAdminViewKeepsADraftedBanner() {
        stored(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "Security incident");

        assertThat(settings.currentRows().rows())
                .containsEntry(ReadOnlySetting.KEY, true)
                .containsEntry(BannerSetting.KEY_MESSAGE, "Security incident");
    }

    @Test
    void aSettingLeftOutOfTheRequestIsNotTouched() {
        stored(ReadOnlySetting.KEY, true);

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "Maintenance tonight"));

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

        settings.updateRows(
                rows(
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

        settings.updateRows(rows(ReadOnlySetting.KEY, false));

        assertThat(written()).containsEntry(ReadOnlySetting.KEY, false);
    }

    @Test
    void aSaveWritesAllItsRowsInOneCall() {
        stored(BannerSetting.KEY_ENABLED, false, BannerSetting.KEY_MESSAGE, "Drafted");

        settings.updateRows(rows(BannerSetting.KEY_ENABLED, true, BannerSetting.KEY_MESSAGE, "Published"));

        // One transaction, so no reader can see the switch on beside the old message.
        verify(store, times(1)).setAll(any());
        assertThat(written()).containsEntry(BannerSetting.KEY_ENABLED, true)
                .containsEntry(BannerSetting.KEY_MESSAGE, "Published");
    }

    @Test
    void theCacheIsClearedAndPublishedOnlyAfterTheRowsAreStored() {
        stored(ReadOnlySetting.KEY, false);

        settings.updateRows(rows(ReadOnlySetting.KEY, true));

        var order = Mockito.inOrder(store, channel);
        order.verify(store).setAll(any());
        order.verify(store).clear();
        order.verify(channel).publish();
    }

    @Test
    void aFailedWriteClearsAndPublishesNothing() {
        stored(ReadOnlySetting.KEY, false);
        Mockito.doThrow(new IllegalStateException("db down")).when(store).setAll(any());

        assertThatThrownBy(() -> settings.updateRows(rows(ReadOnlySetting.KEY, true)))
                .isInstanceOf(IllegalStateException.class);

        verify(store, never()).clear();
        verify(channel, never()).publish();
    }

    @Test
    void aRejectedValueLeavesNothingWritten() {
        stored(ReadOnlySetting.KEY, false);

        assertThatThrownBy(
                () -> settings.updateRows(
                        rows(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "<script>alert(1)</script>")))
                .isInstanceOf(ErrorResultException.class);

        verify(store, never()).setAll(any());
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
                mock(SettingsJsonMapper.class),
                store,
                channel,
                new AfterCommitExecutor(),
                events);
        stored(ReadOnlySetting.KEY, false);

        assertThatThrownBy(() -> service.updateRows(rows(ReadOnlySetting.KEY, true)))
                .isInstanceOf(ErrorResultException.class);

        verify(store, never()).setAll(any());
    }

    @Test
    void theAdminViewFillsEveryFieldFromAnEmptyStore() {
        // SettingsJson is NON_NULL, so a field a setting stops reporting vanishes off the wire.
        stored();

        assertThat(settings.currentRows().rows())
                .containsEntry(ReadOnlySetting.KEY, false)
                .containsEntry(BannerSetting.KEY_ENABLED, false)
                .containsEntry(BannerSetting.KEY_MESSAGE, "")
                .containsEntry(BannerSetting.KEY_SEVERITY, "info")
                .containsEntry(BannerSetting.KEY_DISMISS_ID, "");
    }

    @Test
    void anUpdateDocumentWritesTheRowsItCarries() {
        stored(ReadOnlySetting.KEY, false);
        var update = new SettingsJson();
        update.setReadOnly(true);

        assertThat(settings.update(update)).isEqualTo("read-only -> true");
        verify(store).setAll(Map.of(ReadOnlySetting.KEY, true));
    }

    @Test
    void theAdminDocumentReadsTheStoredRows() {
        stored(ReadOnlySetting.KEY, true);

        var current = settings.getCurrentSettings();

        assertThat(current.getReadOnly()).isTrue();
        assertThat(current.getMaxExtensionSize()).isEqualTo(512L * MB);
        assertThat(current.getMaxOverrideSize()).isEqualTo(1024L * MB);
    }

    @Test
    void theDownloadAnalyticsSwitchIsPublishedAsItsEffectiveState() {
        stored(DownloadAnalyticsSetting.KEY, true);

        assertThat(withAnalytics(true).getSiteSettings().getDownloadAnalyticsVisible()).isTrue();
        assertThat(withAnalytics(true).getCurrentSettings().getDownloadAnalyticsVisible()).isTrue();
        // A switch left on after analytics was unconfigured is stored, but neither served nor offered.
        assertThat(withAnalytics(false).getSiteSettings().getDownloadAnalyticsVisible()).isFalse();
        assertThat(withAnalytics(false).getCurrentSettings().getDownloadAnalyticsVisible()).isNull();
    }

    @Test
    void aDeploymentsOwnSettingIsReadUpdatedAndPublishedThroughTheJsonDocuments() {
        var service = new SettingsService(
                List.of(readOnly, new AuditLogSetting()),
                new SettingsJsonMapper(publishingConfig, mock(DownloadAnalyticsSetting.class)),
                store,
                channel,
                new AfterCommitExecutor(),
                events);
        stored();

        assertThat(service.getCurrentSettings().getAdditionalSettings()).containsEntry("auditLogEnabled", false);
        assertThat(service.getSiteSettings().getAdditionalSettings()).containsEntry("auditLogEnabled", false);

        var update = new SettingsJson();
        update.setAdditionalSetting("auditLogEnabled", true);
        update.setAdditionalSetting("unknownSetting", true);
        assertThat(service.update(update)).isEqualTo("audit-log-enabled -> true");
        verify(store).setAll(Map.of("audit-log-enabled", true));
    }

    /** What a deployment's own module would register: a public boolean outside this package's DTO fields. */
    private static class AuditLogSetting implements WritableSetting<Boolean>, PublicSetting {

        @Override
        public String getName() {
            return "audit-log-enabled";
        }

        @Override
        public Boolean read(SettingRows stored) {
            return stored.asBoolean(getName(), false);
        }

        @Override
        public Boolean merge(Boolean current, SettingRows update) {
            return update.asBoolean(getName(), current);
        }

        @Override
        public Map<String, Object> toRows(Boolean value) {
            return Map.of(getName(), value);
        }

        @Override
        public Map<String, Object> publicView(SettingRows stored) {
            return toRows(read(stored));
        }
    }

    private SettingsService withAnalytics(boolean configured) {
        var analytics = new DownloadAnalyticsSetting(configured);
        return new SettingsService(
                List.of(readOnly, analytics),
                new SettingsJsonMapper(publishingConfig, analytics),
                store,
                channel,
                new AfterCommitExecutor(),
                events);
    }

    @Test
    void theAuditLineNamesEveryChangedRowAndQuotesNoMessage() {
        stored(ReadOnlySetting.KEY, false, BannerSetting.KEY_MESSAGE, "tonigth");

        var changes = settings
                .updateRows(rows(ReadOnlySetting.KEY, true, BannerSetting.KEY_MESSAGE, "tonight"));

        // Settings in name order, and the message named rather than quoted: the log column caps at 512.
        assertThat(changes).isEqualTo("banner-message, read-only -> true");
        verify(channel).publish();
    }

    @Test
    void aCorrectedMessageKeepsTheDismissToken() {
        stored(BannerSetting.KEY_MESSAGE, "tonigth", BannerSetting.KEY_DISMISS_ID, "token-1");

        // A client that doesn't know about the token can't reset dismissals by leaving it out.
        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "tonight"));

        assertThat(written()).doesNotContainKey(BannerSetting.KEY_DISMISS_ID);
    }

    @Test
    void aSuppliedDismissTokenIsStoredAsGiven() {
        stored(BannerSetting.KEY_MESSAGE, "tonight", BannerSetting.KEY_DISMISS_ID, "token-1");

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "tonight", BannerSetting.KEY_DISMISS_ID, "token-2"));

        assertThat(written()).containsEntry(BannerSetting.KEY_DISMISS_ID, "token-2");
    }

    @Test
    void aBannerReplacingAClearedOneGetsAFreshToken() {
        // The admin page echoes the stored token back, so reusing it would hide the new banner
        // from everyone who dismissed the old one.
        stored(BannerSetting.KEY_MESSAGE, "", BannerSetting.KEY_DISMISS_ID, "token-1");

        settings.updateRows(
                rows(BannerSetting.KEY_MESSAGE, "Something new", BannerSetting.KEY_DISMISS_ID, "token-1"));

        assertThat(written().get(BannerSetting.KEY_DISMISS_ID)).asString().isNotBlank().isNotEqualTo("token-1");
    }

    // Another node may have cleared the banner already; this node's cached copy still holds the old
    // message, and merging against it would keep the old token for a banner that is new.
    @Test
    void aSaveMergesAgainstTheStoreNotTheCachedSnapshot() {
        stored(BannerSetting.KEY_MESSAGE, "", BannerSetting.KEY_DISMISS_ID, "token-1");
        when(store.snapshot())
                .thenReturn(
                        new SettingRows(
                                Map.of(
                                        BannerSetting.KEY_MESSAGE,
                                        "Old banner",
                                        BannerSetting.KEY_DISMISS_ID,
                                        "token-1")));

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "Something new"));

        assertThat(written().get(BannerSetting.KEY_DISMISS_ID)).asString().isNotBlank().isNotEqualTo("token-1");
    }

    @Test
    void anEmptyBannerMintsNoDismissToken() {
        stored();

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "", BannerSetting.KEY_SEVERITY, "info"));

        assertThat(written()).doesNotContainKey(BannerSetting.KEY_DISMISS_ID);
    }

    // A clear issued before the commit lets any node refill the ceiling from the rows as they still
    // are, leaving an entry staler than if nothing had been evicted.
    @Test
    void invalidateCacheWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        settings.invalidateCache();
        verify(store, never()).clear();

        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(store).clear();
        verify(channel).publish();
    }

    @Test
    void invalidateCacheClearsStraightAwayWithoutATransaction() {
        settings.invalidateCache();

        verify(store).clear();
    }

    @Test
    void publishesAChangedEventWhenSettingsChange() {
        stored(BannerSetting.KEY_MESSAGE, "old");

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "new"));

        verify(events).publishEvent(any(SettingsChangedEvent.class));
    }

    @Test
    void publishesNoEventWhenNothingChanged() {
        stored();

        settings.updateRows(rows());

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void publishesAChangedEventOnInvalidateCache() {
        settings.invalidateCache();

        verify(events).publishEvent(any(SettingsChangedEvent.class));
    }

    @Test
    void publishesTheChangedEventOnlyAfterTheCommit() {
        stored(BannerSetting.KEY_MESSAGE, "old");
        TransactionSynchronizationManager.initSynchronization();

        settings.updateRows(rows(BannerSetting.KEY_MESSAGE, "new"));
        verify(events, never()).publishEvent(any(Object.class));

        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(events).publishEvent(any(SettingsChangedEvent.class));
    }

    @Test
    void updateStoresNewMaxExtensionSizeAndReportsTheChange() {
        stored(MaxExtensionSizeSetting.KEY, 512L * MB);

        var changes = settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 1024L * MB));

        assertThat(written()).containsEntry(MaxExtensionSizeSetting.KEY, 1024L * MB);
        assertThat(changes).isEqualTo(MaxExtensionSizeSetting.KEY + " -> 1073741824");
    }

    // With nothing stored the value read back is the configuration fallback, so storing that same
    // number is the act of pinning it against a later change to the configuration file.
    @Test
    void updateStoresMaxExtensionSizeEvenWhenItMatchesWhatIsReadBack() {
        stored();

        var changes = settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 512L * MB));

        assertThat(written()).containsEntry(MaxExtensionSizeSetting.KEY, 512L * MB);
        assertThat(changes).isEqualTo(MaxExtensionSizeSetting.KEY + " -> 536870912");
    }

    @Test
    void updateLeavesAnOmittedMaxExtensionSizeAlone() {
        stored();

        settings.updateRows(rows(ReadOnlySetting.KEY, true));

        assertThat(written()).doesNotContainKey(MaxExtensionSizeSetting.KEY);
    }

    @Test
    void updateLeavesAnOmittedReadOnlyAlone() {
        stored(ReadOnlySetting.KEY, true);

        settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 1024L * MB));

        assertThat(written()).doesNotContainKey(ReadOnlySetting.KEY);
    }

    @Test
    void updateAcceptsARequestThatCarriesNothing() {
        stored();

        assertThatCode(() -> settings.updateRows(rows())).doesNotThrowAnyException();
        verify(store, never()).setAll(any());
    }

    @Test
    void updateRejectsNonPositiveMaxExtensionSize() {
        stored();

        assertThatThrownBy(() -> settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 0L)))
                .isInstanceOf(ErrorResultException.class);
        verify(store, never()).setAll(any());
    }

    @Test
    void updateRejectsMaxExtensionSizeAboveTheOverrideCeiling() {
        stored();
        when(publishingConfig.getMaxOverrideSize()).thenReturn(1000L);

        assertThatThrownBy(() -> settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 1001L)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("exceeds the maximum");
        verify(store, never()).setAll(any());
    }

    // The ceiling can be lowered below a default stored earlier; that default stays in force until
    // edited, and must not stop an admin saving something else.
    @Test
    void aStoredSizeAboveALoweredCeilingDoesNotBlockAnUnrelatedSave() {
        stored(MaxExtensionSizeSetting.KEY, 1024L * MB);
        when(publishingConfig.getMaxOverrideSize()).thenReturn(100L * MB);

        settings.updateRows(rows(ReadOnlySetting.KEY, true));

        assertThat(written()).containsEntry(ReadOnlySetting.KEY, true);
    }

    @Test
    void aChangedSizeIsStillHeldToTheLoweredCeiling() {
        stored(MaxExtensionSizeSetting.KEY, 1024L * MB);
        when(publishingConfig.getMaxOverrideSize()).thenReturn(100L * MB);

        assertThatThrownBy(() -> settings.updateRows(rows(MaxExtensionSizeSetting.KEY, 200L * MB)))
                .isInstanceOf(ErrorResultException.class);
    }

    @Test
    void theAdminViewReportsTheMaxExtensionSize() {
        stored();

        assertThat(settings.currentRows().rows()).containsEntry(MaxExtensionSizeSetting.KEY, 512L * MB);
    }
}
