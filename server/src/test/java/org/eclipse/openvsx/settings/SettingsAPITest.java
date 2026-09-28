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
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsAPITest {

    private final SettingsService settings = Mockito.mock(SettingsService.class);

    // standaloneSetup installs no security filters, so this covers the payload only; that the path
    // is anonymous comes from SecurityConfig's /api/** permitAll.
    @Test
    void returnsTheSiteSettingsAsJson() throws Exception {
        Mockito.when(settings.getSiteSettings())
                .thenReturn(
                        Map.of(
                                SettingsService.SETTING_BANNER_ENABLED,
                                true,
                                SettingsService.SETTING_BANNER_MESSAGE,
                                "Maintenance tonight",
                                SettingsService.SETTING_BANNER_SEVERITY,
                                "warning"));

        MockMvcBuilders.standaloneSetup(new SettingsAPI(settings))
                .build()
                .perform(get("/api/-/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['banner-enabled']").value(true))
                .andExpect(jsonPath("$.['banner-message']").value("Maintenance tonight"))
                .andExpect(jsonPath("$.['banner-severity']").value("warning"))
                .andExpect(jsonPath("$.['read-only']").doesNotExist());
    }
}
