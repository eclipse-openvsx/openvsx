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

import java.util.Optional;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerInvocationHandlerTest {

    private final ScannerJobRepository repo = mock(ScannerJobRepository.class);
    private final ScannerRegistry registry = mock(ScannerRegistry.class);
    private final ExtensionScanPersistenceService persistence = mock(ExtensionScanPersistenceService.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final ExtensionScanCompletionService completion = mock(ExtensionScanCompletionService.class);
    private final Scanner scanner = mock(Scanner.class);
    private final ScannerInvocationHandler handler = new ScannerInvocationHandler(
            repo,
            registry,
            persistence,
            scheduler,
            completion);

    private ScannerJob job;

    @BeforeEach
    void setUp() throws Exception {
        job = new ScannerJob();
        job.setId(1);
        job.setScanId("scan");
        job.setScannerType("argus");
        job.setStatus(ScannerJob.JobStatus.PROCESSING);
        when(repo.findByScanIdAndScannerType("scan", "argus")).thenReturn(Optional.of(job));
        when(repo.findById(1L)).thenReturn(Optional.of(job));
        when(registry.getScanner("argus")).thenReturn(scanner);
    }

    private void invoke() throws Exception {
        handler.run(new ScannerInvocationRequest("argus", 7L, "scan"));
    }

    @Test
    void lostRaceOnCompletedScan_checksNothing() throws Exception {
        when(scanner.startScan(any())).thenReturn(new Scanner.Invocation.Completed(mock(Scanner.Result.class)));
        when(persistence.completeScannerJob(any(), any(), anyBoolean(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(ScannerJob.class, 1L));

        invoke();

        verify(completion, never()).checkCompletionSafely(anyString());
    }

    // The job was reset and claimed again (by recovery and another worker) while the scanner ran: the
    // result must be checked against the copy read before the call, not a fresh one.
    private void jobChangesDuringTheScannerCall() {
        var reclaimed = new ScannerJob();
        reclaimed.setId(1);
        reclaimed.setScanId("scan");
        reclaimed.setScannerType("argus");
        reclaimed.setStatus(ScannerJob.JobStatus.PROCESSING);
        when(repo.findById(1L)).thenReturn(Optional.of(reclaimed));
    }

    @Test
    void completedScan_isSavedAgainstTheJobReadBeforeTheScannerCall() throws Exception {
        when(scanner.startScan(any())).thenAnswer(invocation -> {
            jobChangesDuringTheScannerCall();
            return new Scanner.Invocation.Completed(mock(Scanner.Result.class));
        });
        when(persistence.completeScannerJob(any(), any(), anyBoolean(), any()))
                .thenReturn(
                        new ExtensionScanPersistenceService.CompletedScanResult(
                                ScanCheckResult.CheckResult.PASSED,
                                0,
                                "clean"));

        invoke();

        verify(persistence).completeScannerJob(same(job), any(), anyBoolean(), any());
    }

    @Test
    void submittedScan_isSavedAgainstTheJobReadBeforeTheScannerCall() throws Exception {
        when(scanner.isAsync()).thenReturn(true);
        when(scanner.startScan(any())).thenAnswer(invocation -> {
            jobChangesDuringTheScannerCall();
            return new Scanner.Invocation.Submitted(new Scanner.Submission("ext"));
        });

        invoke();

        verify(repo).trySave(same(job));
    }

    @Test
    void scannerFailure_isSavedAgainstTheJobReadBeforeTheScannerCall() throws Exception {
        when(scanner.startScan(any())).thenAnswer(invocation -> {
            jobChangesDuringTheScannerCall();
            throw new RuntimeException("boom");
        });

        invoke();

        verify(repo).trySave(same(job));
    }

    @Test
    void lostRaceOnSubmittedScan_schedulesNoPoll() throws Exception {
        when(scanner.isAsync()).thenReturn(true);
        when(scanner.startScan(any())).thenReturn(new Scanner.Invocation.Submitted(new Scanner.Submission("ext")));
        when(repo.trySave(any())).thenReturn(false);

        invoke();

        verify(scheduler, never()).schedule(any(java.time.Instant.class), any(ScannerPollRequest.class));
    }

    @Test
    void scannerThrowsButJobChangedConcurrently_checksNothing() throws Exception {
        when(scanner.startScan(any())).thenThrow(new RuntimeException("boom"));
        when(repo.trySave(any())).thenReturn(false);

        invoke();

        verify(completion, never()).checkCompletionSafely(anyString());
    }

    @Test
    void scannerThrows_marksTheJobFailedAndChecksCompletion() throws Exception {
        when(scanner.startScan(any())).thenThrow(new RuntimeException("boom"));
        when(repo.trySave(any())).thenReturn(true);

        invoke();

        verify(completion).checkCompletionSafely("scan");
    }
}
