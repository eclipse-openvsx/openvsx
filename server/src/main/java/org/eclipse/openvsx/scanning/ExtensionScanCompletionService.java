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

import jakarta.persistence.EntityManager;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ExtensionThreat;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.repositories.ExtensionScanRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.repositories.ExtensionThreatRepository;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that checks when all scans are complete and activates extensions.
 * <p>
 * Primary: event-driven via checkSingleScanCompletion() for immediate activation.
 * Fallback: polls every 60s to catch missed completions.
 */
@Service
public class ExtensionScanCompletionService {
    
    protected final Logger logger = LoggerFactory.getLogger(ExtensionScanCompletionService.class);
    
    private final ExtensionScanRepository scanRepository;
    private final ScannerJobRepository scanJobRepository;
    private final ExtensionThreatRepository extensionThreatRepository;
    private final EntityManager entityManager;
    private final PublishExtensionVersionService publishService;
    private final ExtensionService extensionService;
    private final ScannerRegistry scannerRegistry;
    private final org.jobrunr.scheduling.JobRequestScheduler jobScheduler;
    private final ExtensionScanService scanService;
    private final RepositoryService repositories;
    private final ExtensionScanPersistenceService persistenceService;
    
    public ExtensionScanCompletionService(
            ExtensionScanRepository scanRepository,
            ScannerJobRepository scanJobRepository,
            ExtensionThreatRepository extensionThreatRepository,
            EntityManager entityManager,
            PublishExtensionVersionService publishService,
            ExtensionService extensionService,
            ScannerRegistry scannerRegistry,
            org.jobrunr.scheduling.JobRequestScheduler jobScheduler,
            ExtensionScanService scanService,
            RepositoryService repositories,
            ExtensionScanPersistenceService persistenceService
    ) {
        this.scanRepository = scanRepository;
        this.scanJobRepository = scanJobRepository;
        this.extensionThreatRepository = extensionThreatRepository;
        this.entityManager = entityManager;
        this.publishService = publishService;
        this.extensionService = extensionService;
        this.scannerRegistry = scannerRegistry;
        this.jobScheduler = jobScheduler;
        this.scanService = scanService;
        this.repositories = repositories;
        this.persistenceService = persistenceService;
    }
    
    /**
     * Check completion for a single scan, catching and logging any errors.
     * 
     * Called inline after scanner jobs finish (within existing transaction).
     * Errors are logged but don't fail the caller - the recovery service
     * will catch any missed completions on next restart.
     */
    public void checkCompletionSafely(String scanId) {
        try {
            checkSingleScanCompletion(scanId);
        } catch (Exception e) {
            logger.warn("Failed inline completion check for scanId={}: {}", scanId, e.getMessage());
        }
    }

    /**
     * Check completion for a single scan (event-driven).
     * 
     * Called by checkCompletionSafely after scanner jobs finish.
     * Also called directly by the recovery service.
     * 
     * Note: @Transactional needed for direct calls from recovery service.
     * When called via checkCompletionSafely, participates in existing transaction.
     */
    @Transactional
    public void checkSingleScanCompletion(String scanId) {
        try {
            // Load the ExtensionScan record
            ExtensionScan scanResult = scanRepository.findById(Long.parseLong(scanId));
            if (scanResult == null) {
                logger.warn("ExtensionScan not found for scanId={}", scanId);
                return;
            }
            
            // Only process if still in SCANNING status
            if (scanResult.getStatus() != ScanStatus.SCANNING) {
                logger.debug("Scan {} not in SCANNING status (status={}), skipping", 
                    scanId, scanResult.getStatus());
                return;
            }
            
            // Check and process this single scan
            processSingleScan(scanResult);
            
        } catch (NumberFormatException e) {
            logger.error("Invalid scanId format: {}", scanId);
        } catch (Exception e) {
            logger.error("Error in event-driven completion check for scanId={}", scanId, e);
        }
    }
    
    /**
     * Maximum number of scans to process per polling cycle.
     * This prevents the fallback job from hogging a worker for too long.
     */
    // Safe to be high since query uses LIMIT - we only load what we process
    private static final int MAX_SCANS_PER_CYCLE = 100;
    
