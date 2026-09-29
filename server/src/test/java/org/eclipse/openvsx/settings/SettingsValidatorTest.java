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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsValidatorTest {

    private static SettingsJson message(String message) {
        var json = new SettingsJson();
        json.setBannerMessage(message);
        return json;
    }

    private static SettingsJson severity(String severity) {
        var json = new SettingsJson();
        json.setBannerSeverity(severity);
        return json;
    }

    private static SettingsJson dismissId(String dismissId) {
        var json = new SettingsJson();
        json.setBannerDismissId(dismissId);
        return json;
    }

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
        assertThatCode(() -> SettingsValidator.validate(message(text))).doesNotThrowAnyException();
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
        assertThatThrownBy(() -> SettingsValidator.validate(message(text)))
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
        assertThatThrownBy(() -> SettingsValidator.validate(message(text)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("may not link to");
    }

    @Test
    void rejectsAnOverlongMessage() {
        assertThatThrownBy(
                () -> SettingsValidator.validate(message("a".repeat(SettingsValidator.MAX_MESSAGE_LENGTH + 1))))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("at most " + SettingsValidator.MAX_MESSAGE_LENGTH + " characters");
    }

    @Test
    void rejectsAnUnknownSeverity() {
        assertThatThrownBy(() -> SettingsValidator.validate(severity("critical")))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Unsupported banner severity: critical");
    }

    @Test
    void rejectsADismissIdThatIsNotAPlainToken() {
        assertThatThrownBy(() -> SettingsValidator.validate(dismissId("<script>")))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("dismiss id");
    }

    @Test
    void acceptsAnUpdateThatTouchesNoBannerSetting() {
        assertThatCode(() -> SettingsValidator.validate(new SettingsJson())).doesNotThrowAnyException();
    }

    @Test
    void refusalsAreBadRequests() {
        assertThatThrownBy(() -> SettingsValidator.validate(message("<script>alert(1)</script>")))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
