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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingActionRequest;
import org.eclipse.openvsx.json.NameSquattingTargetJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the name squatting moderation logic in {@link AdminService}: grouping the recorded
 * findings per extension, counting them per state, and the two moderation actions.
 */
@ExtendWith(MockitoExtension.class)
class NameSquattingAdminServiceTest {

    private static final String CHECK_TYPE = "NAME_SQUATTING";

    private static final LocalDateTime FIRST_DETECTED = LocalDateTime.of(2026, 1, 5, 9, 30);

    private static final LocalDateTime LAST_DETECTED = LocalDateTime.of(2026, 2, 11, 14, 0);

    @Mock
    RepositoryService repositories;

    @Mock
    ExtensionService extensions;

    @Mock
    LogService logs;

    @InjectMocks
    AdminService admins;

    private final UserData adminUser = TestData.adminUser();

    @Test
    void getNameSquattingFlags_groups_findings_per_extension() {
        stubFlaggedExtension("ns", "ext");

        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(TestData.version("universal", "1.0.0")));

        var result = admins.getNameSquattingFlags(null, null, null, null, null, null, 10, 0, "desc");

        assertThat(result.getTotalSize()).isEqualTo(3);
        assertThat(result.getOffset()).isZero();
        assertThat(result.getFlags()).hasSize(1);