    /**
     * Process completed scans and activate extensions when all scans pass.
     * 
     * FALLBACK: Runs every 5 minutes using JobRunr distributed scheduling.
     * Only one pod executes this at a time (distributed lock).
     * 
     * IMPORTANT: Primary completion checking is EVENT-DRIVEN via checkSingleScanCompletion()
     * which is called immediately when each scanner job finishes. This polling job is only
     * a safety net for edge cases (missed events, server crashes, etc.).
     * 
     * To avoid blocking workers during high load:
     * - Runs less frequently (every 5 minutes instead of 1)
     * - Processes at most MAX_SCANS_PER_CYCLE scans per run
     * - Prioritizes oldest scans first (FIFO)
     * 
     * Strategy:
     * 1. Find all ExtensionScanResult records in SCANNING status (oldest first)
     * 2. For each one (up to limit), check if all associated ScanJobs are complete
     * 3. If complete, aggregate results and activate or quarantine
     */
    @Job(name = "Process completed scans", retries = 0)
    @Recurring(id = "process-completed-scans", interval = "PT5M")
    public void processCompletedScans() {
        try {
            logger.debug("Starting scan completion check cycle (fallback)");
            
            // Only load the oldest MAX_SCANS_PER_CYCLE scans - let DB do the sorting
            // This is much faster than loading all 900+ scans and sorting in memory
            List<ExtensionScan> scanningExtensions = 
                scanRepository.findOldestByStatus(ScanStatus.SCANNING, MAX_SCANS_PER_CYCLE);
            
            if (scanningExtensions.isEmpty()) {
                logger.debug("No extensions currently in SCANNING status");
                return;
            }
            
            logger.debug("Processing {} oldest scans in SCANNING status", scanningExtensions.size());
            
            int activatedCount = 0;
            int quarantinedCount = 0;
            int processedCount = 0;
            
            for (ExtensionScan scanResult : scanningExtensions) {
                Boolean result = processSingleScan(scanResult);
                processedCount++;
                
                if (result == null) {
                    // Not ready yet or error
                    continue;
                } else if (result) {
                    activatedCount++;
                } else {
                    quarantinedCount++;
                }
            }
            
            if (activatedCount > 0 || quarantinedCount > 0) {
                logger.info("Scan completion cycle finished: {} activated, {} quarantined of {} checked",
                    activatedCount, quarantinedCount, processedCount);
            } else {
                logger.debug("Scan completion cycle finished: {} checked, none ready for activation",
                    processedCount);
            }
            
        } catch (Exception e) {
            logger.error("Error in scan completion check cycle", e);
        }
    }
    
    /**
     * Process a single scan to check if it's complete.
     */
    private Boolean processSingleScan(ExtensionScan scanResult) {
        long scanId = scanResult.getId();
        String scanIdStr = String.valueOf(scanId);
        
        // RECOVERY: Check if extension is already active but scan stuck in SCANNING
        // This can happen if server crashed after activateExtension() but before markScanPassed()
        ExtensionVersion extVersion = findExtensionVersion(scanResult);
        if (extVersion != null && extVersion.isActive()) {
            logger.debug("Extension already active but scan {} still in SCANNING - marking as PASSED (recovery)",
                scanId);
            scanService.markScanPassed(scanResult);
            return true;
        }
        
        // Load all scan jobs for this scanId
        List<ScannerJob> jobs = scanJobRepository.findByScanId(scanIdStr);
        
        if (jobs.isEmpty()) {
            logger.warn("No scan jobs found for scanId={} ({})", scanId, formatExtensionId(scanResult));
            return null;
        }

        // Check if we should wait for more jobs to be created
        // This handles the case where scanners are added/removed dynamically
        if (!shouldProceedWithCompletion(scanResult, jobs)) {
            return null;
        }
            
        // Check if all jobs in this scan are terminal (finished)
        if (!allJobsTerminal(jobs)) {
            logger.debug("Extension still has pending jobs: scanId={} pending={}", 
                scanId, jobs.size() - countTerminalJobs(jobs));
            return null;
        }
        
        // All jobs are done - process the results
        try {
            return completeExtensionScan(scanIdStr, jobs);
        } catch (Exception e) {
            logger.error("Failed to process completed scan group: scanId={}", scanIdStr, e);
            return null;
        }
    }
    
