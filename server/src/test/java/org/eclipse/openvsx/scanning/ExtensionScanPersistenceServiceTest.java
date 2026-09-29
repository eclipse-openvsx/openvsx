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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.FileDecisionRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScanCheckResultRepository;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionScanPersistenceServiceTest {

    @Mock
    RepositoryService repositories;
    @Mock
    JsonMapper jsonMapper;
    @Mock
    FileDecisionRepository fileDecisionRepository;
    @Mock
    ScannerJobRepository scannerJobRepository;
    @Mock
    ScanCheckResultRepository scanCheckResultRepository;
    @Mock
    ScannerRegistry scannerRegistry;

    private ExtensionScanPersistenceService svc;

    @BeforeEach
    void setUp() {
        svc = new ExtensionScanPersistenceService(
                repositories,
                jsonMapper,
                fileDecisionRepository,
                scannerJobRepository,
                scanCheckResultRepository,
                scannerRegistry);
    }

    @Test
    void resetJobForRetry_resetsJobFieldsAndFlipsScanToScanning() {
        var completedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        var scan = new ExtensionScan();
        scan.setId(10L);
        scan.setStatus(ScanStatus.ERRORED);
        scan.setCompletedAt(completedAt);
        scan.setErrorMessage("scanner timed out");

        var job = new ScannerJob();
        job.setScanId("10");
        job.setScannerType("CLAMAV_REST");
        job.setExtensionVersionId(100L);
        job.setStatus(ScannerJob.JobStatus.FAILED);
        job.setErrorMessage("connection refused");
        job.setExternalJobId("ext-job-123");
        job.setPollAttempts(7);
        job.setPollLeaseUntil(LocalDateTime.of(2026, 1, 1, 11, 0));
        job.setRecoveryInProgress(true);

        svc.resetJobForRetry(scan, job);

        // Job is ready to be picked up again
        assertThat(job.getStatus()).isEqualTo(ScannerJob.JobStatus.QUEUED);
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getExternalJobId()).isNull();
        assertThat(job.getPollAttempts()).isEqualTo(0);
        assertThat(job.getPollLeaseUntil()).isNull();
        assertThat(job.isRecoveryInProgress()).isFalse();
        assertThat(job.getUpdatedAt()).isNotNull();

        // Scan is re-opened so the completion service can finalize it once the job succeeds
        assertThat(scan.getStatus()).isEqualTo(ScanStatus.SCANNING);
        assertThat(scan.getCompletedAt()).isNull();
        assertThat(scan.getErrorMessage()).isNull();

        verify(scanCheckResultRepository).deleteByScannerJobId(job.getId());
        verify(scannerJobRepository).save(job);
        verify(repositories).saveExtensionScan(scan);
    }

    // ========== processCompletedScan: isMalicious verdict vs scanner "enforced" setting ==========

    private ScannerJob jobFor(ExtensionScan scan) {
        var job = new ScannerJob();
        job.setId(1L);
        job.setScanId(String.valueOf(scan.getId()));
        job.setScannerType("ARGUS");
        return job;
    }

    @Test
    void processCompletedScan_quarantines_whenEnforcedAndFindingsPresent_noVerdict() {
        var scan = new ExtensionScan();
        scan.setId(1L);
        when(repositories.findExtensionScan(1L)).thenReturn(scan);
        var job = jobFor(scan);
        var result = Scanner.Result.withThreats(List.of(new Scanner.Threat("rule", "desc", "HIGH")));

        var outcome = svc.processCompletedScan(job, result, true, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.QUARANTINE);
    }

    @Test
    void processCompletedScan_passes_whenBenignVerdict_evenWithFindings_andScannerEnforced() {
        var scan = new ExtensionScan();
        scan.setId(2L);
        when(repositories.findExtensionScan(2L)).thenReturn(scan);
        var job = jobFor(scan);
        var result = Scanner.Result.of(
                List.of(new Scanner.Threat("rule", "desc", "HIGH")),
                null,
                false);

        var outcome = svc.processCompletedScan(job, result, true, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.PASSED);
    }

    @Test
    void processCompletedScan_quarantines_whenMaliciousVerdict_withZeroFindings_andScannerEnforced() {
        var scan = new ExtensionScan();
        scan.setId(3L);
        when(repositories.findExtensionScan(3L)).thenReturn(scan);
        var job = jobFor(scan);
        var result = Scanner.Result.of(List.of(), null, true);

        var outcome = svc.processCompletedScan(job, result, true, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.QUARANTINE);
    }

    @Test
    void processCompletedScan_doesNotQuarantine_whenMaliciousVerdict_butScannerNotEnforced() {
        // A malicious verdict must never override a scanner explicitly configured as enforced=false:
        // that setting is documented to always leave the extension activated.
        var scan = new ExtensionScan();
        scan.setId(4L);
        when(repositories.findExtensionScan(4L)).thenReturn(scan);
        var job = jobFor(scan);
        var result = Scanner.Result.of(
                List.of(new Scanner.Threat("rule", "desc", "HIGH")),
                null,
                true);

        var outcome = svc.processCompletedScan(job, result, false, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.PASSED);
    }

    @Test
    void processCompletedScan_doesNotQuarantine_whenMaliciousVerdict_zeroFindings_andScannerNotEnforced() {
        var scan = new ExtensionScan();
        scan.setId(5L);
        when(repositories.findExtensionScan(5L)).thenReturn(scan);
        var job = jobFor(scan);
        var result = Scanner.Result.of(List.of(), null, true);

        var outcome = svc.processCompletedScan(job, result, false, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.PASSED);
    }

    @Test
    void processCompletedScan_doesNotQuarantine_whenMaliciousVerdict_butAllFindingsAllowlisted() {
        // The admin-managed allowlist takes precedence over a repeat verdict on already-vetted files.
        var scan = new ExtensionScan();
        scan.setId(6L);
        when(repositories.findExtensionScan(6L)).thenReturn(scan);
        var job = jobFor(scan);
        when(fileDecisionRepository.findByFileHash("hash1"))
                .thenReturn(FileDecision.allowed("hash1", new UserData()));
        var result = Scanner.Result.of(
                List.of(new Scanner.Threat("rule", "desc", "HIGH", "file1", "hash1")),
                null,
                true);

        var outcome = svc.processCompletedScan(job, result, true, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.PASSED);
    }

    @Test
    void processCompletedScan_quarantines_whenMaliciousVerdict_andOnlySomeFindingsAllowlisted() {
        var scan = new ExtensionScan();
        scan.setId(7L);
        when(repositories.findExtensionScan(7L)).thenReturn(scan);
        var job = jobFor(scan);
        when(fileDecisionRepository.findByFileHash("hash1"))
                .thenReturn(FileDecision.allowed("hash1", new UserData()));
        var result = Scanner.Result.of(
                List.of(
                        new Scanner.Threat("rule", "desc", "HIGH", "file1", "hash1"),
                        new Scanner.Threat("rule", "desc", "HIGH", "file2", "hash2")),
                null,
                true);

        var outcome = svc.processCompletedScan(job, result, true, LocalDateTime.now());

        assertThat(outcome.checkResult()).isEqualTo(ScanCheckResult.CheckResult.QUARANTINE);
    }
}
