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

import java.time.Instant;
import java.util.Optional;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScannerPollHandlerTest {

    private final ScannerJobRepository repo = mock(ScannerJobRepository.class);
    private final ScannerRegistry registry = mock(ScannerRegistry.class);
    private final ExtensionScanPersistenceService persistence = mock(ExtensionScanPersistenceService.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final ExtensionScanCompletionService completion = mock(ExtensionScanCompletionService.class);
    private final Scanner scanner = mock(Scanner.class);
    private final ScannerPollHandler handler = new ScannerPollHandler(
            repo,
            registry,
            persistence,
            scheduler,
            completion);
    private ScannerJob job;

    @BeforeEach
    void setUp() {
        job = new ScannerJob();
        job.setId(1);
        job.setScanId("scan");
        job.setScannerType("argus");
        job.setExternalJobId("ext");
        job.setStatus(ScannerJob.JobStatus.PROCESSING);
        when(repo.findById(1L)).thenReturn(Optional.of(job));
        when(registry.getScanner("argus")).thenReturn(scanner);
        when(scanner.isAsync()).thenReturn(true);
    }

    private void poll() throws Exception {
        handler.run(new ScannerPollRequest(1L));
    }

    @Test
    void firstErrorStatus_isNotFinal_andSchedulesAnotherPoll() throws Exception {
        when(scanner.pollStatus(any())).thenReturn(Scanner.PollStatus.FAILED);

        poll();

        assertEquals(ScannerJob.JobStatus.PROCESSING, job.getStatus());
        assertEquals(ScannerPollHandler.UNCONFIRMED_FAILURE, job.getErrorMessage());
        verify(scheduler).schedule(any(Instant.class), any(ScannerPollRequest.class));
        verify(completion, never()).checkCompletionSafely(anyString());
    }

    @Test
    void secondConsecutiveErrorStatus_failsTheJob() throws Exception {
        job.setErrorMessage(ScannerPollHandler.UNCONFIRMED_FAILURE);
        when(scanner.pollStatus(any())).thenReturn(Scanner.PollStatus.FAILED);

        poll();

        assertEquals(ScannerJob.JobStatus.FAILED, job.getStatus());
        verify(completion).checkCompletionSafely("scan");
    }

    @Test
    void recoveryAfterErrorStatus_clearsTheMarker() throws Exception {
        job.setErrorMessage(ScannerPollHandler.UNCONFIRMED_FAILURE);
        when(scanner.pollStatus(any())).thenReturn(Scanner.PollStatus.PROCESSING);

        poll();

        assertNull(job.getErrorMessage());
        assertEquals(ScannerJob.JobStatus.PROCESSING, job.getStatus());
    }

    @Test
    void pollException_keepsPollingInsteadOfEndingTheChain() throws Exception {
        when(scanner.pollStatus(any())).thenThrow(new ScannerException("502 Bad Gateway"));

        assertDoesNotThrow(this::poll);

        assertEquals(ScannerJob.JobStatus.PROCESSING, job.getStatus());
        assertTrue(job.getErrorMessage().startsWith("Poll failed"));
        verify(scheduler).schedule(any(Instant.class), any(ScannerPollRequest.class));
    }
}
