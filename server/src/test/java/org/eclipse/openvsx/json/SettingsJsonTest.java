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

/** The payload is camelCase like every other DTO, whatever the kebab-case keys the settings are stored under. */
class SettingsJsonTest {

    private final JsonMapper mapper = JsonMapper.shared();

    @Test
    void serializesToCamelCaseProperties() {
        var json = new SettingsJson();
        json.setReadOnly(false);
        json.setBannerEnabled(true);
        json.setBannerMessage("Maintenance tonight");
        json.setBannerSeverity("warning");
        json.setBannerDismissId("token-1");

        var tree = mapper.readTree(mapper.writeValueAsString(json));

        assertThat(tree.propertyNames())
                .containsExactlyInAnyOrder(
                        "readOnly",
                        "bannerEnabled",
                        "bannerMessage",
                        "bannerSeverity",
                        "bannerDismissId");
        assertThat(tree.get("readOnly").booleanValue()).isFalse();
        assertThat(tree.get("bannerEnabled").booleanValue()).isTrue();
        assertThat(tree.get("bannerMessage").stringValue()).isEqualTo("Maintenance tonight");
    }

    @Test
    void deserializesFromCamelCaseProperties() {
        var json = mapper.readValue("""
                {"readOnly":true,"bannerEnabled":false,"bannerMessage":"Hi",\
                "bannerSeverity":"info","bannerDismissId":"token-2"}\
                """, SettingsJson.class);

        assertThat(json.getReadOnly()).isTrue();
        assertThat(json.getBannerEnabled()).isFalse();
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
        assertThat(json.getBannerSeverity()).isEqualTo("info");
        assertThat(json.getBannerDismissId()).isEqualTo("token-2");
    }

    @Test
    void anOmittedSettingStaysNull() {
        var json = mapper.readValue("{\"bannerMessage\":\"Hi\"}", SettingsJson.class);

        assertThat(json.getReadOnly()).isNull();
        assertThat(json.getBannerEnabled()).isNull();
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
    }

    @Test
    void theRowsUseTheStoredKebabCaseKeys() {
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

        assertThat(json.getReadOnly()).isTrue();
        assertThat(json.getBannerSeverity()).isEqualTo("warning");
        assertThat(json.getBannerMessage()).isEqualTo("Hi");
        assertThat(json.getBannerEnabled()).isNull();
    }
}
