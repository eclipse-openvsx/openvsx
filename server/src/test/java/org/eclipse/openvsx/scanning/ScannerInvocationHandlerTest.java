/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scan-timeout watchdog ({@code ExtensionScanJobRecoveryService}) can mark a job FAILED (and the
 * whole scan ERRORED) while {@code scanner.startScan()} is still blocked on a slow scanner. These tests
 * cover the resulting race: the blocked call eventually returns, and the handler must not resurrect a
 * job the watchdog already finalized.
 */
@ExtendWith(MockitoExtension.class)
class ScannerInvocationHandlerTest {

    @Mock
    ScannerJobRepository scanJobRepository;
    @Mock
    ScannerRegistry scannerRegistry;
    @Mock
    ExtensionScanPersistenceService persistenceService;
    @Mock
    JobRequestScheduler jobScheduler;
    @Mock
    ExtensionScanCompletionService completionService;
    @Mock
    Scanner scanner;

    private ScannerInvocationHandler newHandler() {
        return new ScannerInvocationHandler(
                scanJobRepository,
                scannerRegistry,
                persistenceService,
                jobScheduler,
                completionService);
    }

    private ScannerJob processingJob() {
        var job = new ScannerJob();
        job.setId(42L);
        job.setScanId("scan-1");
        job.setScannerType("clamav-rest");
        job.setExtensionVersionId(7L);
        job.setStatus(ScannerJob.JobStatus.PROCESSING);
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    @Test
    void run_discardsLateCleanResultWhenWatchdogAlreadyFailedTheJob() throws Exception {
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any())).thenReturn(new Scanner.Invocation.Completed(Scanner.Result.clean()));

        // Simulate the watchdog marking the job FAILED while startScan() above was still blocked:
        // the fresh load inside saveResults sees FAILED, not the PROCESSING snapshot taken at Phase 1.
        var failedJob = processingJob();
        failedJob.setStatus(ScannerJob.JobStatus.FAILED);
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(failedJob));
        // Stubbed leniently: the guard must prevent this from ever being called, but without the
        // guard it would be called and return null, masking the real assertion behind an NPE.
        org.mockito.Mockito.lenient()
                .when(persistenceService.processCompletedScan(any(), any(), anyBoolean(), any()))
                .thenReturn(
                        new ExtensionScanPersistenceService.CompletedScanResult(
                                ScanCheckResult.CheckResult.PASSED,
                                0,
                                "No threats found"));

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        // The watchdog's FAILED verdict must stand: no re-processing, no re-triggered completion check,
        // no save that would flip FAILED back to COMPLETE.
        assertEquals(ScannerJob.JobStatus.FAILED, failedJob.getStatus());
        verify(persistenceService, never()).processCompletedScan(any(), any(), anyBoolean(), any());
        verify(completionService, never()).checkCompletionSafely(any());
        verify(scanJobRepository, never()).save(any());
    }

    @Test
    void run_appliesCleanResultWhenJobStillProcessing() throws Exception {
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any())).thenReturn(new Scanner.Invocation.Completed(Scanner.Result.clean()));
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(persistenceService.processCompletedScan(any(), any(), anyBoolean(), any()))
                .thenReturn(
                        new ExtensionScanPersistenceService.CompletedScanResult(
                                ScanCheckResult.CheckResult.PASSED,
                                0,
                                "No threats found"));

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        assertEquals(ScannerJob.JobStatus.COMPLETE, job.getStatus());
        verify(completionService).checkCompletionSafely("scan-1");
        verify(scanJobRepository).save(job);
    }
}