    /**
     * Determine if we should proceed with scan completion or wait for more jobs.
     * 
     * This method dynamically checks which scanners are CURRENTLY active and ensures
     * all active scanners have jobs (terminal or not).
     * 
     * Strategy:
     * 1. Get list of currently active scanners from ScannerRegistry
     * 2. Check which scanners have jobs for this extension
     * 3. If missing scanners AND > 5 minutes since scan started → create jobs for new scanners
     * 4. If all active scanners have terminal jobs → proceed with completion
     * 
     * Edge cases handled:
     * 
     * 1. Scanner Removed: Jobs for removed scanners are ignored (not in active list)
     * 2. Scanner Added: After 5 minutes, create jobs for new scanners
     * 3. Jobs Still Being Created: Wait < 5 minutes for initial job creation
     */
    private boolean shouldProceedWithCompletion(ExtensionScan scanResult, List<ScannerJob> jobs) {
        // Get currently active scanners from registry
        // This is DYNAMIC - reflects current configuration, not snapshot from publish time
        List<Scanner> activeScanners = scannerRegistry.getAllScanners();
        
        if (activeScanners.isEmpty()) {
            // No scanners active - proceed with whatever we have
            logger.warn("No active scanners found for scanId={}, proceeding with {} existing jobs",
                scanResult.getId(), jobs.size());
            return true;
        }
        
        // Build set of active scanner types
        Set<String> activeScannerTypes = activeScanners.stream()
            .map(Scanner::getScannerType)
            .collect(Collectors.toSet());
        
        // Build set of scanner types that have jobs (any status)
        Set<String> scannersWithJobs = jobs.stream()
            .map(ScannerJob::getScannerType)
            .collect(Collectors.toSet());
        
        // Find scanners that are active but don't have jobs yet
        Set<String> missingScanners = new HashSet<>(activeScannerTypes);
        missingScanners.removeAll(scannersWithJobs);
        
        if (!missingScanners.isEmpty()) {
            // Some active scanners don't have jobs
            LocalDateTime scanStarted = scanResult.getStartedAt();
            if (scanStarted != null) {
                long minutesElapsed = java.time.Duration.between(scanStarted, TimeUtil.getCurrentUTC()).toMinutes();
                
                if (minutesElapsed > 1) {
                    // Been scanning for > 1 minutes
                    // These are likely scanners that were added after publishing started
                    // Create jobs for them so they scan this extension too
                    logger.info("ScanId {} has been scanning for {} minutes - creating jobs for newly added scanners: {}",
                        scanResult.getId(), minutesElapsed, missingScanners);
                    
                    createMissingScanJobs(scanResult, jobs, missingScanners);
                    
                    // Don't proceed yet - wait for the newly created jobs to complete
                    return false;
                } else {
                    // Just started - give jobs time to be created
                    logger.debug("ScanId {} waiting for initial job creation for scanners {} (elapsed: {} min)",
                        scanResult.getId(), missingScanners, minutesElapsed);
                    return false;
                }
            }
            
            // No start time - don't proceed
            logger.debug("ScanId {} missing start time, waiting", scanResult.getId());
            return false;
        }
        
        // All active scanners have jobs - check if they're all terminal
        Set<String> terminalScanners = jobs.stream()
            .filter(job -> job.getStatus().isTerminal())
            .map(ScannerJob::getScannerType)
            .collect(Collectors.toSet());
        
        // Only check scanners that are currently active
        // Ignore jobs for removed scanners
        Set<String> pendingScanners = new HashSet<>(activeScannerTypes);
        pendingScanners.removeAll(terminalScanners);
        
        String extId = formatExtensionId(scanResult);
        
        if (pendingScanners.isEmpty()) {
            // All active scanners have terminal jobs - ready to complete
            logger.debug("ScanId {} ({}) has all {} active scanners terminal",
                scanResult.getId(), extId, activeScannerTypes.size());
            return true;
        }
        
        // Some active scanners still pending - keep waiting
        logger.debug("ScanId {} ({}) waiting for active scanners to complete: {}",
            scanResult.getId(), extId, pendingScanners);
        return false;
    }
    
