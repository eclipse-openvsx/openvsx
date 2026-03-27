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
package org.eclipse.openvsx.scanning;

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TempFile;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Focused tests for "enforced vs monitor-only" behavior.
 * <p>
 * Goal:
 * - failures are always persisted
 * - publishing is only blocked when enforcement is enabled (throws ErrorResultException)
 */
@ExtendWith(MockitoExtension.class)
class ExtensionScanServiceEnforcementTest {

    @Mock ExtensionScanConfig config;
    @Mock PublishCheckRunner checkRunner;
    @Mock ExtensionScanPersistenceService persistenceService;
    @Mock ScannerRegistry scannerRegistry;
    @Mock JobRequestScheduler jobScheduler;
    @Mock ScannerJobRepository scanJobRepository;
    @Mock TempFile extensionFile;

    private ExtensionScanService svc;
    private ExtensionScan scan;
    private UserData user;

    @BeforeEach
    void setUp() {
        svc = new ExtensionScanService(config, checkRunner, persistenceService, scannerRegistry, jobScheduler, scanJobRepository);
        
        scan = new ExtensionScan();
        scan.setId(123);
        scan.setStatus(ScanStatus.STARTED);
        scan.setNamespaceName("ns");
        scan.setExtensionName("ext");
        scan.setExtensionVersion("1.0.0");
        scan.setTargetPlatform("universal");
        scan.setPublisher("publisher");

        user = new UserData();
        user.setLoginName("testuser");
        
        // Make mock persistence service actually update scan status for state machine validation
        lenient().doAnswer(invocation -> {
            ExtensionScan s = invocation.getArgument(0);
            ScanStatus status = invocation.getArgument(1);
            s.setStatus(status);
            return null;
        }).when(persistenceService).updateStatus(any(ExtensionScan.class), any(ScanStatus.class));
        
        lenient().doAnswer(invocation -> {
            ExtensionScan s = invocation.getArgument(0);
            ScanStatus status = invocation.getArgument(1);
            s.setStatus(status);
            return null;
        }).when(persistenceService).completeWithStatus(any(ExtensionScan.class), any(ScanStatus.class));
        
        // Make mock persistence service actually add failures to scan
        lenient().doAnswer(invocation -> {
            ExtensionScan s = invocation.getArgument(0);
            String checkType = invocation.getArgument(1);
            String ruleName = invocation.getArgument(2);
            String reason = invocation.getArgument(3);
            boolean enforced = invocation.getArgument(4);
            
            var failure = ExtensionValidationFailure.create(checkType, ruleName, reason);
            failure.setEnforced(enforced);
            s.addValidationFailure(failure);
            return null;
        }).when(persistenceService).recordValidationFailure(any(), any(), any(), any(), anyBoolean());
    }

    // Helper to create a check execution
    private PublishCheckRunner.CheckExecution checkExecution(String checkType, ScanCheckResult.CheckResult result) {
        return new PublishCheckRunner.CheckExecution(
            checkType,
            LocalDateTime.now(),
            LocalDateTime.now(),
            result,
            0,
            null,
            "summary",
            true
        );
    }

    // ========== HAPPY PATH TESTS ==========

    @Test
    void runValidation_passes_whenAllChecksPass() {
        // Scanner returns no findings
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                List.of(),
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act - should not throw
        svc.runValidation(scan, extensionFile, user);

        // Assert: no failures recorded, validation lifecycle methods called
        verify(persistenceService).updateStatus(scan, ScanStatus.VALIDATING);
        verify(persistenceService, never()).recordValidationFailure(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void runValidation_delegatesToCheckRunner() {
        // Scanner returns pass
        when(checkRunner.runChecks(eq(scan), eq(extensionFile), eq(user)))
            .thenReturn(new PublishCheckRunner.Result(
                List.of(),
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act
        svc.runValidation(scan, extensionFile, user);

        // Assert: scanner was called with correct arguments
        verify(checkRunner).runChecks(scan, extensionFile, user);
    }

    // ========== ENFORCEMENT TESTS ==========

    @Test
    void runValidation_doesNotThrow_whenFailuresNotEnforced_butPersistsFailures() {
        // Scanner returns failures but not enforced
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", false, null),
            new PublishCheckRunner.Finding("CHECK_2", "rule2", "reason2", false, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act: should NOT throw because nothing is enforced
        svc.runValidation(scan, extensionFile, user);

        // Assert: failures persisted even in monitor-only mode
        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_1"), eq("rule1"), eq("reason1"), eq(false));
        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_2"), eq("rule2"), eq("reason2"), eq(false));
    }

    @Test
    void runValidation_throwsWhenEnforcedCheckFails() {
        // Scanner returns enforced failure
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", true, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.REJECT)),
                List.of(),
                true,
                false
            ));

        // Act & Assert: should throw ErrorResultException
        assertThatThrownBy(() -> svc.runValidation(scan, extensionFile, user))
            .isInstanceOf(ErrorResultException.class);

        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_1"), any(), any(), eq(true));
    }

    @Test
    void runValidation_throwsWhenBothEnforced_andBothFail() {
        // Scanner returns multiple enforced failures
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", true, null),
            new PublishCheckRunner.Finding("CHECK_2", "rule2", "reason2", true, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.REJECT)),
                List.of(),
                true,
                false
            ));

        // Act & Assert
        assertThatThrownBy(() -> svc.runValidation(scan, extensionFile, user))
            .isInstanceOf(ErrorResultException.class);

        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_1"), any(), any(), eq(true));
        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_2"), any(), any(), eq(true));
    }

