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
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.eclipse.openvsx.json.SiteSettingsJson;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsAPITest {

    private final SettingsService settings = Mockito.mock(SettingsService.class);

    // standaloneSetup installs no security filters, so this covers the payload only; that the path
    // is anonymous comes from SecurityConfig's /api/** permitAll.
    @Test
    void returnsTheSiteSettingsAsJson() throws Exception {
        var site = new SiteSettingsJson();
        site.setBannerEnabled(true);
        site.setBannerMessage("Maintenance tonight");
        site.setBannerSeverity("warning");
        Mockito.when(settings.getSiteSettings()).thenReturn(site);

        MockMvcBuilders.standaloneSetup(new SettingsAPI(settings))
                .build()
                .perform(get("/api/-/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerEnabled").value(true))
                .andExpect(jsonPath("$.bannerMessage").value("Maintenance tonight"))
                .andExpect(jsonPath("$.bannerSeverity").value("warning"))
                .andExpect(jsonPath("$.bannerDismissId").doesNotExist())
                // only banner properties are public, whatever else the admin payload carries
                .andExpect(jsonPath("$.length()").value(3));
    }
}