        var flag = result.getFlags().getFirst();
        assertThat(flag.getNamespace()).isEqualTo("ns");
        assertThat(flag.getExtensionName()).isEqualTo("ext");
        assertThat(flag.getDisplayName()).isEqualTo("Extension Display Name");
        assertThat(flag.getPublisher()).isEqualTo("publisher");
        assertThat(flag.getState()).isEqualTo("PUBLISHED");
        assertThat(flag.getActiveVersionCount()).isEqualTo(1);
        assertThat(flag.getFindingCount()).isEqualTo(2);
        assertThat(flag.getFindings()).hasSize(2);
        // Findings come back newest first, so the dates bracket the whole history.
        assertThat(flag.getFindings().getFirst().getVersion()).isEqualTo("2.0.0");
        assertThat(flag.getFindings().getFirst().getScanStatus()).isEqualTo("PASSED");
        assertThat(flag.getFindings().getFirst().isEnforcedFlag()).isFalse();
        assertThat(flag.getFindings().getLast().getVersion()).isEqualTo("1.0.0");
        assertThat(flag.getDateLastDetected()).isEqualTo("2026-02-11T14:00Z");
        assertThat(flag.getDateFirstDetected()).isEqualTo("2026-01-05T09:30Z");
    }

    @Test
    void getNameSquattingFlags_reports_rejected_when_extension_was_never_created() {
        stubFlaggedExtension("ns", "ext");

        // Publication was blocked by an enforced check, so no extension exists.
        when(repositories.findExtension("ext", "ns")).thenReturn(null);

        var flag = admins
                .getNameSquattingFlags(null, null, null, null, null, null, 10, 0, "desc")
                .getFlags()
                .getFirst();

        assertThat(flag.getState()).isEqualTo("REJECTED");
        assertThat(flag.getActiveVersionCount()).isZero();
    }

    @Test
    void getNameSquattingFlags_reports_deactivated_when_no_active_versions_remain() {
        stubFlaggedExtension("ns", "ext");

        var extension = TestData.extension("ns", "ext", false);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension)).thenReturn(Streamable.empty());

        var flag = admins
                .getNameSquattingFlags(null, null, null, null, null, null, 10, 0, "desc")
                .getFlags()
                .getFirst();

        assertThat(flag.getState()).isEqualTo("DEACTIVATED");
        assertThat(flag.getActiveVersionCount()).isZero();
    }

    @Test
    void getNameSquattingFlags_rejects_unknown_state_filter() {
        assertThatThrownBy(
                () -> admins.getNameSquattingFlags(
                        null,
                        null,
                        null,
                        List.of("PUBLISHED,SOMETHING"),
                        null,
                        null,
                        10,
                        0,
                        "desc"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Unknown state filter: SOMETHING");
    }

    @Test
    void getNameSquattingFlags_rejects_unknown_sort_order() {
        assertThatThrownBy(
                () -> admins.getNameSquattingFlags(null, null, null, null, null, null, 10, 0, "sideways"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Unsupported sortOrder value: sideways");
    }

    @Test
    void getNameSquattingFlags_rejects_an_unparseable_detection_date() {
        assertThatThrownBy(
                () -> admins.getNameSquattingFlags(null, null, null, null, "yesterday", null, 10, 0, "desc"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Invalid ISO date-time for parameter 'dateDetectedFrom': yesterday");
    }

    @Test
    void getNameSquattingCounts_breaks_down_flagged_extensions_by_state() {
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
                        eq(new AdminService.ExtensionStateFilter(true, false, false))))
                .thenReturn(4L);
        when(
                repositories.countFlaggedExtensions(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(new AdminService.ExtensionStateFilter(false, true, false))))
                .thenReturn(2L);
        when(
                repositories.countFlaggedExtensions(
                        eq(CHECK_TYPE),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(new AdminService.ExtensionStateFilter(false, false, true))))
                .thenReturn(3L);

        var counts = admins.getNameSquattingCounts(null, null, null, null, null);

        assertThat(counts.getTotal()).isEqualTo(9);
        assertThat(counts.getPublished()).isEqualTo(4);
        assertThat(counts.getDeactivated()).isEqualTo(2);
        assertThat(counts.getRejected()).isEqualTo(3);
    }

    @Test
    void clearNameSquattingFindings_removes_the_records_and_logs_the_action() {
        when(repositories.deleteValidationFailures(CHECK_TYPE, "ns", "ext")).thenReturn(2);

        var response = admins.clearNameSquattingFindings(adminUser, request("ns", "ext"));

        assertThat(response.getProcessed()).isEqualTo(1);
        assertThat(response.getSuccessful()).isEqualTo(1);
        assertThat(response.getFailed()).isZero();
        assertThat(response.getResults().getFirst().isSuccess()).isTrue();
        assertThat(response.getResults().getFirst().getMessage())
                .isEqualTo("Cleared 2 name squatting findings for extension ns.ext as a false positive");

        verify(repositories).deleteValidationFailures(CHECK_TYPE, "ns", "ext");
        verify(logs).logAction(eq(adminUser), any(ResultJson.class));
    }

    @Test
    void clearNameSquattingFindings_reports_a_failure_when_nothing_is_recorded() {
        when(repositories.deleteValidationFailures(CHECK_TYPE, "ns", "ext")).thenReturn(0);

        var response = admins.clearNameSquattingFindings(adminUser, request("ns", "ext"));

        assertThat(response.getSuccessful()).isZero();
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults().getFirst().isSuccess()).isFalse();
        assertThat(response.getResults().getFirst().getError())
                .isEqualTo("No name squatting findings are recorded for this extension");

        verify(logs, never()).logAction(any(), any());
    }

    @Test
    void clearNameSquattingFindings_requires_at_least_one_extension() {
        var request = new NameSquattingActionRequest();
        request.setTargets(List.of());

        assertThatThrownBy(() -> admins.clearNameSquattingFindings(adminUser, request))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("At least one extension is required")
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void clearNameSquattingFindings_rejects_targets_without_a_name() {
        var request = request("ns", null);

        assertThatThrownBy(() -> admins.clearNameSquattingFindings(adminUser, request))
                .isInstanceOf(ErrorResultException.class)
                .hasMessage("Each extension must have a namespace and an extension name");
    }

    @Test
    void deleteNameSquattingExtensions_deactivates_every_active_version() {
        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(
                        Streamable.of(
                                TestData.version("universal", "1.0.0"),
                                TestData.version("linux-x64", "2.0.0")));
        when(
                extensions.deleteExtension(
                        any(),
                        anyBoolean(),
                        anyString(),
                        anyString(),
                        any(TargetPlatformVersion[].class)))
                .thenReturn(ResultJson.success("deleted"));

        var response = admins.deleteNameSquattingExtensions(adminUser, request("ns", "ext"));

        assertThat(response.getSuccessful()).isEqualTo(1);
        assertThat(response.getResults().getFirst().isSuccess()).isTrue();
        assertThat(response.getResults().getFirst().getMessage())
                .isEqualTo("Deactivated 2 versions of extension ns.ext flagged for name squatting");

        verify(extensions).deleteExtension(
                eq(adminUser),
                eq(false),
                eq("ns"),
                eq("ext"),
                eq(new TargetPlatformVersion("universal", "1.0.0")),
                eq(new TargetPlatformVersion("linux-x64", "2.0.0")));
        verify(logs).logAction(eq(adminUser), any(ResultJson.class));
    }

    @Test
    void deleteNameSquattingExtensions_refuses_an_extension_that_was_never_created() {
        when(repositories.findExtension("ext", "ns")).thenReturn(null);

        var response = admins.deleteNameSquattingExtensions(adminUser, request("ns", "ext"));

        assertThat(response.getSuccessful()).isZero();
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults().getFirst().getError())
                .isEqualTo("Extension does not exist, its publication was blocked by the check");

        verify(extensions, never()).deleteExtension(any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    void deleteNameSquattingExtensions_refuses_an_extension_that_is_already_deactivated() {
        var extension = TestData.extension("ns", "ext", false);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension)).thenReturn(Streamable.empty());

        var response = admins.deleteNameSquattingExtensions(adminUser, request("ns", "ext"));

        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults().getFirst().getError())
                .isEqualTo("Extension has no active versions left to deactivate");
    }

    @Test
    void deleteNameSquattingExtensions_reports_why_a_deletion_was_refused() {
        var extension = TestData.extension("ns", "ext", true);
        when(repositories.findExtension("ext", "ns")).thenReturn(extension);
        when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(TestData.version("universal", "1.0.0")));
        when(
                extensions.deleteExtension(
                        any(),
                        anyBoolean(),
                        anyString(),
                        anyString(),
                        any(TargetPlatformVersion[].class)))
                .thenThrow(
                        new ErrorResultException(
                                "Extension is bundled by other extensions",
                                HttpStatus.BAD_REQUEST));

        var response = admins.deleteNameSquattingExtensions(adminUser, request("ns", "ext"));

        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults().getFirst().getError())
                .isEqualTo("Extension is bundled by other extensions");
    }

    private static NameSquattingActionRequest request(String namespace, String extensionName) {
        var target = new NameSquattingTargetJson();
        target.setNamespace(namespace);
        target.setExtension(extensionName);
        var request = new NameSquattingActionRequest();
        request.setTargets(List.of(target));
        return request;
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
