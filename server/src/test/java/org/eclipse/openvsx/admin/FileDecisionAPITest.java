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

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = FileDecisionAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebUiProperties.class)
class FileDecisionAPITest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    MeterRegistry meterRegistry;

    @Test
    void getFiles_pagination_validation_failures() throws Exception {
        // Always allow the request to pass the admin gate in this test setup.
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                get("/admin/scans/files")
                        .param("status", "VALIDATING")
                        .param("publisher", "alpha")
                        .param("namespace", "a")
                        .param("name", "a")
                        .param("size", "-1")
                        .param("offset", "0")
                        .param("sortBy", "displayName")
                        .param("sortOrder", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(
                get("/admin/scans/files")
                        .param("status", "VALIDATING")
                        .param("publisher", "alpha")
                        .param("namespace", "a")
                        .param("name", "a")
                        .param("size", "9999")
                        .param("offset", "0")
                        .param("sortBy", "displayName")
                        .param("sortOrder", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be larger than 100"));

        mockMvc.perform(
                get("/admin/scans/files")
                        .param("status", "VALIDATING")
                        .param("publisher", "alpha")
                        .param("namespace", "a")
                        .param("name", "a")
                        .param("size", "100")
                        .param("offset", "-1")
                        .param("sortBy", "displayName")
                        .param("sortOrder", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
    }

    private static class TestData {
        static org.eclipse.openvsx.entities.UserData adminUser() {
            var user = new org.eclipse.openvsx.entities.UserData();
            user.setRole(org.eclipse.openvsx.entities.UserData.Role.ADMIN);
            return user;
        }
    }
}
