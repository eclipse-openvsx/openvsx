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

import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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

    @BeforeEach
    void setUp() throws Exception {
        var job = new ScannerJob();
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

    @Test
    void watchdogFailedTheJobDuringTheScannerCall_resultIsDiscarded() throws Exception {
        var failed = new ScannerJob();
        failed.setId(1);
        failed.setScanId("scan");
        failed.setScannerType("argus");
        failed.setStatus(ScannerJob.JobStatus.FAILED);
        when(scanner.startScan(any())).thenAnswer(invocation -> {
            // the watchdog's verdict lands while the scanner is busy
            when(repo.findById(1L)).thenReturn(Optional.of(failed));
            return new Scanner.Invocation.Completed(mock(Scanner.Result.class));
        });

        invoke();

        verify(persistence, never()).completeScannerJob(any(), any(), anyBoolean(), any());
        verify(repo, never()).trySave(any());
        verify(completion, never()).checkCompletionSafely(anyString());
    }

    @Test
    void lostRaceOnSubmittedScan_schedulesNoPoll() throws Exception {
        when(scanner.isAsync()).thenReturn(true);
        when(scanner.startScan(any())).thenReturn(new Scanner.Invocation.Submitted(new Scanner.Submission("ext")));
        when(repo.trySave(any())).thenReturn(false);

        invoke();

        verify(scheduler, never()).schedule(any(java.time.Instant.class), any(ScannerPollRequest.class));
    }
}
