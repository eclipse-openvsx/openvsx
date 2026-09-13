/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.admin;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.consistency.ConsistencyCheckService;
import org.eclipse.openvsx.consistency.ConsistencyCheckSummary;
import org.eclipse.openvsx.consistency.ConsistencyFinding;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = ConsistencyAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebUiProperties.class)
class ConsistencyAPITest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    ConsistencyCheckService service;

    @MockitoBean
    MeterRegistry meterRegistry;

    @Test
    void listChecks_requiresAdmin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/consistency").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void listChecks_returnsOneSummaryPerRegisteredCheck() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(service.listSummaries()).thenReturn(
                List.of(
                        new ConsistencyCheckSummary(
                                "extension-active-flag",
                                "Extension active flag",
                                "Extensions whose `active` flag disagrees with whether any of their versions is active.",
                                2)));

        mockMvc.perform(get("/admin/consistency").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks[0].id").value("extension-active-flag"))
                .andExpect(jsonPath("$.checks[0].currentFindingsCount").value(2));
    }

    @Test
    void findings_returnsNotFoundForUnknownCheck() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(service.findings("does-not-exist")).thenThrow(new NotFoundException());

        mockMvc.perform(get("/admin/consistency/does-not-exist/findings").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown consistency check: does-not-exist"));
    }

    @Test
    void findings_returnsLiveFindings() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(service.findings("extension-active-flag")).thenReturn(
                List.of(
                        new ConsistencyFinding(42L, "acme.foo", "marked active, but no version of it is active")));

        mockMvc.perform(get("/admin/consistency/extension-active-flag/findings").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].entityId").value(42))
                .andExpect(jsonPath("$.findings[0].label").value("acme.foo"));
    }

    @Test
    void fixAll_fixesEveryCurrentFinding() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(service.fixAll("extension-active-flag")).thenReturn(3);

        mockMvc.perform(post("/admin/consistency/extension-active-flag/fix").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("Fixed 3 finding(s) for check 'extension-active-flag'."));

        verify(service).fixAll("extension-active-flag");
    }

    @Test
    void fixOne_fixesTheGivenEntity() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());

        mockMvc.perform(post("/admin/consistency/extension-active-flag/fix/42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(service).fixOne(eq("extension-active-flag"), eq(42L));
    }

    private static UserData adminUser() {
        var user = new UserData();
        user.setRole(UserData.Role.ADMIN);
        return user;
    }
}
