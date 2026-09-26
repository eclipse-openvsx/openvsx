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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingActionResponseJson;
import org.eclipse.openvsx.json.NameSquattingActionResultJson;
import org.eclipse.openvsx.json.NameSquattingCountsJson;
import org.eclipse.openvsx.json.NameSquattingFlagJson;
import org.eclipse.openvsx.json.NameSquattingFlagListJson;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the HTTP layer of {@link NameSquattingAPI}: request mapping, parameter binding and
 * validation, the admin check, and serialization of what {@link AdminService} returns. The
 * moderation logic itself is covered by {@link NameSquattingAdminServiceTest}.
 */
@WebMvcTest(
    value = NameSquattingAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
class NameSquattingAPITest {

    private static final String TARGETS = "{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    MeterRegistry meterRegistry;

    @Test
    void getFlaggedExtensions_passes_the_query_on_and_returns_the_flags() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(
                admins.getNameSquattingFlags(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Integer.class),
                        any(Integer.class),
                        any()))
                .thenReturn(flagList());

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("publisher", "publisher")
                        .param("namespace", "ns")
                        .param("name", "ext")
                        .param("state", "PUBLISHED,DEACTIVATED")
                        .param("dateDetectedFrom", "2026-01-01T00:00Z")
                        .param("dateDetectedTo", "2026-02-01T00:00Z")
                        .param("size", "25")
                        .param("offset", "50")
                        .param("sortOrder", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(3))
                .andExpect(jsonPath("$.offset").value(50))
                .andExpect(jsonPath("$.flags.length()").value(1))
                .andExpect(jsonPath("$.flags[0].namespace").value("ns"))
                .andExpect(jsonPath("$.flags[0].extensionName").value("ext"))
                .andExpect(jsonPath("$.flags[0].state").value("PUBLISHED"));

        verify(admins).getNameSquattingFlags(
                eq("publisher"),
                eq("ns"),
                eq("ext"),
                eq(List.of("PUBLISHED", "DEACTIVATED")),
                eq("2026-01-01T00:00Z"),
                eq("2026-02-01T00:00Z"),
                eq(25),
                eq(50),
                eq("asc"));
    }

    @Test
    void getFlaggedExtensions_applies_the_paging_defaults() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(
                admins.getNameSquattingFlags(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Integer.class),
                        any(Integer.class),
                        any()))
                .thenReturn(flagList());

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(admins).getNameSquattingFlags(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(10),
                eq(0),
                eq("desc"));
    }

    @Test
    void getFlaggedExtensions_validates_paging_parameters() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("size", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be larger than 100"));

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("offset", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
    }

    @Test
    void getFlaggedExtensions_reports_a_rejected_query() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(
                admins.getNameSquattingFlags(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Integer.class),
                        any(Integer.class),
                        any()))
                .thenThrow(new ErrorResultException("Unknown state filter: SOMETHING", HttpStatus.BAD_REQUEST));

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("state", "PUBLISHED,SOMETHING")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown state filter: SOMETHING"));
    }

    @Test
    void getCounts_returns_the_breakdown_per_state() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());

        var counts = new NameSquattingCountsJson();
        counts.setTotal(9);
        counts.setPublished(4);
        counts.setDeactivated(2);
        counts.setRejected(3);
        when(admins.getNameSquattingCounts(any(), any(), any(), any(), any())).thenReturn(counts);

        mockMvc.perform(
                get("/admin/name-squatting/counts")
                        .param("publisher", "publisher")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.published").value(4))
                .andExpect(jsonPath("$.deactivated").value(2))
                .andExpect(jsonPath("$.rejected").value(3));

        verify(admins).getNameSquattingCounts(eq("publisher"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void clearFindings_hands_the_request_to_the_service() throws Exception {
        var adminUser = adminUser();
        when(admins.checkAdminUser()).thenReturn(adminUser);
        when(admins.clearNameSquattingFindings(eq(adminUser), any())).thenReturn(actionResponse("cleared"));

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARGETS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].message").value("cleared"));
    }

    @Test
    void clearFindings_reports_a_rejected_request() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(admins.clearNameSquattingFindings(any(), any()))
                .thenThrow(new ErrorResultException("At least one extension is required", HttpStatus.BAD_REQUEST));

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one extension is required"));
    }

    @Test
    void deleteExtensions_hands_the_request_to_the_service() throws Exception {
        var adminUser = adminUser();
        when(admins.checkAdminUser()).thenReturn(adminUser);
        when(admins.deleteNameSquattingExtensions(eq(adminUser), any())).thenReturn(actionResponse("deactivated"));

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARGETS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].message").value("deactivated"));
    }

    @Test
    void getFlaggedExtensions_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(admins, never()).getNameSquattingFlags(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any());
    }

    @Test
    void getCounts_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/name-squatting/counts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(admins, never()).getNameSquattingCounts(any(), any(), any(), any(), any());
    }

    @Test
    void moderation_actions_require_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARGETS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARGETS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(admins, never()).clearNameSquattingFindings(any(), any());
        verify(admins, never()).deleteNameSquattingExtensions(any(), any());
    }

    private static NameSquattingFlagListJson flagList() {
        var flag = new NameSquattingFlagJson();
        flag.setNamespace("ns");
        flag.setExtensionName("ext");
        flag.setState("PUBLISHED");

        var result = new NameSquattingFlagListJson();
        result.setOffset(50);
        result.setTotalSize(3);
        result.setFlags(List.of(flag));
        return result;
    }

    private static NameSquattingActionResponseJson actionResponse(String message) {
        var response = new NameSquattingActionResponseJson();
        response.setProcessed(1);
        response.setSuccessful(1);
        response.setFailed(0);
        response.setResults(List.of(NameSquattingActionResultJson.success("ns", "ext", message)));
        return response;
    }

    private static UserData adminUser() {
        var user = new UserData();
        user.setRole(UserData.Role.ADMIN);
        return user;
    }
}
