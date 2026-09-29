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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.settings.BannerSetting.Banner;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.openvsx.settings.BannerSetting.KEY_DISMISS_ID;
import static org.eclipse.openvsx.settings.BannerSetting.KEY_ENABLED;
import static org.eclipse.openvsx.settings.BannerSetting.KEY_MESSAGE;
import static org.eclipse.openvsx.settings.BannerSetting.KEY_SEVERITY;

class BannerSettingTest {

    private final BannerSetting banner = new BannerSetting();

    private static SettingRows rows(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new SettingRows(map);
    }

    /** Validation is scoped to what changed, so a rule only fires once the field it guards moves. */
    private void validateAgainstNothingStored(Banner value) {
        banner.validate(value, banner.read(rows()));
    }

    private static Banner message(String message) {
        return new Banner(false, message, "info", "");
    }

    // -- reading ----------------------------------------------------------------------------

    @Test
    void anEmptyStoreReadsTheDefaults() {
        assertThat(banner.read(rows())).isEqualTo(new Banner(false, "", "info", ""));
    }

    @Test
    void aRowOfTheWrongTypeFallsBackRatherThanFailing() {
        // Values can be written straight into the jsonb column, and one bad row must not break reads.
        assertThat(banner.read(rows(KEY_SEVERITY, 42)).severity()).isEqualTo("info");
    }

    // -- merging ----------------------------------------------------------------------------

    @Test
    void anUpdateTouchingNoBannerRowLeavesTheValueAlone() {
        var current = new Banner(true, "Maintenance tonight", "warning", "token-1");

        assertThat(banner.merge(current, rows("read-only", true))).isEqualTo(current);
    }

    @Test
    void aCorrectedMessageKeepsTheDismissToken() {
        var current = new Banner(true, "tonigth", "info", "token-1");

        assertThat(banner.merge(current, rows(KEY_MESSAGE, "tonight")).dismissId()).isEqualTo("token-1");
    }

    @Test
    void aSuppliedDismissTokenIsTakenAsGiven() {
        var current = new Banner(true, "tonight", "info", "token-1");

        assertThat(banner.merge(current, rows(KEY_DISMISS_ID, "token-2")).dismissId()).isEqualTo("token-2");
    }

    @Test
    void aBannerReplacingAClearedOneGetsAFreshToken() {
        // The admin page can echo the stored token back, and reusing it would hide the new banner
        // from everyone who dismissed the old one.
        var current = new Banner(false, "", "info", "token-1");

        var merged = banner.merge(current, rows(KEY_MESSAGE, "Something new", KEY_DISMISS_ID, "token-1"));

        assertThat(merged.dismissId()).isNotBlank().isNotEqualTo("token-1");
    }

    // -- validating -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = {
            "**Maintenance** tonight - see the [status page](https://status.example.org).",
            // Ordinary prose the rules must not refuse.
            "Migration of user data: complete",
            "Queue > 500 jobs, latency <2s",
            "metadata: unavailable"
        }
    )
    void acceptsPlainMarkdownAndProse(String text) {
        assertThatCode(() -> validateAgainstNothingStored(message(text))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<iframe src='https://evil.example'></iframe>",
            "<svg/onload=alert(1)>",
            "done <!-- comment -->",
            "</b>"
        }
    )
    void rejectsHtmlTags(String text) {
        assertThatThrownBy(() -> validateAgainstNothingStored(message(text)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("may not contain HTML");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "[click](javascript:alert(1))",
            "[click](JavaScript:alert(1))",
            // Upper case: folding with the default locale would miss this on a Turkish JVM.
            "[click](JAVASCRIPT:alert(1))",
            "[click](vbscript:msgbox(1))",
            "[click](data:text/html;base64,PHNjcmlwdD4=)"
        }
    )
    void rejectsExecutableLinkDestinations(String text) {
        assertThatThrownBy(() -> validateAgainstNothingStored(message(text)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("may not link to");
    }

    @Test
    void rejectsAnOverlongMessage() {
        assertThatThrownBy(
                () -> validateAgainstNothingStored(message("a".repeat(BannerSetting.MAX_MESSAGE_LENGTH + 1))))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("at most " + BannerSetting.MAX_MESSAGE_LENGTH + " characters");
    }

    @Test
    void rejectsAnUnknownSeverity() {
        assertThatThrownBy(() -> validateAgainstNothingStored(new Banner(false, "", "critical", "")))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Unsupported banner severity: critical");
    }

    @Test
    void rejectsADismissIdThatIsNotAPlainToken() {
        assertThatThrownBy(() -> validateAgainstNothingStored(new Banner(false, "", "info", "<script>")))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("dismiss id");
    }

    @Test
    void acceptsAnUpdateThatChangesNothing() {
        var current = banner.read(rows());

        assertThatCode(() -> banner.validate(current, current)).doesNotThrowAnyException();
    }

    @Test
    void aStoredMessageThatBreaksARuleDoesNotBlockSwitchingTheBannerOff() {
        // The rules can be tightened after a message is stored, and an admin must still be able to
        // take the banner down without first rewriting it.
        var current = new Banner(true, "a".repeat(BannerSetting.MAX_MESSAGE_LENGTH + 1), "info", "token-1");
        var merged = banner.merge(current, rows(KEY_ENABLED, false));

        assertThatCode(() -> banner.validate(merged, current)).doesNotThrowAnyException();
    }

    @Test
    void refusalsAreBadRequests() {
        assertThatThrownBy(() -> validateAgainstNothingStored(message("<script>alert(1)</script>")))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -- writing ----------------------------------------------------------------------------

    @Test
    void switchingOnWritesTheSwitchLast() {
        assertThat(banner.toRows(new Banner(true, "Published", "info", "token-1")).keySet())
                .containsExactly(KEY_MESSAGE, KEY_SEVERITY, KEY_DISMISS_ID, KEY_ENABLED);
    }

    @Test
    void switchingOffWritesTheSwitchFirst() {
        assertThat(banner.toRows(new Banner(false, "Drafted", "info", "token-1")).keySet())
                .containsExactly(KEY_ENABLED, KEY_MESSAGE, KEY_SEVERITY, KEY_DISMISS_ID);
    }

    /** The message body would blow the 512-character admin log column on its own. */
    @Test
    void theLogLineNamesTheMessageWithoutQuotingIt() {
        assertThat(banner.describe(KEY_MESSAGE, "Maintenance tonight")).isEqualTo(KEY_MESSAGE);
        assertThat(banner.describe(KEY_SEVERITY, "warning")).isEqualTo("banner-severity -> warning");
    }

    // -- publishing -------------------------------------------------------------------------

    @Test
    void aDraftedBannerPublishesNothing() {
        assertThat(banner.publicView(rows(KEY_ENABLED, false, KEY_MESSAGE, "Security incident"))).isEmpty();
    }

    @Test
    void aPublishedBannerOmitsTheRowsTheStoreDoesNotHold() {
        var view = banner.publicView(rows(KEY_ENABLED, true, KEY_MESSAGE, "Maintenance tonight"));

        assertThat(view).containsExactly(Map.entry(KEY_ENABLED, true), Map.entry(KEY_MESSAGE, "Maintenance tonight"));
    }
}