    /**
     * Create scan jobs for scanners that were added after publishing started.
     * 
     * This allows new scanners to retroactively scan extensions that are still
     * in the scanning phase.
     */
    private void createMissingScanJobs(ExtensionScan scanResult, List<ScannerJob> existingJobs, Set<String> missingScannerTypes) {
        String scanId = String.valueOf(scanResult.getId());
        
        // Get extensionVersionId from existing jobs (all jobs for a scan share the same extension version)
        if (existingJobs.isEmpty()) {
            logger.warn("Cannot create missing scan jobs - no existing jobs to get extensionVersionId from. scanId={}", scanId);
            return;
        }
        long extensionVersionId = existingJobs.getFirst().getExtensionVersionId();
        
        for (String scannerType : missingScannerTypes) {
            try {
                // Create scanner invocation job request
                ScannerInvocationRequest jobRequest = new ScannerInvocationRequest(
                    scannerType,
                    extensionVersionId,
                    scanId
                );
                
                // Enqueue to JobRunr - returns immediately
                jobScheduler.enqueue(jobRequest);
                
                logger.debug("Enqueued retroactive scanner invocation for newly added scanner: {} scanId={} extensionVersionId={}",
                    scannerType, scanId, extensionVersionId);
                
            } catch (Exception e) {
                logger.error("Failed to enqueue retroactive scanner invocation for scanner {} scanId={}",
                    scannerType, scanId, e);
                // Continue with other scanners even if one fails
            }
        }
    }
    
    /**
     * Count how many jobs are in terminal state.
     */
    private long countTerminalJobs(List<ScannerJob> jobs) {
        return jobs.stream()
            .filter(job -> job.getStatus().isTerminal())
            .count();
    }
    
    /**
     * Check if all jobs in a scan group are terminal (finished).
     */
    private boolean allJobsTerminal(List<ScannerJob> jobs) {
        return jobs.stream().allMatch(job -> job.getStatus().isTerminal());
    }
    
