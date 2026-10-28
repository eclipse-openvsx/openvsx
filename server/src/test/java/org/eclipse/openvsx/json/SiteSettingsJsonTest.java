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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SiteSettingsJsonTest {

    private final JsonMapper mapper = JsonMapper.shared();

    @Test
    void serializesTheBannerAsCamelCase() {
        var json = new SiteSettingsJson();
        json.setBannerEnabled(true);
        json.setBannerMessage("Maintenance tonight");
        json.setBannerSeverity("warning");
        json.setBannerDismissId("token-1");

        var tree = mapper.readTree(mapper.writeValueAsString(json));

        assertThat(tree.propertyNames())
                .containsExactlyInAnyOrder("bannerEnabled", "bannerMessage", "bannerSeverity", "bannerDismissId");
        assertThat(tree.get("bannerMessage").stringValue()).isEqualTo("Maintenance tonight");
    }
}