    @Test
    void runValidation_throwsWhenOneEnforcedFails_andOneNotEnforcedFails() {
        // Mixed: one enforced failure, one non-enforced warning
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", true, null),
            new PublishCheckRunner.Finding("CHECK_2", "rule2", "reason2", false, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.REJECT)),
                List.of(),
                true,
                false
            ));

        // Act & Assert: blocked because at least one enforced check failed
        assertThatThrownBy(() -> svc.runValidation(scan, extensionFile, user))
            .isInstanceOf(ErrorResultException.class);

        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_1"), any(), any(), eq(true));
        verify(persistenceService).recordValidationFailure(eq(scan), eq("CHECK_2"), any(), any(), eq(false));
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    void runValidation_marksErrorAndRethrows_whenScannerReturnsError() {
        var exception = new RuntimeException("Check failed unexpectedly");
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                List.of(),
                List.of(new PublishCheckRunner.CheckExecution(
                    "CHECK_1",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    ScanCheckResult.CheckResult.ERROR,
                    0,
                    "Check failed unexpectedly",
                    "Error",
                    true
                )),
                List.of(new PublishCheckRunner.CheckError("CHECK_1", exception, true)),  // errors
                false,
                true
            ));

        // Act & Assert
        assertThatThrownBy(() -> svc.runValidation(scan, extensionFile, user))
            .isInstanceOf(ErrorResultException.class)
            .hasMessageContaining("Check failed unexpectedly");

        verify(persistenceService).markAsErrored(eq(scan), anyString());
    }

    // ========== LIFECYCLE TESTS ==========

    @Test
    void runValidation_transitionsToValidating() {
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                List.of(),
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act
        svc.runValidation(scan, extensionFile, user);

        // Assert: transitions to VALIDATING
        verify(persistenceService).updateStatus(scan, ScanStatus.VALIDATING);
    }

    @Test
    void runValidation_completesNormally_whenFailuresNotEnforced() {
        // Scanner returns non-enforced failures (warnings)
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", false, null),
            new PublishCheckRunner.Finding("CHECK_2", "rule2", "reason2", false, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act - should not throw
        svc.runValidation(scan, extensionFile, user);

        // Assert: completes normally (no exception)
        verify(persistenceService).updateStatus(scan, ScanStatus.VALIDATING);
    }

    @Test
    void runValidation_transitionsToRejected_whenEnforcedFailure() {
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", true, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.REJECT)),
                List.of(),
                true,
                false
            ));

        // Act & Assert: throws and transitions to REJECTED
        assertThatThrownBy(() -> svc.runValidation(scan, extensionFile, user))
            .isInstanceOf(ErrorResultException.class);

        verify(persistenceService).completeWithStatus(scan, ScanStatus.REJECTED);
    }

    // ========== MULTIPLE FINDINGS TESTS ==========

    @Test
    void runValidation_recordsAllFindings() {
        // Scanner returns multiple findings from one check type
        var findings = List.of(
            new PublishCheckRunner.Finding("CHECK_1", "rule1", "reason1", false, null),
            new PublishCheckRunner.Finding("CHECK_1", "rule2", "reason2", false, null),
            new PublishCheckRunner.Finding("CHECK_1", "rule3", "reason3", false, null)
        );
        when(checkRunner.runChecks(any(), any(), any()))
            .thenReturn(new PublishCheckRunner.Result(
                findings,
                List.of(checkExecution("CHECK_1", ScanCheckResult.CheckResult.PASSED)),
                List.of(),
                false,
                false
            ));

        // Act
        svc.runValidation(scan, extensionFile, user);

        // Assert: all 3 findings recorded
        verify(persistenceService, times(3)).recordValidationFailure(eq(scan), eq("CHECK_1"), any(), any(), eq(false));
    }

    @Test
    void submitScannerJobs_skipsWhenJobAlreadyExists_forSameScanAndScannerType() {
        // Arrange
        Namespace namespace = new Namespace();
        namespace.setName("ns");
        Extension extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("ext");

        ExtensionVersion extVersion = new ExtensionVersion();
        extVersion.setId(456L);
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform("universal");
        when(config.isEnabled()).thenReturn(true);

        Scanner scanner = mock(Scanner.class);
        when(scanner.getScannerType()).thenReturn("CLAMAV_REST");
        when(scannerRegistry.getAllScanners()).thenReturn(List.of(scanner));

        ScannerJob existingJob = new ScannerJob();
        existingJob.setId(999L);
        existingJob.setScanId("123");
        existingJob.setScannerType("CLAMAV_REST");
        when(scanJobRepository.findByScanIdAndScannerType("123", "CLAMAV_REST"))
            .thenReturn(Optional.of(existingJob));

        // Act
        boolean submitted = svc.submitScannerJobs(scan, extVersion);

        // Assert
        assertThat(submitted).isTrue();
        verify(scanJobRepository, never()).save(any(ScannerJob.class));
        verify(jobScheduler, never()).enqueue(any(ScannerInvocationRequest.class));
    }
}