    /**
     * Process a completed scan group and activate extension if scans passed.
     * 
     * Logic:
     * 1. Check if any jobs FAILED
     * 2. Load all threats from all jobs
     * 3. If no failures and no threats: activate extension
     * 4. If failures or threats: log and skip (keep inactive)
     */
    private boolean completeExtensionScan(String scanId, List<ScannerJob> jobs) {
        // Get extension version ID from first job
        // All jobs in a scan group scan the same extension version
        long extensionVersionId = jobs.getFirst().getExtensionVersionId();
        
        // Load extension version entity
        ExtensionVersion extVersion = entityManager.find(ExtensionVersion.class, extensionVersionId);
        if (extVersion == null) {
            logger.error("Extension version not found: id={}, scanId={}", 
                extensionVersionId, scanId);
            return false;
        }
        
        // If extension is already active, this scan group was already processed
        // This can happen if JobRunr retries or if multiple completion cycles run
        // Return true to indicate successful activation (idempotent)
        if (extVersion.isActive()) {
            logger.debug("Extension already active, skipping reprocessing: {} scanId={}", 
                NamingUtil.toLogFormat(extVersion), scanId);
            return true;
        }
        
        // Check for failed jobs (ignore REMOVED jobs - those are from removed scanners)
        List<ScannerJob> failedJobs = jobs.stream()
            .filter(job -> job.getStatus() == ScannerJob.JobStatus.FAILED)
            .toList();
        
        if (!failedJobs.isEmpty()) {
            // Check if any REQUIRED scanners failed
            // Optional scanners (typically external) can fail without blocking activation
            List<ScannerJob> requiredFailedJobs = new ArrayList<>();
            List<ScannerJob> optionalFailedJobs = new ArrayList<>();
            
            for (ScannerJob failedJob : failedJobs) {
                // Record ERROR result for audit trail (if not already recorded)
                if (!repositories.hasScanCheckResult(Long.parseLong(scanId), failedJob.getScannerType())) {
                    // Include the actual error reason in the summary so it's visible in the UI
                    String errorDetail = failedJob.getErrorMessage();
                    String failSummary = errorDetail != null
                        ? "Scanner failed: " + errorDetail
                        : "Scanner failed (no details available)";
                    persistenceService.recordScannerJobResult(
                        scanId,
                        failedJob,
                        ScanCheckResult.CheckResult.ERROR,
                        failedJob.getCreatedAt(),
                        0,
                        0,
                        failSummary,
                        errorDetail
                    );
                }
                
                Scanner scanner = scannerRegistry.getScanner(failedJob.getScannerType());
                if (scanner != null && !scanner.isRequired()) {
                    optionalFailedJobs.add(failedJob);
                    logger.warn("Optional scanner failed (not blocking activation): {} error: {}",
                        failedJob.getScannerType(), failedJob.getErrorMessage());
                } else {
                    requiredFailedJobs.add(failedJob);
                    logger.error("Required scanner failed (blocking activation): {} error: {}",
                        failedJob.getScannerType(), failedJob.getErrorMessage());
                }
            }
            
            // If any REQUIRED scanners failed (errored), block activation (fail-closed)
            // Note: This is different from threats - scanner errors mean the scan couldn't complete
            if (!requiredFailedJobs.isEmpty()) {
                logger.error("Extension scan group has {} REQUIRED scanner(s) ERRORED, marking as error: {} scanId={}",
                    requiredFailedJobs.size(), NamingUtil.toLogFormat(extVersion), scanId);
                
                // Build error message listing failed required scanners
                var scannerErrors = requiredFailedJobs.stream()
                    .map(job -> {
                        String msg = job.getScannerType();
                        if (job.getErrorMessage() != null) {
                            msg += " (" + job.getErrorMessage() + ")";
                        }
                        return msg;
                    })
                    .toList();
                String errorMessage = requiredFailedJobs.size() + " required scanner(s) errored: " 
                    + String.join(", ", scannerErrors);
                
                // Update ExtensionScan status to ERRORED (not QUARANTINED - that's for threats)
                ExtensionScan scan = scanRepository.findById(Long.parseLong(scanId));
                if (scan != null) {
                    scanService.markScanAsErrored(scan, errorMessage);
                }
                
                return false;
            }
            
            // Only optional scanners failed - log but allow activation
            if (!optionalFailedJobs.isEmpty()) {
                logger.debug("Extension has {} optional scanner(s) failed, but proceeding with activation: {} scanId={}",
                    optionalFailedJobs.size(), NamingUtil.toLogFormat(extVersion), scanId);
            }
        }
        
        // Log any removed scanners for informational purposes
        long removedCount = jobs.stream()
            .filter(job -> job.getStatus() == ScannerJob.JobStatus.REMOVED)
            .count();
        if (removedCount > 0) {
            logger.debug("Extension scan group includes {} REMOVED scanner(s) - these will be ignored: {} scanId={}",
                removedCount, NamingUtil.toLogFormat(extVersion), scanId);
        }
        
        // All jobs completed successfully - check for threats
        // Load all threats from all jobs in this scan group
        // Separate enforced threats (block activation) from non-enforced (warning only)
        int enforcedThreatCount = 0;
        int warningThreatCount = 0;
        
        for (ScannerJob job : jobs) {
            List<ExtensionThreat> threats = extensionThreatRepository.findByJobId(job.getId());
            
            if (!threats.isEmpty()) {
                // Separate enforced vs non-enforced threats
                List<ExtensionThreat> enforcedThreats = threats.stream()
                    .filter(ExtensionThreat::isEnforced)
                    .toList();
                List<ExtensionThreat> warningThreats = threats.stream()
                    .filter(t -> !t.isEnforced())
                    .toList();
                
                enforcedThreatCount += enforcedThreats.size();
                warningThreatCount += warningThreats.size();
                
                if (!enforcedThreats.isEmpty()) {
                    logger.warn("Scanner {} found {} ENFORCED threats (blocking activation) in extension: {} scanId={}",
                        job.getScannerType(), enforcedThreats.size(), 
                        NamingUtil.toLogFormat(extVersion), scanId);
                    
                    for (ExtensionThreat threat : enforcedThreats) {
                        logger.warn("  - [ENFORCED] Threat: {} severity={} file={}", 
                            threat.getRuleName(), 
                            threat.getSeverity(),
                            threat.getFileName());
                    }
                }
                
                if (!warningThreats.isEmpty()) {
                    logger.info("Scanner {} found {} non-enforced threats (warning only) in extension: {} scanId={}",
                        job.getScannerType(), warningThreats.size(), 
                        NamingUtil.toLogFormat(extVersion), scanId);
                    
                    for (ExtensionThreat threat : warningThreats) {
                        logger.info("  - [WARNING] Threat: {} severity={} file={}", 
                            threat.getRuleName(), 
                            threat.getSeverity(),
                            threat.getFileName());
                    }
                }
            }
        }
        
        // Only block activation if there are ENFORCED threats
        if (enforcedThreatCount > 0) {
            // Enforced threats found - keep extension inactive (quarantined)
            logger.warn("Extension has {} enforced threats (plus {} warnings), keeping inactive (quarantined): {} scanId={}",
                enforcedThreatCount, warningThreatCount, NamingUtil.toLogFormat(extVersion), scanId);
            
            // Update ExtensionScan status to QUARANTINED
            ExtensionScan scan = scanRepository.findById(Long.parseLong(scanId));
            if (scan != null) {
                scanService.quarantineScan(scan);
            }
            
            return false;
        }
        
        // Log warning if there are non-enforced threats but proceeding anyway
        if (warningThreatCount > 0) {
            logger.warn("Extension has {} non-enforced threats (warnings only), proceeding with activation: {} scanId={}",
                warningThreatCount, NamingUtil.toLogFormat(extVersion), scanId);
        }
        
        // All scans passed with no threats - activate the extension!
        logger.info("Activating extension version: {}, all {} scans passed with no enforced threats scanId={}",
            NamingUtil.toLogFormat(extVersion), jobs.size(), scanId);
        
        // Activate extension first
        // This sets active=true and updates the extension metadata
        publishService.activateExtension(extVersion, extensionService);
        
        // Now mark scan as passed
        ExtensionScan scan = scanRepository.findById(Long.parseLong(scanId));
        if (scan != null) {
            scanService.markScanPassed(scan);
        }
        
        return true;
    }
    
