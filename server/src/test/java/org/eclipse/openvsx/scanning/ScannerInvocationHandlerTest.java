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
import static org.mockito.ArgumentMatchers.eq;
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
        // Stubbed leniently: the guard must prevent this from ever being called, so the stub
        // goes unused here - without lenient(), Mockito's strict-stubbing check would fail the test.
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

        // findById is called twice: once before the claim (the PROCESSING snapshot used to
        // compute startedAt), once after. The real claim is a bulk UPDATE, so that second call
        // would see the DB already reflecting it in production - simulated here with a distinct
        // object so the test can tell whether the code saves this one or the stale one.
        var claimedJob = processingJob();
        claimedJob.setStatus(ScannerJob.JobStatus.COMPLETE);
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(job), Optional.of(claimedJob));
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.COMPLETE), any()))
                .thenReturn(1);
        when(persistenceService.processCompletedScan(any(), any(), anyBoolean(), any()))
                .thenReturn(
                        new ExtensionScanPersistenceService.CompletedScanResult(
                                ScanCheckResult.CheckResult.PASSED,
                                0,
                                "No threats found"));

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        verify(persistenceService).processCompletedScan(any(), any(), anyBoolean(), any());
        verify(completionService).checkCompletionSafely("scan-1");
        verify(scanJobRepository).save(claimedJob);
    }

    @Test
    void run_discardsLateCleanResultWhenClaimLosesRaceToWatchdog() throws Exception {
        // The fast-path isTerminal() check alone cannot catch this: the job still reads as
        // PROCESSING here. Only the atomic claimStatusIfActive() call closes the real gap -
        // simulated here by the watchdog's own claim having already won it. Threat/audit
        // persistence runs before that claim is attempted (so a real completion never races
        // ahead of its own data), so it does happen here even though the claim then loses -
        // only the job's own terminal status and the completion check are discarded.
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
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.COMPLETE), any()))
                .thenReturn(0);

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        verify(persistenceService).processCompletedScan(any(), any(), anyBoolean(), any());
        verify(completionService, never()).checkCompletionSafely(any());
        verify(scanJobRepository, never()).save(any());
    }

    @Test
    void run_marksJobFailedWhenScannerThrows() throws Exception {
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any())).thenThrow(new RuntimeException("boom"));
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.FAILED), any()))
                .thenReturn(1);
        // The real claim is a bulk UPDATE, so the entity markJobFailed() re-fetches afterward
        // would already show FAILED in production; the mock has to simulate that explicitly,
        // since it won't apply claimStatusIfActive()'s effect to this stubbed instance itself.
        var failedJob = processingJob();
        failedJob.setStatus(ScannerJob.JobStatus.FAILED);
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(failedJob));

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        assertEquals(ScannerJob.JobStatus.FAILED, failedJob.getStatus());
        verify(scanJobRepository).save(failedJob);
        verify(completionService).checkCompletionSafely("scan-1");
    }

    @Test
    void run_doesNotOverwriteJobWhenFailClaimLosesRace() throws Exception {
        // Simulates the job having already been completed (e.g. by saveResults on a previous,
        // since-retried attempt) by the time this exception-handling claim runs.
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any())).thenThrow(new RuntimeException("boom"));
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.FAILED), any()))
                .thenReturn(0);

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        assertEquals(ScannerJob.JobStatus.PROCESSING, job.getStatus());
        verify(scanJobRepository, never()).save(any());
        verify(completionService).checkCompletionSafely("scan-1");
    }

    @Test
    void run_appliesSubmissionWhenJobStillProcessing() throws Exception {
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any()))
                .thenReturn(new Scanner.Invocation.Submitted(new Scanner.Submission("ext-job-1")));
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.SUBMITTED), any()))
                .thenReturn(1);

        var claimedJob = processingJob();
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(job), Optional.of(claimedJob));
        when(scanner.getPollConfig()).thenReturn(RemoteScannerProperties.PollConfig.DEFAULT);

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        assertEquals(ScannerJob.JobStatus.SUBMITTED, claimedJob.getStatus());
        assertEquals("ext-job-1", claimedJob.getExternalJobId());
        verify(scanJobRepository).save(claimedJob);
        verify(jobScheduler).schedule(any(java.time.Instant.class), any(ScannerPollRequest.class));
    }

    @Test
    void run_discardsSubmissionWhenClaimLosesRaceToWatchdog() throws Exception {
        // Same race as the Completed branch, mirrored for the async path: an async startScan()
        // can block on the same external call a sync one would, so this guard must exist here
        // too, not just for Completed.
        var job = processingJob();
        when(scanJobRepository.findByScanIdAndScannerType("scan-1", "clamav-rest")).thenReturn(Optional.of(job));
        when(scannerRegistry.getScanner("clamav-rest")).thenReturn(scanner);
        when(scanner.startScan(any()))
                .thenReturn(new Scanner.Invocation.Submitted(new Scanner.Submission("ext-job-1")));
        when(scanJobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(scanJobRepository.claimStatusIfActive(eq(42L), eq(ScannerJob.JobStatus.SUBMITTED), any()))
                .thenReturn(0);

        newHandler().run(new ScannerInvocationRequest("clamav-rest", 7L, "scan-1"));

        verify(scanJobRepository, never()).save(any());
        verify(jobScheduler, never()).schedule(any(java.time.Instant.class), any(ScannerPollRequest.class));
    }
}
