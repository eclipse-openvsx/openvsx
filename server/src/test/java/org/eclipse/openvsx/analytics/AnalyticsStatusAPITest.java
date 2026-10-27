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
package org.eclipse.openvsx.analytics;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsStatusAPITest {

    private final AdminService admins = Mockito.mock(AdminService.class);
    private final RepositoryService repositories = Mockito.mock(RepositoryService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AnalyticsAvailability> availability = Mockito.mock(ObjectProvider.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsStatusAPI(admins, repositories, availability)).build();
        Mockito.when(repositories.countPendingAnalyticsDownloadIngestions()).thenReturn(4L);
        Mockito.when(repositories.countFailedDownloadIngestions()).thenReturn(2L);
    }

    @Test
    void reportsAnAvailableDatabase() throws Exception {
        var tracker = new AnalyticsAvailability(() -> {
        }, Duration.ofMinutes(10), 3);
        tracker.runProbe(false);
        Mockito.when(availability.getIfAvailable()).thenReturn(tracker);

        mockMvc.perform(get("/admin/analytics/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.lastProbe").exists())
                .andExpect(jsonPath("$.lastError").doesNotExist())
                .andExpect(jsonPath("$.pendingFiles").value(4))
                .andExpect(jsonPath("$.failedFiles").value(2));
        Mockito.verify(admins).checkPermission(Permission.VIEW_REPORTS);
    }

    @Test
    void reportsAnUnreachableDatabaseWithTheReason() throws Exception {
        var tracker = new AnalyticsAvailability(() -> {
            throw new DataAccessException("down", new SQLTransientConnectionException("pool timeout"));
        }, Duration.ofMinutes(10), 3);
        tracker.runProbe(false);
        Mockito.when(availability.getIfAvailable()).thenReturn(tracker);

        mockMvc.perform(get("/admin/analytics/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.lastError").value("SQLTransientConnectionException: pool timeout"));
    }

    @Test
    void saysSoWhenAnalyticsIsDisabled() throws Exception {
        Mockito.when(availability.getIfAvailable()).thenReturn(null);

        mockMvc.perform(get("/admin/analytics/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.available").doesNotExist())
                .andExpect(jsonPath("$.pendingFiles").value(4));
    }

    @Test
    void requiresThePermission() throws Exception {
        Mockito.when(admins.checkPermission(any(Permission.class)))
                .thenThrow(new ErrorResultException("forbidden", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/analytics/status")).andExpect(status().isForbidden());
    }
}
