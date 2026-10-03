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
package org.eclipse.openvsx.admin;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = ExtensionSizeOverrideAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
class ExtensionSizeOverrideAPITest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    ExtensionSizeLimitService limits;

    @MockitoBean
    LogService logs;

    @MockitoBean
    MeterRegistry meterRegistry;

    @Test
    void listReturnsEveryOverride() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(limits.listOverrides()).thenReturn(List.of(override("foo", null, 100L)));

        mockMvc.perform(get("/admin/size-overrides").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeOverrides[0].namespace").value("foo"))
                .andExpect(jsonPath("$.sizeOverrides[0].maxSize").value(100))
                .andExpect(jsonPath("$.sizeOverrides[0].extension").doesNotExist());
    }

    @Test
    void listRequiresAdmin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/size-overrides").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPersistsTheOverrideAndAuditsIt() throws Exception {
        var admin = adminUser();
        when(admins.checkAdminUser()).thenReturn(admin);
        when(limits.createOverride("foo", null, 100L)).thenReturn(override("foo", null, 100L));

        mockMvc.perform(
                post("/admin/size-overrides/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"foo\",\"maxSize\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    @Test
    void createSurfacesAValidationFailureAsBadRequest() throws Exception {
        when(admins.checkAdminUser()).thenReturn(adminUser());
        when(limits.createOverride(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenThrow(new ErrorResultException("Unknown namespace: nope", HttpStatus.BAD_REQUEST));

        mockMvc.perform(
                post("/admin/size-overrides/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"nope\",\"maxSize\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown namespace: nope"));

        Mockito.verify(logs, Mockito.never()).logAction(Mockito.any(), Mockito.any());
    }

    @Test
    void updateChangesTheSize() throws Exception {
        var admin = adminUser();
        when(admins.checkAdminUser()).thenReturn(admin);
        when(limits.updateOverride(1L, 200L)).thenReturn(override("foo", null, 200L));

        mockMvc.perform(
                put("/admin/size-overrides/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxSize\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSize").value(200));

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    @Test
    void deleteRemovesTheOverride() throws Exception {
        var admin = adminUser();
        when(admins.checkAdminUser()).thenReturn(admin);
        when(limits.deleteOverride(1L)).thenReturn(override("foo", "bar", 100L));

        mockMvc.perform(delete("/admin/size-overrides/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }

    private UserData adminUser() {
        var userData = new UserData();
        userData.setLoginName("admin_user");
        userData.setFullName("Admin User");
        userData.setRole(UserData.Role.ADMIN);
        return userData;
    }

    private ExtensionSizeOverride override(String namespaceName, @Nullable String extensionName, long maxSize) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        if (extensionName != null) {
            var extension = new Extension();
            extension.setName(extensionName);
            extension.setNamespace(namespace);
            override.setScopeExtension(extension);
        }
        override.setMaxSize(maxSize);
        return override;
    }
}
