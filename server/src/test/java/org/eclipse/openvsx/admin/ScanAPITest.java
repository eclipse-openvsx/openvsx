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

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ScanStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScanAPI.class)
@AutoConfigureMockMvc(addFilters = false)
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
    org.eclipse.openvsx.scanning.ExtensionScanCompletionService completionService;

    @MockitoBean
    org.eclipse.openvsx.scanning.ScannerRegistry scannerRegistry;

    @MockitoBean
    org.eclipse.openvsx.repositories.ScannerJobRepository scanJobRepository;

    @Test
    void getScans_filters_sorting_and_pagination_are_applied() throws Exception {
        // Always allow the request to pass the admin gate in this test setup.
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        // Build scan with display name for the sorted/filtered result.
        var scanC = TestData.scan(3, "gamma", "third", "2.0.0", "alpha-team", ScanStatus.VALIDATING, LocalDateTime.of(2024, 12, 3, 10, 0));
        scanC.setExtensionDisplayName("Alpha Utility");

        // Mock the DB-level filtered/paginated query to return just the expected result.
        // The DB does filtering and pagination, so tests now verify correct parameters are passed.
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanC), org.springframework.data.domain.PageRequest.of(0, 1), 2));
        
        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        // Provide display name from linked version
        Mockito.when(repositories.findVersion("2.0.0", "universal", "third", "gamma")).thenReturn(TestData.version(12, "Alpha Utility"));

        mockMvc.perform(get("/admin/scans")
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
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData.scan(1, "alpha-ns", "ext-a", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB-level filtering returns only the matching scan
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanA)));
        
        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        mockMvc.perform(get("/admin/scans")
                .param("namespace", "alp")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSize").value(1))
            .andExpect(jsonPath("$.scans.length()").value(1))
            .andExpect(jsonPath("$.scans[0].namespace").value("alpha-ns"));
    }

    @Test
    void getScans_name_matches_extensionName_and_displayName_partial() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData.scan(1, "alpha-ns", "alpha-one", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));
        scanA.setExtensionDisplayName("Zebra Toolkit");
        var scanB = TestData.scan(2, "beta-ns", "beta-two", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));
        scanB.setExtensionDisplayName("Something Else");

        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        Mockito.when(repositories.findVersion("1.0.0", "universal", "alpha-one", "alpha-ns")).thenReturn(TestData.version(10, "Zebra Toolkit"));
        Mockito.when(repositories.findVersion("1.0.0", "universal", "beta-two", "beta-ns")).thenReturn(TestData.version(11, "Something Else"));

        // First request: DB returns scanA which matches displayName "Toolkit"
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanA)));

        // Match by displayName partial (case-insensitive)
        mockMvc.perform(get("/admin/scans")
                .param("name", "tool")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSize").value(1))
            .andExpect(jsonPath("$.scans[0].extensionName").value("alpha-one"));

        // Second request: DB returns scanB which matches extensionName "beta"
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanB)));

        // Match by extensionName partial
        mockMvc.perform(get("/admin/scans")
                .param("name", "bet")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSize").value(1))
            .andExpect(jsonPath("$.scans[0].extensionName").value("beta-two"));
    }

    @Test
    void getScans_status_supports_comma_separated_values() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanPassed = TestData.scan(2, "ns", "ext-passed", "1.0.0", "pub", ScanStatus.PASSED, LocalDateTime.of(2024, 12, 1, 10, 0));
        var scanErrored = TestData.scan(3, "ns", "ext-error", "1.0.0", "pub", ScanStatus.ERRORED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB returns only PASSED and ERRORED scans (filtered by status)
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanPassed, scanErrored)));
        
        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        // explode=false behavior: status=PASSED,ERROR should be parsed into a list of two values.
        mockMvc.perform(get("/admin/scans")
                .param("status", "PASSED,ERROR")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSize").value(2))
            .andExpect(jsonPath("$.scans.length()").value(2));
    }

    @Test
    void getScans_checkType_supports_comma_separated_values() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scanA = TestData.scan(1, "ns", "ext-a", "1.0.0", "pub", ScanStatus.REJECTED, LocalDateTime.of(2024, 12, 1, 10, 0));

        // DB returns only scanA (filtered by checkType)
        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scanA)));

        Mockito.when(repositories.findVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(null);
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        // scanA has a validation failure with checkType NAME_SQUATTING
        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenAnswer(invocation -> {
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
        mockMvc.perform(get("/admin/scans")
                .param("validationType", "BLOCKLIST,NAME SQUATTING")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSize").value(1))
            .andExpect(jsonPath("$.scans.length()").value(1))
            .andExpect(jsonPath("$.scans[0].extensionName").value("ext-a"));
    }

    @Test
    void getScanFilterOptions_returns_validationTypes() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        Mockito.when(repositories.findDistinctValidationFailureCheckTypes()).thenReturn(java.util.List.of("NAME_SQUATTING", "BLOCKLIST"));

        mockMvc.perform(get("/admin/scans/filterOptions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validationTypes.length()").value(2))
            .andExpect(jsonPath("$.validationTypes[0]").value("NAME_SQUATTING"))
            .andExpect(jsonPath("$.validationFailureNames").doesNotExist());
    }

    @Test
    void getScans_rejects_unknown_sort_field() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        Mockito.when(repositories.findAllExtensionScans()).thenReturn(Streamable.empty());

        mockMvc.perform(get("/admin/scans")
                .param("sortBy", "unknownField")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("Unsupported sortBy value: unknownField"));
    }

    @Test
    void getScanCounts_returns_status_counts_and_zero_decisions() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.STARTED)).thenReturn(1L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.VALIDATING)).thenReturn(2L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.SCANNING)).thenReturn(3L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.PASSED)).thenReturn(4L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.QUARANTINED)).thenReturn(5L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.REJECTED)).thenReturn(6L);
        Mockito.when(repositories.countExtensionScansByStatus(ScanStatus.ERRORED)).thenReturn(7L);

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
            .andExpect(jsonPath("$.NEEDS_REVIEW").value(5))  // QUARANTINED(5) - ALLOWED(0) - BLOCKED(0)
            .andExpect(jsonPath("$.started").doesNotExist())
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void getScanCounts_supports_enforcement_filtering() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        // DB-level enforcement filtering: mock counts for each status with enforcement
        // When enforcement filter is applied, the code uses countScansForStatistics
        // First request: enforced=true -> returns 1 for REJECTED
        Mockito.when(repositories.countScansForStatistics(
            Mockito.eq(ScanStatus.REJECTED), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true)
        )).thenReturn(1L);
        Mockito.when(repositories.countScansForStatistics(
            Mockito.eq(ScanStatus.REJECTED), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(false)
        )).thenReturn(1L);
        
        // Other statuses return 0 when enforcement filter is applied
        Mockito.when(repositories.countScansForStatistics(
            Mockito.argThat(s -> s != ScanStatus.REJECTED), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()
        )).thenReturn(0L);

        mockMvc.perform(get("/admin/scans/counts")
                .param("enforcement", "enforced")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.AUTO_REJECTED").value(1));

        mockMvc.perform(get("/admin/scans/counts")
                .param("enforcement", "notEnforced")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.AUTO_REJECTED").value(1));
    }

    @Test
    void getScans_returns_displayName_from_scan_when_version_missing() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var scan = TestData.scan(99, "ns", "ext", "0.0.1", "pub", ScanStatus.REJECTED, LocalDateTime.of(2024, 12, 4, 10, 0));
        scan.setExtensionDisplayName("Manifest Display");

        Mockito.when(repositories.findScansFullyFiltered(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()
        )).thenReturn(new PageImpl<>(List.of(scan)));
        
        Mockito.when(repositories.findVersion("0.0.1", "universal", "ext", "ns")).thenReturn(null);
        Mockito.when(repositories.findValidationFailures(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(repositories.findExtensionThreats(Mockito.any())).thenReturn(Streamable.empty());
        Mockito.when(storageUtil.getFileUrls(Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Map.of());

        mockMvc.perform(get("/admin/scans").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scans[0].displayName").value("Manifest Display"))
            .andExpect(jsonPath("$.scans[0].extensionName").value("ext"))
            .andExpect(jsonPath("$.scans[0].targetPlatform").value("universal"))
            .andExpect(jsonPath("$.scans[0].universalTargetPlatform").value(true));
    }

    @Test
    void getScanCounts_requires_admin() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/scans/counts").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void getScans_requires_admin() throws Exception {
        Mockito.when(admins.checkAdminUser()).thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/scans").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    private static class TestData {

        static ExtensionScan scan(long id, String namespace, String name, String version, String publisher, ScanStatus status, LocalDateTime startedAt) {
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

        static org.eclipse.openvsx.entities.UserData adminUser() {
            var user = new org.eclipse.openvsx.entities.UserData();
            user.setRole(org.eclipse.openvsx.entities.UserData.ROLE_ADMIN);
            return user;
        }
    }
}