    /**
     * Find the ExtensionVersion associated with an ExtensionScan.
     * 
     * Uses the namespace/extension/version/platform from the scan record
     * to look up the actual ExtensionVersion entity.
     */
    private ExtensionVersion findExtensionVersion(ExtensionScan scan) {
        if (scan == null) {
            return null;
        }
        
        try {
            var extension = repositories.findExtension(scan.getExtensionName(), scan.getNamespaceName());
            if (extension == null) {
                return null;
            }
            
            return repositories.findVersion(
                scan.getExtensionVersion(),
                scan.getTargetPlatform(),
                extension
            );
        } catch (Exception e) {
            logger.warn("Failed to find extension version for scan {}: {}", 
                scan.getId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Allow a quarantined scan (admin decision) and activate the extension.
     */
    @Transactional
    public boolean adminAllowScan(ExtensionScan scan) {
        try {
            // Use findExtensionVersionIncludingInactive because quarantined extensions are inactive
            var detachedVersion = repositories.findExtensionVersionIncludingInactive(
                scan.getNamespaceName(),
                scan.getExtensionName(),
                scan.getTargetPlatform(),
                scan.getExtensionVersion()
            );
            
            if (detachedVersion == null) {
                logger.warn("Extension version not found for scan #{}: {}.{} v{} ({})",
                    scan.getId(),
                    scan.getNamespaceName(),
                    scan.getExtensionName(),
                    scan.getExtensionVersion(),
                    scan.getTargetPlatform());
                return false;
            }
            
            // Load managed entity for modifications
            var extVersion = entityManager.find(ExtensionVersion.class, detachedVersion.getId());
            if (extVersion == null) {
                logger.warn("Could not load managed entity for extension version ID {} (scan #{})",
                    detachedVersion.getId(), scan.getId());
                return false;
            }
            
            // Already active - just mark scan passed
            if (extVersion.isActive()) {
                logger.info("Extension already active for scan #{}: {}",
                    scan.getId(), NamingUtil.toLogFormat(extVersion));
                scanService.markScanPassed(scan);
                return true;
            }
            
            // Mark scan passed and activate extension
            scanService.markScanPassed(scan);
            publishService.activateExtension(extVersion, extensionService);
            
            logger.info("Extension activated by admin decision: {} (scan #{})",
                NamingUtil.toLogFormat(extVersion), scan.getId());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to activate extension for scan #{}: {}",
                scan.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Format extension identifier from ExtensionScan for logging.
     * Uses cached namespace/name/version fields.
     */
    private String formatExtensionId(ExtensionScan scan) {
        return scan.getNamespaceName() + "." + scan.getExtensionName() + " " + scan.getExtensionVersion();
    }
}

