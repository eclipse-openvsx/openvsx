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
package org.eclipse.openvsx.json;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The admin payload has to use the same key names the settings are stored and served under, so the
 * web UI reads one shape from both endpoints.
 */
class SettingsJsonTest {

    private final JsonMapper mapper = JsonMapper.shared();

    @Test
    void serializesToTheStoredSettingKeys() {
        var json = new SettingsJson();
        json.setReadOnly(false);
        json.setBannerEnabled(true);
        json.setBannerMessage("Maintenance tonight");
        json.setBannerSeverity("warning");
        json.setBannerDismissId("token-1");

        var tree = mapper.readTree(mapper.writeValueAsString(json));

        assertThat(tree.propertyNames())
                .containsExactlyInAnyOrder(
                        "read-only",
                        "banner-enabled",
                        "banner-message",
                        "banner-severity",
                        "banner-dismiss-id");
        assertThat(tree.get("read-only").booleanValue()).isFalse();
        assertThat(tree.get("banner-enabled").booleanValue()).isTrue();
        assertThat(tree.get("banner-message").stringValue()).isEqualTo("Maintenance tonight");
    }

    @Test
    void deserializesFromTheStoredSettingKeys() {
        var json = mapper.readValue("""
                {"read-only":true,"banner-enabled":false,"banner-message":"Hi",\
                "banner-severity":"info","banner-dismiss-id":"token-2"}\
                """, SettingsJson.class);

        assertThat(json.isReadOnly()).isTrue();
        assertThat(json.isBannerEnabled()).isFalse();
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
        assertThat(json.getBannerSeverity()).isEqualTo("info");
        assertThat(json.getBannerDismissId()).isEqualTo("token-2");
    }

    @Test
    void anOmittedSettingStaysNull() {
        var json = mapper.readValue("{\"banner-message\":\"Hi\"}", SettingsJson.class);

        assertThat(json.isReadOnly()).isNull();
        assertThat(json.isBannerEnabled()).isNull();
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
    }

    @Test
    void theRowsUseTheSameKeysAsTheWire() {
        var json = new SettingsJson();
        json.setReadOnly(true);
        json.setBannerMessage("Maintenance tonight");

        assertThat(json.toRows())
                .containsExactly(
                        entry("read-only", true),
                        entry("banner-message", "Maintenance tonight"));
    }

    @Test
    void aSettingLeftOutOfTheRequestProducesNoRow() {
        assertThat(new SettingsJson().toRows()).isEmpty();
    }

    @Test
    void rowsReadBackIntoThePayload() {
        var json = SettingsJson
                .of(Map.of("read-only", true, "banner-severity", "warning", "banner-message", "Hi"));

        assertThat(json.isReadOnly()).isTrue();
        assertThat(json.getBannerSeverity()).isEqualTo("warning");
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
        assertThat(json.isBannerEnabled()).isNull();
    }
}
