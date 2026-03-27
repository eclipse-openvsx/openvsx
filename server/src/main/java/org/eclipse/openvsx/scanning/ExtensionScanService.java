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

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.TimeUtil;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing extension scans.
 * <p>
 * Owns scan lifecycle (STARTED → VALIDATING → SCANNING → PASSED/QUARANTINED),
 * runs validation checks, and submits scanner jobs via JobRunr.
 */
@Component
public class ExtensionScanService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExtensionScanService.class);

    private final ExtensionScanConfig config;
    private final PublishCheckRunner checkRunner;
    private final ExtensionScanPersistenceService persistenceService;
    private final ScannerRegistry scannerRegistry;
    private final JobRequestScheduler jobScheduler;
    private final ScannerJobRepository scanJobRepository;

    public ExtensionScanService(
            ExtensionScanConfig config,
            PublishCheckRunner checkRunner,
            ExtensionScanPersistenceService persistenceService,
            ScannerRegistry scannerRegistry,
            JobRequestScheduler jobScheduler,
            ScannerJobRepository scanJobRepository
    ) {
        this.config = config;
        this.checkRunner = checkRunner;
        this.persistenceService = persistenceService;
        this.scannerRegistry = scannerRegistry;
        this.jobScheduler = jobScheduler;
        this.scanJobRepository = scanJobRepository;
    }

    /**
     * Check if extension scanning is enabled.
     * @return true if scanning is enabled via configuration
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Creates a scan record from ExtensionProcessor metadata.
     * Use this when validation runs BEFORE extension creation.
     */
    @Nonnull
    public ExtensionScan initializeScan(@Nonnull ExtensionProcessor processor, @Nonnull UserData user) {
        return initializeScan(
            processor.getNamespace(),
            processor.getExtensionName(),
            processor.getVersion(),
            processor.getTargetPlatform(),
            processor.getDisplayName(),
            user
        );
    }

    private ExtensionScan initializeScan(
            String namespaceName,
            String extensionName,
            String version,
            String targetPlatform,
            String displayName,
            UserData user
    ) {
        logger.debug("Starting publish scan for {}.{} v{}", namespaceName, extensionName, version);
        
        return persistenceService.initializeScan(
            namespaceName,
            extensionName,
            version,
            targetPlatform,
            displayName,
            user
        );
    }

    /**
     * Run validation checks and record results.
     * <p>
     * Delegates the actual check execution to ExtensionScanner,
     * then records findings and manages state transitions.
     */
    public void runValidation(
            @Nonnull ExtensionScan scan,
            @Nonnull TempFile extensionFile,
            @Nonnull UserData user
    ) {
        transitionTo(scan, ScanStatus.VALIDATING);

        var checkResult = checkRunner.runChecks(scan, extensionFile, user);

        // Record ALL check executions for audit trail (pass, fail, skip, error).
        // This gives admins visibility into what checks were run.
        for (var execution : checkResult.checkExecutions()) {
            persistenceService.recordCheckResult(
                scan,
                execution.checkType(),
                ScanCheckResult.CheckCategory.PUBLISH_CHECK,
                execution.result(),
                execution.startedAt(),
                execution.completedAt(),
                null,  // filesScanned - not applicable for publish checks
                execution.findingsCount(),
                execution.summary(),
                execution.errorMessage(),
                null,  // scannerJobId - not applicable for publish checks
                execution.required()
            );
        }

        // Handle required check errors - block publication
        if (checkResult.hasRequiredCheckError()) {
            var requiredError = checkResult.getRequiredErrors().getFirst();
            markScanAsErrored(scan, checkResult.getErrorMessage());
            throw new ErrorResultException(checkResult.getErrorMessage(), requiredError.exception());
        }

        // Record all findings in the database (detailed failure records).
        for (var finding : checkResult.findings()) {
            persistenceService.recordValidationFailure(
                scan,
                finding.checkType(),
                finding.ruleName(),
                finding.reason(),
                finding.enforced()
            );
        }

        // Handle enforced failures - block publication.
        if (checkResult.hasEnforcedFailure()) {
            transitionToTerminal(scan, ScanStatus.REJECTED);
            logger.info("Publication blocked due to policy violations: {}.{}",
                scan.getNamespaceName(), scan.getExtensionName());
            
            // Use user-facing messages from findings (which may be masked for security).
            // Detailed reasons are still stored in the database for admin review.
            var enforcedFindings = checkResult.getEnforcedFindings();
            throw new ErrorResultException(getUserFacingErrorMessage(enforcedFindings));
        }

        if (!checkResult.getWarningFindings().isEmpty()) {
            logger.warn("Policy violations detected but not enforced: {}.{}",
                scan.getNamespaceName(), scan.getExtensionName());
        }

        logger.debug("Scan {} - Validation passed", scan.getId());
    }

    /**
     * Submit long-running scanner jobs for an extension version.
     * <p>
     * Flow: transition scan to SCANNING, then for each scanner create a ScanJob (QUEUED)
     * and try to enqueue a JobRunr request. On enqueue failure we only log since the watchdog
     * will re-enqueue them for retry.
     */
    public boolean submitScannerJobs(@Nonnull ExtensionScan scan, @Nonnull ExtensionVersion extVersion) {
        if (!config.isEnabled()) {
            logger.debug("Scanning is disabled, skipping scanner jobs for: {}", 
                NamingUtil.toLogFormat(extVersion));
            return false;
        }
        
        // Get all registered scanners
        List<Scanner> scanners = scannerRegistry.getAllScanners();
        
        if (scanners.isEmpty()) {
            logger.warn("No scanners registered, skipping scanner jobs for: {}", 
                NamingUtil.toLogFormat(extVersion));
            return false;
        }
        
        // Use ExtensionScan.id as the scan ID to link everything together
        String scanId = String.valueOf(scan.getId());
        long extensionVersionId = extVersion.getId();
        
        logger.debug("Submitting {} scanner jobs for extension: {} (scanId={})",
            scanners.size(), NamingUtil.toLogFormat(extVersion), scanId);
        
        // Transition to SCANNING status before submitting jobs
        transitionTo(scan, ScanStatus.SCANNING);
        
        int enqueuedCount = 0;

        for (Scanner scanner : scanners) {
            String scannerType = scanner.getScannerType();
            var existingJob = scanJobRepository.findByScanIdAndScannerType(scanId, scannerType);
            if (existingJob.isPresent()) {
                logger.debug("Skipping duplicate ScanJob creation: {} for {} (scanId={}), existingJobId={}",
                    scannerType, NamingUtil.toLogFormat(extVersion), scanId, existingJob.get().getId());
                continue;
            }

            ScannerJob job = new ScannerJob();
            job.setScanId(scanId);
            job.setScannerType(scannerType);
            job.setExtensionVersionId(extensionVersionId);
            job.setStatus(ScannerJob.JobStatus.QUEUED);
            job.setCreatedAt(TimeUtil.getCurrentUTC());
            job.setUpdatedAt(TimeUtil.getCurrentUTC());
            job.setPollLeaseUntil(null);
            job.setPollAttempts(0);
            job.setRecoveryInProgress(false);
            scanJobRepository.save(job);
            
            if (scanner.getMaxConcurrency() <= 0) {
                try {
                    jobScheduler.enqueue(new ScannerInvocationRequest(
                        scannerType, extensionVersionId, scanId));
                    enqueuedCount++;
                } catch (Exception e) {
                    // Job record exists in QUEUED state — the watchdog will
                    // re-enqueue it after the stuck threshold
                    logger.error("Failed to enqueue scanner {} for scanId={}: {}",
                        scannerType, scanId, e.getMessage());
                }
            }
        }

        logger.debug("Enqueued {} of {} scanner jobs for: {} (scanId={})",
            enqueuedCount, scanners.size(), NamingUtil.toLogFormat(extVersion), scanId);

        return true;
    }

    /**
     * Check if there are any scanners registered for long-running scans.
     */
    public boolean hasRegisteredScanners() {
        return !scannerRegistry.getAllScanners().isEmpty();
    }

    public void markScanPassed(@Nullable ExtensionScan scan) {
        if (scan == null) return;
        if (scan.getStatus().isCompleted()) {
            logger.debug("Scan {} already completed with status {}, skipping success marking",
                scan.getId(), scan.getStatus());
            return;
        }
        transitionToTerminal(scan, ScanStatus.PASSED);
    }

    public void quarantineScan(@Nullable ExtensionScan scan) {
        if (scan == null) return;
        if (scan.getStatus().isCompleted()) {
            logger.debug("Scan {} already completed with status {}, skipping quarantine marking",
                scan.getId(), scan.getStatus());
            return;
        }
        transitionToTerminal(scan, ScanStatus.QUARANTINED);
    }

    /**
     * Mark scan as REJECTED due to publish check/validation failure.
     * Used when policy violations or malicious content is detected during validation.
     */
    public void rejectScan(@Nullable ExtensionScan scan) {
        if (scan == null) return;
        if (scan.getStatus().isCompleted()) {
            logger.debug("Scan {} already completed with status {}, skipping rejection",
                scan.getId(), scan.getStatus());
            return;
        }
        transitionToTerminal(scan, ScanStatus.REJECTED);
    }

    public void markScanAsErrored(@Nullable ExtensionScan scan, @Nullable String errorMessage) {
        if (scan == null) return;
        if (scan.getStatus().isCompleted()) {
            logger.debug("Scan {} already completed with status {}, skipping error marking",
                scan.getId(), scan.getStatus());
            return;
        }
        validateTransition(scan.getStatus(), ScanStatus.ERRORED);
        persistenceService.markAsErrored(scan, errorMessage);
    }

    private void transitionTo(ExtensionScan scan, ScanStatus newStatus) {
        validateTransition(scan.getStatus(), newStatus);
        persistenceService.updateStatus(scan, newStatus);
    }
    
    private void transitionToTerminal(ExtensionScan scan, ScanStatus newStatus) {
        validateTransition(scan.getStatus(), newStatus);
        persistenceService.completeWithStatus(scan, newStatus);
    }
    
    private void validateTransition(ScanStatus from, ScanStatus to) {
        if (!isValidTransition(from, to)) {
            throw new IllegalStateException(String.format(
                "Invalid scan state transition: %s -> %s", from, to
            ));
        }
    }
    
    private boolean isValidTransition(ScanStatus from, ScanStatus to) {
        if (from.isCompleted()) {
            return false;
        }
        
        return switch (from) {
            // Any in-progress state can end with an error.
            case STARTED -> to == ScanStatus.VALIDATING || to == ScanStatus.SCANNING || to == ScanStatus.ERRORED;
            // VALIDATING can go to PASSED if no scanners, REJECTED for policy failures,
            // or QUARANTINED for security concerns
            case VALIDATING -> to == ScanStatus.SCANNING || to == ScanStatus.REJECTED || to == ScanStatus.PASSED || to == ScanStatus.QUARANTINED || to == ScanStatus.ERRORED;
            case SCANNING -> to == ScanStatus.PASSED || to == ScanStatus.QUARANTINED || to == ScanStatus.ERRORED;
            default -> false;
        };
    }

    public void removeScan(@Nonnull ExtensionScan scan) {
        persistenceService.removeScan(scan);
    }

    /**
     * Build user-facing error message from findings.
     * 
     * Uses the userFacingMessage from each check, which may be masked for security
     * Detailed reasons are stored in the database for admin review.
     */
    private String getUserFacingErrorMessage(List<PublishCheckRunner.Finding> findings) {
        if (findings.isEmpty()) {
            return "Extension publication blocked.";
        }

        // Group findings by check type, using the user-facing message (one per check type).
        var messagesByType = findings.stream()
                .collect(Collectors.groupingBy(PublishCheckRunner.Finding::checkType));

        var parts = new java.util.ArrayList<String>();
        for (var entry : messagesByType.entrySet()) {
            var typeFindings = entry.getValue();
            String userMessage = typeFindings.getFirst().userFacingMessage();
            if (userMessage != null && !userMessage.isBlank()) {
                parts.add(userMessage);
            }
        }

        if (parts.isEmpty()) {
            return "Extension publication blocked.";
        }

        return "Extension publication blocked: " + String.join(", ", parts) + ". For details on " + 
            "publishing extensions, see: https://github.com/eclipse/openvsx/wiki/Publishing-Extensions";
    }
}