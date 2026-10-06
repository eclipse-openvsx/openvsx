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

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionThreat;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.repositories.ExtensionScanRepository;
import org.eclipse.openvsx.repositories.ExtensionThreatRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code ScannerInvocationHandler} now persists threats before claiming a job's final status
 * (see its own commit history), so a job that loses that claim to the watchdog - ending up
 * FAILED - can still have threat rows already committed under its id. These tests cover that
 * completion must not count those orphaned threats toward a quarantine decision: only a job
 * that is actually COMPLETE gets to contribute to enforcement.
 */
@ExtendWith(MockitoExtension.class)
class ExtensionScanCompletionServiceTest {

    @Mock
    ExtensionScanRepository scanRepository;
    @Mock
    ScannerJobRepository scanJobRepository;
    @Mock
    ExtensionThreatRepository extensionThreatRepository;
    @Mock
    EntityManager entityManager;
    @Mock
    PublishExtensionVersionService publishService;
    @Mock
    ExtensionService extensionService;
    @Mock
    ScannerRegistry scannerRegistry;
    @Mock
    JobRequestScheduler jobScheduler;
    @Mock
    ExtensionScanService scanService;
    @Mock
    RepositoryService repositories;
    @Mock
    ExtensionScanPersistenceService persistenceService;
    @Mock
    Scanner requiredScanner;
    @Mock
    Scanner optionalScanner;

    private ExtensionScanCompletionService newService() {
        return new ExtensionScanCompletionService(
                scanRepository,
                scanJobRepository,
                extensionThreatRepository,
                entityManager,
                publishService,
                extensionService,
                scannerRegistry,
                jobScheduler,
                scanService,
                repositories,
                persistenceService);
    }

    private ExtensionScan scanningScan() {
        var scan = new ExtensionScan();
        scan.setId(100L);
        scan.setStatus(ScanStatus.SCANNING);
        scan.setStartedAt(LocalDateTime.now());
        return scan;
    }

    private ScannerJob job(long id, String scannerType, ScannerJob.JobStatus status) {
        var job = new ScannerJob();
        job.setId(id);
        job.setScanId("100");
        job.setScannerType(scannerType);
        job.setExtensionVersionId(500L);
        job.setStatus(status);
        return job;
    }

    private ExtensionVersion extensionVersion() {
        var namespace = new Namespace();
        namespace.setName("acme");
        var extension = new Extension();
        extension.setName("widget");
        extension.setNamespace(namespace);
        var extVersion = new ExtensionVersion();
        extVersion.setId(500L);
        extVersion.setVersion("1.0.0");
        extVersion.setExtension(extension);
        return extVersion;
    }

    private ExtensionThreat enforcedThreat() {
        var threat = new ExtensionThreat();
        threat.setEnforced(true);
        threat.setRuleName("malware");
        threat.setSeverity("HIGH");
        threat.setFileName("extension.js");
        return threat;
    }

    @Test
    void checkSingleScanCompletion_ignoresThreatsFromAnOptionalScannerThatLostItsClaim() {
        when(scanRepository.findById(100L)).thenReturn(scanningScan());
        var completeJob = job(1L, "clamav-rest", ScannerJob.JobStatus.COMPLETE);
        var failedOptionalJob = job(2L, "yara", ScannerJob.JobStatus.FAILED);
        when(scanJobRepository.findByScanId("100")).thenReturn(List.of(completeJob, failedOptionalJob));

        when(requiredScanner.getScannerType()).thenReturn("clamav-rest");
        when(optionalScanner.getScannerType()).thenReturn("yara");
        when(optionalScanner.isRequired()).thenReturn(false);
        when(scannerRegistry.getAllScanners()).thenReturn(List.of(requiredScanner, optionalScanner));
        // Classifying the FAILED job as optional (vs. required, which would ERROR the whole
        // scan before the threat-counting loop this test targets is ever reached) goes through
        // a separate lookup by scanner type, not getAllScanners().
        when(scannerRegistry.getScanner("yara")).thenReturn(optionalScanner);

        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 500L)).thenReturn(extVersion);

        when(extensionThreatRepository.findByJobId(1L)).thenReturn(List.of());
        // Orphaned threat left behind by the failed claim: must never be read once the fix
        // skips non-COMPLETE jobs, so this is stubbed leniently to prove it stays unused.
        lenient().when(extensionThreatRepository.findByJobId(2L)).thenReturn(List.of(enforcedThreat()));

        newService().checkSingleScanCompletion("100");

        verify(extensionThreatRepository, never()).findByJobId(2L);
        verify(scanService, never()).quarantineScan(any());
        verify(publishService).activateExtension(extVersion, extensionService);
        verify(scanService).markScanPassed(any());
    }

    @Test
    void checkSingleScanCompletion_quarantinesOnThreatsFromACompleteJob() {
        when(scanRepository.findById(100L)).thenReturn(scanningScan());
        var completeJob = job(1L, "clamav-rest", ScannerJob.JobStatus.COMPLETE);
        when(scanJobRepository.findByScanId("100")).thenReturn(List.of(completeJob));

        when(requiredScanner.getScannerType()).thenReturn("clamav-rest");
        when(scannerRegistry.getAllScanners()).thenReturn(List.of(requiredScanner));

        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 500L)).thenReturn(extVersion);

        when(extensionThreatRepository.findByJobId(1L)).thenReturn(List.of(enforcedThreat()));

        newService().checkSingleScanCompletion("100");

        verify(scanService).quarantineScan(any());
        verify(publishService, never()).activateExtension(any(), any());
    }
}
