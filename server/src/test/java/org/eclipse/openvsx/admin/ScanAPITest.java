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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.scanning.ExtensionScanCompletionService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.scanning.ScannerRegistry;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ScanAPI.class, excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
@Import(WebUiProperties.class)
class ScanAPITest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    LogService logs;

    @MockitoBean
    StorageUtilService storageUtil;

    @MockitoBean
    MeterRegistry meterRegistry;

    @MockitoBean
    ExtensionScanCompletionService completionService;

    @MockitoBean
    ScannerRegistry scannerRegistry;

    @MockitoBean
    ScannerJobRepository scanJobRepository;

    @MockitoBean
    ExtensionScanService scanService;

    @Test
    void getScans_pagination_validation_failures() throws Exception {
        // Always allow the request to pass the admin gate in this test setup.
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                get("/admin/scans")
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
                get("/admin/scans")
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
                get("/admin/scans")
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

    @Test
    void getScans_filters_sorting_and_pagination_are_applied() throws Exception {
        // Always allow the request to pass the admin gate in this test setup.
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        // Build scan with display name for the sorted/filtered result.
        var scanC = TestData.scan(
                3,
                "gamma",
                "third",
                "2.0.0",
                "alpha-team",
                ScanStatus.VALIDATING,
                LocalDateTime.of(2024, 12, 3, 10, 0));
        scanC.setExtensionDisplayName("Alpha Utility");

        // Mock the DB-level filtered/paginated query to return just the expected result.
        // The DB does filtering and pagination, so tests now verify correct parameters are passed.
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanC), PageRequest.of(0, 1), 2));

        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        // Provide display name from linked version
        when(repositories.findVersion("2.0.0", "universal", "third", "gamma"))
                .thenReturn(TestData.version(12, "Alpha Utility"));

        mockMvc.perform(
                get("/admin/scans")
                        .param("status", "VALIDATING")
                        .param("publisher", "alpha")
                        .param("namespace", "a")
                        .param("name", "a")
                        .param("size", "1")
                        .param("offset", "0")
                        .param("sortBy", "displayName")
                        .param("sortOrder", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(2))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.scans.length()").value(1))
                .andExpect(jsonPath("$.scans[0].displayName").value("Alpha Utility"))
                .andExpect(jsonPath("$.scans[0].extensionName").value("third"))
                .andExpect(jsonPath("$.scans[0].targetPlatform").value("universal"))
                .andExpect(jsonPath("$.scans[0].universalTargetPlatform").value(true))
                .andExpect(jsonPath("$.scans[0].status").value("VALIDATING"));
    }

    @Test
    void getScans_namespace_partial_match_is_applied() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData
                .scan(1, "alpha-ns", "ext-a", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB-level filtering returns only the matching scan
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanA)));

        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(repositories.findVersion(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        mockMvc.perform(
                get("/admin/scans")
                        .param("namespace", "alp")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(1))
                .andExpect(jsonPath("$.scans.length()").value(1))
                .andExpect(jsonPath("$.scans[0].namespace").value("alpha-ns"));
    }

    @Test
    void getScans_name_matches_extensionName_and_displayName_partial() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData.scan(
                1,
                "alpha-ns",
                "alpha-one",
                "1.0.0",
                "pub",
                ScanStatus.PASSED,
                LocalDateTime.of(2024, 12, 1, 10, 0));
        scanA.setExtensionDisplayName("Zebra Toolkit");
        var scanB = TestData.scan(
                2,
                "beta-ns",
                "beta-two",
                "1.0.0",
                "pub",
                ScanStatus.PASSED,
                LocalDateTime.of(2024, 12, 1, 10, 0));
        scanB.setExtensionDisplayName("Something Else");

        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        when(repositories.findVersion("1.0.0", "universal", "alpha-one", "alpha-ns"))
                .thenReturn(TestData.version(10, "Zebra Toolkit"));
        when(repositories.findVersion("1.0.0", "universal", "beta-two", "beta-ns"))
                .thenReturn(TestData.version(11, "Something Else"));

        // First request: DB returns scanA which matches displayName "Toolkit"
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanA)));

        // Match by displayName partial (case-insensitive)
        mockMvc.perform(
                get("/admin/scans")
                        .param("name", "tool")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(1))
                .andExpect(jsonPath("$.scans[0].extensionName").value("alpha-one"));

        // Second request: DB returns scanB which matches extensionName "beta"
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanB)));

        // Match by extensionName partial
        mockMvc.perform(
                get("/admin/scans")
                        .param("name", "bet")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(1))
                .andExpect(jsonPath("$.scans[0].extensionName").value("beta-two"));
    }

    @Test
    void getScans_status_supports_comma_separated_values() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanPassed = TestData
                .scan(2, "ns", "ext-passed", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));
        var scanErrored = TestData
                .scan(3, "ns", "ext-error", "1.0.0", "pub", ScanStatus.ERRORED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB returns only PASSED and ERRORED scans (filtered by status)
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanPassed, scanErrored)));

        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(repositories.findVersion(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        // explode=false behavior: status=PASSED,ERROR should be parsed into a list of two values.
        mockMvc.perform(
                get("/admin/scans")
                        .param("status", "PASSED,ERROR")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(2))
                .andExpect(jsonPath("$.scans.length()").value(2));
    }

    @Test
    void getScans_checkType_supports_comma_separated_values() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData
                .scan(1, "ns", "ext-a", "1.0.0", "pub", ScanStatus.REJECTED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB returns only scanA (filtered by checkType)
        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scanA)));

        when(repositories.findVersion(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        // scanA has a validation failure with checkType NAME_SQUATTING
        when(repositories.findValidationFailures(any())).thenAnswer(invocation -> {
            var scan = (ExtensionScan) invocation.getArgument(0);
            if (scan.getId() == 1) {
                var failure = ExtensionValidationFailure.create("NAME_SQUATTING", "any-name", "reason");
                failure.setEnforced(true);
                failure.setScan(scanA);
                return Streamable.of(failure);
            }
            return Streamable.empty();
        });

        // Validates CSV parsing: "BLOCKLIST,NAME SQUATTING" -> ["BLOCKLIST", "NAME SQUATTING"]
        mockMvc.perform(
                get("/admin/scans")
                        .param("validationType", "BLOCKLIST,NAME SQUATTING")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(1))
                .andExpect(jsonPath("$.scans.length()").value(1))
                .andExpect(jsonPath("$.scans[0].extensionName").value("ext-a"));
    }

    @Test
    void getScanFilterOptions_returns_validationTypes() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        when(repositories.findDistinctValidationFailureCheckTypes())
                .thenReturn(java.util.List.of("NAME_SQUATTING", "BLOCKLIST"));

        mockMvc.perform(get("/admin/scans/filterOptions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationTypes.length()").value(2))
                .andExpect(jsonPath("$.validationTypes[0]").value("NAME_SQUATTING"))
                .andExpect(jsonPath("$.validationFailureNames").doesNotExist());
    }

    @Test
    void getScans_rejects_unknown_sort_field() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                get("/admin/scans")
                        .param("sortBy", "unknownField")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unsupported sortBy value: unknownField"));
    }

    @Test
    void getScanCounts_returns_status_counts_and_zero_decisions() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        when(repositories.countExtensionScansByStatus(ScanStatus.STARTED)).thenReturn(1L);
        when(repositories.countExtensionScansByStatus(ScanStatus.VALIDATING)).thenReturn(2L);
        when(repositories.countExtensionScansByStatus(ScanStatus.SCANNING)).thenReturn(3L);
        when(repositories.countExtensionScansByStatus(ScanStatus.PASSED)).thenReturn(4L);
        when(repositories.countExtensionScansByStatus(ScanStatus.QUARANTINED)).thenReturn(5L);
        when(repositories.countExtensionScansByStatus(ScanStatus.REJECTED)).thenReturn(6L);
        when(repositories.countExtensionScansByStatus(ScanStatus.ERRORED)).thenReturn(7L);

        // Default behavior (no filters): uses the fast count-by-status repository calls.
        mockMvc.perform(get("/admin/scans/counts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.STARTED").value(1))
                .andExpect(jsonPath("$.VALIDATING").value(2))
                .andExpect(jsonPath("$.SCANNING").value(3))
                .andExpect(jsonPath("$.PASSED").value(4))
                .andExpect(jsonPath("$.QUARANTINED").value(5))
                .andExpect(jsonPath("$.AUTO_REJECTED").value(6))
                .andExpect(jsonPath("$.ERROR").value(7))
                .andExpect(jsonPath("$.ALLOWED").value(0))
                .andExpect(jsonPath("$.BLOCKED").value(0))
                .andExpect(jsonPath("$.NEEDS_REVIEW").value(5)) // QUARANTINED(5) - ALLOWED(0) - BLOCKED(0)
                .andExpect(jsonPath("$.started").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void getScanCounts_supports_enforcement_filtering() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        // DB-level enforcement filtering: mock counts for each status with enforcement
        // When enforcement filter is applied, the code uses countScansForStatistics
        // First request: enforced=true -> returns 1 for REJECTED
        when(
                repositories.countScansForStatistics(
                        eq(ScanStatus.REJECTED),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(true)))
                .thenReturn(1L);
        when(
                repositories.countScansForStatistics(
                        eq(ScanStatus.REJECTED),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(false)))
                .thenReturn(1L);

        // Other statuses return 0 when enforcement filter is applied
        when(
                repositories.countScansForStatistics(
                        argThat(s -> s != ScanStatus.REJECTED),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean()))
                .thenReturn(0L);

        mockMvc.perform(
                get("/admin/scans/counts")
                        .param("enforcement", "enforced")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AUTO_REJECTED").value(1));

        mockMvc.perform(
                get("/admin/scans/counts")
                        .param("enforcement", "notEnforced")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AUTO_REJECTED").value(1));
    }

    @Test
    void getScans_returns_displayName_from_scan_when_version_missing() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scan = TestData
                .scan(99, "ns", "ext", "0.0.1", "pub", ScanStatus.REJECTED, LocalDateTime.of(2024, 12, 4, 10, 0));
        scan.setExtensionDisplayName("Manifest Display");

        when(
                repositories.findScansFullyFiltered(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(scan)));

        when(repositories.findVersion("0.0.1", "universal", "ext", "ns")).thenReturn(null);
        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        mockMvc.perform(get("/admin/scans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans[0].displayName").value("Manifest Display"))
                .andExpect(jsonPath("$.scans[0].extensionName").value("ext"))
                .andExpect(jsonPath("$.scans[0].targetPlatform").value("universal"))
                .andExpect(jsonPath("$.scans[0].universalTargetPlatform").value(true));
    }

    @Test
    void getScanCounts_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/scans/counts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getScans_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/scans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryFailedScannerJobs_returns200_andDelegatesToService() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        var scan = TestData
                .scan(5, "ns", "ext", "1.0.0", "pub", ScanStatus.ERRORED, LocalDateTime.of(2024, 12, 1, 10, 0));
        when(repositories.findExtensionScan(5L)).thenReturn(scan);
        when(scanService.retryFailedJobs(scan)).thenReturn(scan);
        when(repositories.findVersion(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        when(repositories.findValidationFailures(any())).thenReturn(Streamable.empty());
        when(repositories.findExtensionThreats(any())).thenReturn(Streamable.empty());
        when(storageUtil.getFileUrls(anyList(), anyString(), any(), any())).thenReturn(Map.of());

        mockMvc.perform(post("/admin/scans/5/jobs/retry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(scanService).retryFailedJobs(scan);
    }

    @Test
    void retryFailedScannerJobs_returns404_whenScanNotFound() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        when(repositories.findExtensionScan(99L)).thenReturn(null);

        mockMvc.perform(post("/admin/scans/99/jobs/retry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void retryFailedScannerJobs_returns400_whenServiceRejectsRequest() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        var scan = TestData
                .scan(3, "ns", "ext", "1.0.0", "pub", ScanStatus.SCANNING, LocalDateTime.of(2024, 12, 1, 10, 0));
        when(repositories.findExtensionScan(3L)).thenReturn(scan);
        when(scanService.retryFailedJobs(scan))
                .thenThrow(new ErrorResultException("Cannot retry: scan is not terminal", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/admin/scans/3/jobs/retry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryFailedScannerJobs_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/admin/scans/1/jobs/retry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    private static class TestData {

        static ExtensionScan scan(
                long id,
                String namespace,
                String name,
                String version,
                String publisher,
                ScanStatus status,
                LocalDateTime startedAt
        ) {
            var scan = new ExtensionScan();
            scan.setId(id);
            scan.setNamespaceName(namespace);
            scan.setExtensionName(name);
            scan.setExtensionVersion(version);
            scan.setTargetPlatform("universal");
            scan.setUniversalTargetPlatform(true);
            scan.setPublisher(publisher);
            scan.setStartedAt(startedAt);
            scan.setStatus(status);
            return scan;
        }

        static ExtensionVersion version(long id, String displayName) {
            var version = new ExtensionVersion();
            version.setId(id);
            version.setDisplayName(displayName);
            return version;
        }

        static UserData adminUser() {
            var user = new UserData();
            user.setRole(UserData.Role.ADMIN);
            return user;
        }
    }
}
