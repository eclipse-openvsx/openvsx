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

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = NameSquattingAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
class NameSquattingAPITest {

    private static final String CHECK_TYPE = "NAME_SQUATTING";

    private static final LocalDateTime FIRST_DETECTED = LocalDateTime.of(2026, 1, 5, 9, 30);

    private static final LocalDateTime LAST_DETECTED = LocalDateTime.of(2026, 2, 11, 14, 0);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    AdminService admins;

    @MockitoBean
    LogService logs;

    @MockitoBean
    MeterRegistry meterRegistry;

    @Test
    void getFlaggedExtensions_groups_findings_per_extension() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        stubFlaggedExtension("ns", "ext");

        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(TestData.version("universal", "1.0.0")));

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(3))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.flags.length()").value(1))
                .andExpect(jsonPath("$.flags[0].namespace").value("ns"))
                .andExpect(jsonPath("$.flags[0].extensionName").value("ext"))
                .andExpect(jsonPath("$.flags[0].displayName").value("Extension Display Name"))
                .andExpect(jsonPath("$.flags[0].publisher").value("publisher"))
                .andExpect(jsonPath("$.flags[0].state").value("PUBLISHED"))
                .andExpect(jsonPath("$.flags[0].activeVersionCount").value(1))
                .andExpect(jsonPath("$.flags[0].findingCount").value(2))
                .andExpect(jsonPath("$.flags[0].findings.length()").value(2))
                // Findings come back newest first, so the dates bracket the whole history.
                .andExpect(jsonPath("$.flags[0].findings[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.flags[0].findings[0].scanStatus").value("PASSED"))
                .andExpect(jsonPath("$.flags[0].findings[0].enforcedFlag").value(false))
                .andExpect(jsonPath("$.flags[0].findings[1].version").value("1.0.0"))
                .andExpect(jsonPath("$.flags[0].dateLastDetected").value("2026-02-11T14:00Z"))
                .andExpect(jsonPath("$.flags[0].dateFirstDetected").value("2026-01-05T09:30Z"));
    }

    @Test
    void getFlaggedExtensions_reports_rejected_when_extension_was_never_created() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        stubFlaggedExtension("ns", "ext");

        // Publication was blocked by an enforced check, so no extension exists.
        when(repositories.findExtension("ext", "ns")).thenReturn(null);

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].state").value("REJECTED"))
                .andExpect(jsonPath("$.flags[0].activeVersionCount").value(0));
    }

    @Test
    void getFlaggedExtensions_reports_deactivated_when_no_active_versions_remain() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        stubFlaggedExtension("ns", "ext");

        var extension = TestData.extension("ns", "ext", false);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension)).thenReturn(Streamable.empty());

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].state").value("DEACTIVATED"))
                .andExpect(jsonPath("$.flags[0].activeVersionCount").value(0));
    }

    @Test
    void getFlaggedExtensions_validates_paging_parameters() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

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
    void getFlaggedExtensions_rejects_unknown_state_filter() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("state", "PUBLISHED,SOMETHING")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown state filter: SOMETHING"));
    }

    @Test
    void getFlaggedExtensions_rejects_unknown_sort_order() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                get("/admin/name-squatting")
                        .param("sortOrder", "sideways")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported sortOrder value: sideways"));
    }

    @Test
    void getCounts_breaks_down_flagged_extensions_by_state() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        when(repositories.countFlaggedExtensions(eq(CHECK_TYPE), any(), any(), any(), any(), any(), eq(null)))
                .thenReturn(9L);
        when(
                repositories.countFlaggedExtensions(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(new NameSquattingAPI.ExtensionStateFilter(true, false, false))))
                .thenReturn(4L);
        when(
                repositories.countFlaggedExtensions(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(new NameSquattingAPI.ExtensionStateFilter(false, true, false))))
                .thenReturn(2L);
        when(
                repositories.countFlaggedExtensions(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(new NameSquattingAPI.ExtensionStateFilter(false, false, true))))
                .thenReturn(3L);

        mockMvc.perform(get("/admin/name-squatting/counts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.published").value(4))
                .andExpect(jsonPath("$.deactivated").value(2))
                .andExpect(jsonPath("$.rejected").value(3));
    }

    @Test
    void clearFindings_removes_the_records_and_logs_the_action() throws Exception {
        var adminUser = TestData.adminUser();
        when(admins.checkAdminUser()).thenReturn(adminUser);
        when(repositories.deleteValidationFailures(CHECK_TYPE, "ns", "ext")).thenReturn(2);

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(
                        jsonPath("$.results[0].message")
                                .value("Cleared 2 name squatting findings for extension ns.ext as a false positive"));

        verify(repositories).deleteValidationFailures(CHECK_TYPE, "ns", "ext");
        verify(logs).logAction(eq(adminUser), any(ResultJson.class));
    }

    @Test
    void clearFindings_reports_a_failure_when_nothing_is_recorded() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        when(repositories.deleteValidationFailures(CHECK_TYPE, "ns", "ext")).thenReturn(0);

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(
                        jsonPath("$.results[0].error")
                                .value("No name squatting findings are recorded for this extension"));

        verify(logs, never()).logAction(any(), any());
    }

    @Test
    void clearFindings_requires_at_least_one_extension() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one extension is required"));
    }

    @Test
    void clearFindings_rejects_targets_without_a_name() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error").value("Each extension must have a namespace and an extension name"));
    }

    @Test
    void deleteExtensions_deactivates_every_active_version() throws Exception {
        var adminUser = TestData.adminUser();
        when(admins.checkAdminUser()).thenReturn(adminUser);

        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(
                        Streamable.of(
                                TestData.version("universal", "1.0.0"),
                                TestData.version("linux-x64", "2.0.0")));
        when(admins.deleteExtensionNoWait(any(), anyString(), anyString(), any(TargetPlatformVersion[].class)))
                .thenReturn(ResultJson.success("deleted"));

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(
                        jsonPath("$.results[0].message")
                                .value("Deactivated 2 versions of extension ns.ext flagged for name squatting"));

        verify(admins).deleteExtensionNoWait(
                eq(adminUser),
                eq("ns"),
                eq("ext"),
                eq(new TargetPlatformVersion("universal", "1.0.0")),
                eq(new TargetPlatformVersion("linux-x64", "2.0.0")));
        verify(logs).logAction(eq(adminUser), any(ResultJson.class));
    }

    @Test
    void deleteExtensions_refuses_an_extension_that_was_never_created() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());
        when(repositories.findExtension("ext", "ns")).thenReturn(null);

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(
                        jsonPath("$.results[0].error")
                                .value("Extension does not exist, its publication was blocked by the check"));

        verify(admins, never()).deleteExtensionNoWait(any(), anyString(), anyString());
    }

    @Test
    void deleteExtensions_refuses_an_extension_that_is_already_deactivated() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var extension = TestData.extension("ns", "ext", false);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension)).thenReturn(Streamable.empty());

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(
                        jsonPath("$.results[0].error")
                                .value("Extension has no active versions left to deactivate"));
    }

    @Test
    void deleteExtensions_reports_why_a_deletion_was_refused() throws Exception {
        when(admins.checkAdminUser()).thenReturn(TestData.adminUser());

        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(TestData.version("universal", "1.0.0")));
        when(admins.deleteExtensionNoWait(any(), anyString(), anyString(), any(TargetPlatformVersion[].class)))
                .thenThrow(
                        new ErrorResultException(
                                "Extension is bundled by other extensions",
                                HttpStatus.BAD_REQUEST));

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].error").value("Extension is bundled by other extensions"));
    }

    @Test
    void getFlaggedExtensions_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/name-squatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCounts_requires_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/admin/name-squatting/counts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderation_actions_require_admin() throws Exception {
        when(admins.checkAdminUser())
                .thenThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN));

        mockMvc.perform(
                post("/admin/name-squatting/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                post("/admin/name-squatting/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"namespace\":\"ns\",\"extension\":\"ext\"}]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(repositories, never()).deleteValidationFailures(anyString(), anyString(), anyString());
    }

    /**
     * Stub one flagged extension with two findings, newest first, as the repository returns them.
     */
    private void stubFlaggedExtension(String namespace, String extensionName) {
        when(
                repositories.findFlaggedExtensionKeys(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        anyInt()))
                .thenReturn(List.of(namespace + "/" + extensionName));
        when(repositories.countFlaggedExtensions(eq(CHECK_TYPE), any(), any(), any(), any(), any(), any()))
                .thenReturn(3L);
        when(repositories.findValidationFailures(eq(CHECK_TYPE), eq(namespace), eq(extensionName), any(), any()))
                .thenReturn(
                        List.of(
                                TestData.failure(
                                        2,
                                        TestData.scan(20, namespace, extensionName, "2.0.0", ScanStatus.PASSED),
                                        LAST_DETECTED,
                                        false),
                                TestData.failure(
                                        1,
                                        TestData.scan(10, namespace, extensionName, "1.0.0", ScanStatus.REJECTED),
                                        FIRST_DETECTED,
                                        true)));
    }

    private static class TestData {

        static ExtensionScan scan(long id, String namespace, String name, String version, ScanStatus status) {
            var scan = new ExtensionScan();
            scan.setId(id);
            scan.setNamespaceName(namespace);
            scan.setExtensionName(name);
            scan.setExtensionDisplayName("Extension Display Name");
            scan.setExtensionVersion(version);
            scan.setTargetPlatform("universal");
            scan.setUniversalTargetPlatform(true);
            scan.setPublisher("publisher");
            scan.setPublisherUrl("https://example.com/publisher");
            scan.setStartedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            scan.setStatus(status);
            return scan;
        }

        static ExtensionValidationFailure failure(
                long id,
                ExtensionScan scan,
                LocalDateTime detectedAt,
                boolean enforced
        ) {
            var failure = ExtensionValidationFailure
                    .create(CHECK_TYPE, "Levenshtein Distance", "Too similar to an existing extension");
            failure.setId(id);
            failure.setScan(scan);
            failure.setDetectedAt(detectedAt);
            failure.setEnforced(enforced);
            return failure;
        }

        static Extension extension(String namespaceName, String name, boolean active) {
            var namespace = new Namespace();
            namespace.setName(namespaceName);
            var extension = new Extension();
            extension.setName(name);
            extension.setNamespace(namespace);
            extension.setActive(active);
            return extension;
        }

        static ExtensionVersion version(String targetPlatform, String version) {
            var extVersion = new ExtensionVersion();
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setVersion(version);
            return extVersion;
        }

        static UserData adminUser() {
            var user = new UserData();
            user.setRole(UserData.Role.ADMIN);
            return user;
        }
    }
}
