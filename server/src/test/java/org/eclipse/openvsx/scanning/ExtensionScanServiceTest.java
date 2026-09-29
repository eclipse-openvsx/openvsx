/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.scanning;

import java.util.List;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtensionScanServiceTest {

    @Mock
    ExtensionScanConfig config;
    @Mock
    PublishCheckRunner checkRunner;
    @Mock
    ExtensionScanPersistenceService persistenceService;
    @Mock
    ScannerRegistry scannerRegistry;
    @Mock
    RemoteScanner remoteScanner;
    @Mock
    JobRequestScheduler jobScheduler;
    @Mock
    ScannerJobRepository scanJobRepository;

    private ExtensionScanService svc;

    @BeforeEach
    void setUp() {
        svc = new ExtensionScanService(
                config,
                checkRunner,
                persistenceService,
                scannerRegistry,
                jobScheduler,
                scanJobRepository);
    }

    @Test
    void retryFailedJobs_throwsBadRequest_whenScanIsNotTerminal() {
        var scan = scanWithStatus(42L, ScanStatus.SCANNING);

        assertThatThrownBy(() -> svc.retryFailedJobs(scan))
                .isInstanceOf(ErrorResultException.class)
                .satisfies(
                        e -> assertThat(((ErrorResultException) e).getStatus())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(scanJobRepository);
    }

    @Test
    void retryFailedJobs_throwsNotFound_whenScanHasNoJobs() {
        var scan = scanWithStatus(42L, ScanStatus.PASSED);
        when(scanJobRepository.findByScanId("42")).thenReturn(List.of());

        assertThatThrownBy(() -> svc.retryFailedJobs(scan))
                .isInstanceOf(ErrorResultException.class)
                .satisfies(
                        e -> assertThat(((ErrorResultException) e).getStatus())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(persistenceService, jobScheduler);
    }

    @Test
    void retryFailedJobs_enqueuesScannerInvocationWithJobFields() {
        // Verifies the enqueued request carries the FAILED job's scannerType/extensionVersionId/scanId — not stale data
        var scan = scanWithStatus(77L, ScanStatus.ERRORED);
        var failedJob = job("77", ScannerJob.JobStatus.FAILED);
        failedJob.setScannerType("CLAMAV_REST");
        failedJob.setExtensionVersionId(987L);
        when(scanJobRepository.findByScanId("77")).thenReturn(List.of(failedJob));
        when(scannerRegistry.getScanner("CLAMAV_REST")).thenReturn(remoteScanner);
        when(remoteScanner.getMaxConcurrency()).thenReturn(0);

        svc.retryFailedJobs(scan);

        var captor = ArgumentCaptor.forClass(ScannerInvocationRequest.class);
        verify(jobScheduler).enqueue(captor.capture());
        var request = captor.getValue();
        assertThat(request.getScannerType()).isEqualTo("CLAMAV_REST");
        assertThat(request.getExtensionVersionId()).isEqualTo(987L);
        assertThat(request.getScanId()).isEqualTo("77");
    }

    @Test
    void retryFailedJobs_throwsBadRequest_whenNoJobsAreFailed() {
        // COMPLETE and REMOVED jobs exist but are not eligible for retry
        var scan = scanWithStatus(42L, ScanStatus.ERRORED);
        when(scanJobRepository.findByScanId("42"))
                .thenReturn(
                        List
                                .of(
                                        job("42", ScannerJob.JobStatus.COMPLETE),
                                        job("42", ScannerJob.JobStatus.REMOVED)));

        assertThatThrownBy(() -> svc.retryFailedJobs(scan))
                .isInstanceOf(ErrorResultException.class)
                .satisfies(
                        e -> assertThat(((ErrorResultException) e).getStatus())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(persistenceService, jobScheduler);
    }

    @Test
    void retryFailedJobs_onlyRetriesFailedJobs_returnsUpdatedScan() {
        var scan = scanWithStatus(10L, ScanStatus.ERRORED);
        scan.setCompletedAt(java.time.LocalDateTime.of(2026, 1, 1, 12, 0));
        scan.setErrorMessage("previous failure");
        var failedJob1 = job("10", ScannerJob.JobStatus.FAILED);
        var failedJob2 = job("10", ScannerJob.JobStatus.FAILED);
        when(scanJobRepository.findByScanId("10"))
                .thenReturn(
                        List
                                .of(failedJob1, failedJob2, job("10", ScannerJob.JobStatus.COMPLETE)));

        doAnswer(invocation -> {
            ExtensionScan s = invocation.getArgument(0);
            s.setStatus(ScanStatus.SCANNING);
            s.setCompletedAt(null);
            s.setErrorMessage(null);
            return null;
        }).when(persistenceService).resetJobForRetry(eq(scan), any());

        when(scannerRegistry.getScanner("CLAMAV_REST")).thenReturn(remoteScanner);
        when(remoteScanner.getMaxConcurrency()).thenReturn(0);

        var result = svc.retryFailedJobs(scan);

        // The returned scan reflects the "now running" state the UI will display
        assertThat(result).isSameAs(scan);
        assertThat(result.getStatus()).isEqualTo(ScanStatus.SCANNING);
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        verify(persistenceService, times(2)).resetJobForRetry(eq(scan), any());
        verify(jobScheduler, times(2)).enqueue(any(ScannerInvocationRequest.class));
    }

    @Test
    void retryFailedJob_throwsErrorResult_whenJobIsActive() {
        var scan = scanWithStatus(1L, ScanStatus.SCANNING);
        var queuedJob = job("1", ScannerJob.JobStatus.QUEUED);

        assertThatThrownBy(() -> svc.retryFailedJob(scan, queuedJob))
                .isInstanceOf(ErrorResultException.class);

        verifyNoInteractions(persistenceService, jobScheduler);
    }

    @Test
    void retryFailedJob_swallowsSchedulerException_afterPersistingReset() {
        // If JobRunr fails to enqueue after the DB reset was committed, we should not surface the error
        var scan = scanWithStatus(8L, ScanStatus.ERRORED);
        var failedJob = job("8", ScannerJob.JobStatus.FAILED);
        doThrow(new RuntimeException("JobRunr unavailable")).when(jobScheduler)
                .enqueue(any(ScannerInvocationRequest.class));

        when(scannerRegistry.getScanner("CLAMAV_REST")).thenReturn(remoteScanner);
        when(remoteScanner.getMaxConcurrency()).thenReturn(0);

        assertThatCode(() -> svc.retryFailedJob(scan, failedJob)).doesNotThrowAnyException();

        verify(persistenceService).resetJobForRetry(scan, failedJob);
        verify(jobScheduler).enqueue(any(ScannerInvocationRequest.class));
    }

    private static ExtensionScan scanWithStatus(long id, ScanStatus status) {
        var scan = new ExtensionScan();
        scan.setId(id);
        scan.setStatus(status);
        return scan;
    }

    private static ScannerJob job(String scanId, ScannerJob.JobStatus status) {
        var job = new ScannerJob();
        job.setScanId(scanId);
        job.setScannerType("CLAMAV_REST");
        job.setExtensionVersionId(100L);
        job.setStatus(status);
        return job;
    }
}
