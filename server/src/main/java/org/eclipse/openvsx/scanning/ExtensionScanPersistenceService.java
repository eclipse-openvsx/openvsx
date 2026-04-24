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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.FileDecisionRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static jakarta.transaction.Transactional.TxType;

/**
 * Handles all transactional persistence operations for extension scans.
 * 
 * This service is separate from ExtensionScanService to avoid self-invocation issues
 * where @Transactional(TxType.REQUIRES_NEW) would be ignored.
 * 
 * Responsibilities:
 * - Creating and updating ExtensionScan records
 * - Recording validation failures
 * - Saving threats from scanner jobs
 * 
 * All methods use REQUIRES_NEW to ensure they commit independently,
 * preserving the scan audit trail even when the outer transaction rolls back.
 */
@Service
public class ExtensionScanPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionScanPersistenceService.class);

    private final RepositoryService repositories;
    private final ObjectMapper objectMapper;
    private final FileDecisionRepository fileDecisionRepository;
    private final ScannerJobRepository scannerJobRepository;
    private final ScannerRegistry scannerRegistry;

    public ExtensionScanPersistenceService(
            RepositoryService repositories,
            ObjectMapper objectMapper,
            FileDecisionRepository fileDecisionRepository,
            ScannerJobRepository scannerJobRepository,
            ScannerRegistry scannerRegistry
    ) {
        this.repositories = repositories;
        this.objectMapper = objectMapper;
        this.fileDecisionRepository = fileDecisionRepository;
        this.scannerJobRepository = scannerJobRepository;
        this.scannerRegistry = scannerRegistry;
    }

    /**
     * Creates and persists a new scan record BEFORE an extension version exists.
     */
    @Transactional(TxType.REQUIRES_NEW)
    @Nonnull
    public ExtensionScan initializeScan(
            @Nonnull String namespaceName,
            @Nonnull String extensionName,
            @Nonnull String version,
            @Nullable String targetPlatform,
            @Nullable String displayName,
            @Nonnull UserData user
    ) {
        var isUniversal = targetPlatform == null || "universal".equals(targetPlatform);
        if (displayName == null || displayName.isBlank()) {
            displayName = extensionName;
        }
        
        return initializeScanInternal(
            namespaceName,
            extensionName,
            version,
            targetPlatform,
            isUniversal,
            displayName,
            user
        );
    }

    /**
     * Internal method that handles the actual scan creation logic.
     */
    private ExtensionScan initializeScanInternal(
            String namespaceName,
            String extensionName,
            String version,
            String targetPlatform,
            boolean isUniversal,
            String displayName,
            UserData user
    ) {
        try {
            var scan = new ExtensionScan();
            // Store raw values instead of foreign keys for audit trail
            scan.setNamespaceName(namespaceName);
            scan.setExtensionName(extensionName);
            scan.setExtensionVersion(version);
            scan.setTargetPlatform(targetPlatform);
            scan.setUniversalTargetPlatform(isUniversal);
            scan.setExtensionDisplayName(displayName);
            
            var publisherLoginName = user.getLoginName() != null ? user.getLoginName() : "unknown";
            var publisherUrl = user.getProviderUrl();
            scan.setPublisher(publisherLoginName);
            scan.setPublisherUrl(publisherUrl);
            
            scan.setStartedAt(TimeUtil.getCurrentUTC());
            scan.setStatus(ScanStatus.STARTED);
            
            return repositories.saveExtensionScan(scan);
        } catch (Exception e) {
            logger.error("FATAL: Failed to create extension scan", e);
            throw e;
        }
    }

    /**
     * Persists a status change. The caller is responsible for validating the transition.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void updateStatus(@Nonnull ExtensionScan scan, @Nonnull ScanStatus newStatus) {
        scan.setStatus(newStatus);
        repositories.saveExtensionScan(scan);
    }

    /**
     * Persists a terminal status change with completion timestamp.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void completeWithStatus(@Nonnull ExtensionScan scan, @Nonnull ScanStatus newStatus) {
        scan.setStatus(newStatus);
        scan.setCompletedAt(TimeUtil.getCurrentUTC());
        repositories.saveExtensionScan(scan);
    }

    /**
     * Persists an error status with message.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void markAsErrored(@Nonnull ExtensionScan scan, @Nullable String errorMessage) {
        scan.setStatus(ScanStatus.ERRORED);
        scan.setErrorMessage(errorMessage);
        scan.setCompletedAt(TimeUtil.getCurrentUTC());
        repositories.saveExtensionScan(scan);
    }

    /**
     * Removes a scan.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void removeScan(@Nonnull ExtensionScan scan) {
        repositories.deleteExtensionScan(scan);
    }

    /**
     * Records a validation failure with the given check type.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void recordValidationFailure(
            @Nonnull ExtensionScan scan,
            @Nonnull String checkType,
            @Nonnull String ruleName,
            @Nullable String reason,
            boolean enforced
    ) {
        var failure = ExtensionValidationFailure.create(checkType, ruleName, reason);
        failure.setEnforced(enforced);
        scan.addValidationFailure(failure);
        repositories.saveValidationFailure(failure);
    }

    /**
     * Records a check execution result for audit trail.
     * 
     * This records ALL check executions - both pass and fail - so admins
     * can see exactly what checks were run on an extension.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void recordCheckResult(
            @Nonnull ExtensionScan scan,
            @Nonnull String checkType,
            @Nonnull ScanCheckResult.CheckCategory category,
            @Nonnull ScanCheckResult.CheckResult result,
            @Nonnull LocalDateTime startedAt,
            @Nonnull LocalDateTime completedAt,
            @Nullable Integer filesScanned,
            int findingsCount,
            @Nullable String summary,
            @Nullable String errorMessage,
            @Nullable Long scannerJobId,
            @Nullable Boolean required
    ) {
        var checkResult = new ScanCheckResult();
        checkResult.setScan(scan);
        checkResult.setCheckType(checkType);
        checkResult.setCategory(category);
        checkResult.setResult(result);
        checkResult.setStartedAt(startedAt);
        checkResult.setCompletedAt(completedAt);
        checkResult.setDurationMs(java.time.Duration.between(startedAt, completedAt).toMillis());
        checkResult.setFilesScanned(filesScanned);
        checkResult.setFindingsCount(findingsCount);
        checkResult.setSummary(summary);
        checkResult.setErrorMessage(errorMessage);
        checkResult.setScannerJobId(scannerJobId);
        checkResult.setRequired(required);
        
        repositories.saveScanCheckResult(checkResult);
        
        logger.debug("Recorded check result: {}.{} {} (scan={}) type={}, result={}, duration={}ms, required={}",
            scan.getNamespaceName(), scan.getExtensionName(), scan.getExtensionVersion(),
            scan.getId(), checkType, result, checkResult.getDurationMs(), required);
    }

    /**
     * Records a scanner job execution result for audit trail.
     * 
     * Convenience method that looks up the ExtensionScan by scanId.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void recordScannerJobResult(
            @Nonnull String scanId,
            @Nonnull ScannerJob job,
            @Nonnull ScanCheckResult.CheckResult result,
            @Nonnull LocalDateTime startedAt,
            @Nullable Integer filesScanned,
            int findingsCount,
            @Nullable String summary,
            @Nullable String errorMessage
    ) {
        ExtensionScan scan = repositories.findExtensionScan(Long.parseLong(scanId));
        if (scan == null) {
            logger.warn("Cannot record scanner job result - scan not found: {}", scanId);
            return;
        }
        
        // Look up the scanner to get its "required" configuration
        Scanner scanner = scannerRegistry.getScanner(job.getScannerType());
        Boolean required = scanner == null || scanner.isRequired();  // Default to required if scanner not found
        
        recordCheckResult(
            scan,
            job.getScannerType(),
            ScanCheckResult.CheckCategory.SCANNER_JOB,
            result,
            startedAt,
            TimeUtil.getCurrentUTC(),
            filesScanned,
            findingsCount,
            summary,
            errorMessage,
            job.getId(),
            required
        );
    }

    /**
     * Result of saving threats, with enforcement statistics.
     * Used to determine the final check result status.
     */
    public record ThreatSaveResult(
        int totalThreats,
        int enforcedCount,
        int notEnforcedCount
    ) {
        /** Returns true if any threats will block publication */
        public boolean hasEnforcedThreats() {
            return enforcedCount > 0;
        }
        
        /** Returns true if there are threats but none are enforced */
        public boolean allThreatsNotEnforced() {
            return totalThreats > 0 && enforcedCount == 0;
        }
        
        /** Empty result for clean scans */
        public static ThreatSaveResult clean() {
            return new ThreatSaveResult(0, 0, 0);
        }
    }

    /**
     * Save threats from a Scanner.Result to the database.
     * 
     * Used by both InvokeScannerHandler (sync) and PollScannerHandler (async).
     * 
     * For async scanners with file extraction, file hashes are looked up from
     * the stored fileHashesJson on the ScannerJob.
     * 
     * Returns enforcement statistics so callers can determine the check result:
     * - If no enforced threats → check PASSED (threats are warnings only)
     * - If enforced threats exist → check FOUND (blocks publication)
     */
    @Transactional(TxType.REQUIRES_NEW)
    public ThreatSaveResult saveThreats(@Nonnull ScannerJob job, @Nonnull Scanner.Result result, boolean scannerEnforced) {
        if (result.isClean()) {
            logger.debug("No threats to save for scanner job {}", job.getId());
            return ThreatSaveResult.clean();
        }
        
        LocalDateTime now = TimeUtil.getCurrentUTC();
        long scanJobId = job.getId();
        String scanId = job.getScanId();
        String scannerType = job.getScannerType();
        
        // Load the parent ExtensionScan entity
        ExtensionScan scan = repositories.findExtensionScan(Long.parseLong(scanId));
        if (scan == null) {
            logger.error("ExtensionScan not found for scanId={}, cannot save threats", scanId);
            return ThreatSaveResult.clean();
        }
        
        // Parse stored file hashes for async scanners with file extraction
        Map<String, String> fileHashes = parseFileHashes(job.getFileHashesJson());
        if (!fileHashes.isEmpty()) {
            logger.debug("Loaded {} file hashes from scanner job for hash lookup", fileHashes.size());
        }
        
        int enforcedCount = 0;
        int notEnforcedCount = 0;
        
        for (Scanner.Threat threat : result.getThreats()) {
            ExtensionThreat scanThreat = createThreatEntity(
                    scan, scanJobId, scannerType, threat, fileHashes, scannerEnforced, now
            );
            
            repositories.saveExtensionThreat(scanThreat);
            
            // Track actual enforcement (may differ from scannerEnforced due to allowlist)
            if (scanThreat.isEnforced()) {
                enforcedCount++;
            } else {
                notEnforcedCount++;
            }
            
            logger.debug("Saved threat: {} (severity: {}, file: {}, enforced: {})",
                    threat.getName(), 
                    threat.getSeverity(), 
                    scanThreat.getFileName(),
                    scanThreat.isEnforced());
        }
        
        int totalThreats = result.getThreats().size();
        logger.debug("Saved {} threats ({} enforced, {} not enforced) for scanner job {}", 
                totalThreats, enforcedCount, notEnforcedCount, scanJobId);
        
        return new ThreatSaveResult(totalThreats, enforcedCount, notEnforcedCount);
    }

    /**
     * Result of processing a completed scan.
     */
    public record CompletedScanResult(
        ScanCheckResult.CheckResult checkResult,
        int threatCount,
        String summary
    ) {}

    /**
     * Process a completed scanner result: save threats, determine check result, record audit.
     * 
     * This consolidates the result processing logic used by both InvokeScannerHandler (sync)
     * and PollScannerHandler (async) to avoid duplication.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public CompletedScanResult processCompletedScan(
            @Nonnull ScannerJob job,
            @Nonnull Scanner.Result result,
            boolean scannerEnforced,
            @Nonnull LocalDateTime startedAt
    ) {
        int threatCount = 0;
        String summary;
        ScanCheckResult.CheckResult checkResult;
        
        if (result.isClean()) {
            checkResult = ScanCheckResult.CheckResult.PASSED;
            summary = "No threats found";
        } else {
            threatCount = result.getThreats().size();
            
            // Save threats and get enforcement statistics
            var saveResult = saveThreats(job, result, scannerEnforced);
            
            // Determine check result based on actual enforcement (considers allowlist)
            if (saveResult.hasEnforcedThreats()) {
                checkResult = ScanCheckResult.CheckResult.QUARANTINE;
                summary = String.format("Found %d threat(s) - %d enforced", 
                    threatCount, saveResult.enforcedCount());
            } else {
                // Threats found but none enforced (scanner not enforced OR all on allowlist)
                checkResult = ScanCheckResult.CheckResult.PASSED;
                summary = String.format("Found %d threat(s) - not enforced", threatCount);
            }
        }

        // If the scanner provided its own human-readable summary, prefer it
        // over our auto-generated text.
        if (result.getSummary() != null && !result.getSummary().isBlank()) {
            summary = result.getSummary();
        }
        
        // Record scanner job result for audit trail
        recordScannerJobResult(
            job.getScanId(),
            job,
            checkResult,
            startedAt,
            null,  // filesScanned - could be added if scanner tracks this
            threatCount,
            summary,
            null   // errorMessage - none for successful completion
        );
        
        return new CompletedScanResult(checkResult, threatCount, summary);
    }

    /**
     * Records a threat detected by a security scanner during long-running scans.
     * Lower-level method for direct threat creation.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void recordThreat(
            @Nonnull ExtensionScan scan,
            @Nonnull String fileName,
            @Nullable String fileHash,
            @Nullable String fileExtension,
            @Nonnull String scannerType,
            @Nonnull String ruleName,
            @Nullable String reason,
            @Nonnull String severity,
            boolean enforced
    ) {
        var threat = ExtensionThreat.create(
                fileName,
                fileHash,
                fileExtension,
                scannerType,
                ruleName,
                reason,
                severity
        );
        threat.setEnforced(enforced);
        scan.addThreat(threat);
        repositories.saveExtensionThreat(threat);
    }

    /**
     * Serialize file hashes to JSON for storage in ScannerJob.
     */
    @Nullable
    public String serializeFileHashes(@Nullable Map<String, String> fileHashes) {
        if (fileHashes == null || fileHashes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fileHashes);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize file hashes: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create an ExtensionThreat entity from a Scanner.Threat.
     * 
     * If the file hash is on the allowlist, the threat is recorded but NOT enforced.
     * This allows admins to see the threat was detected while allowing publication.
     */
    private ExtensionThreat createThreatEntity(
            ExtensionScan scan,
            long scanJobId,
            String scannerType,
            Scanner.Threat threat,
            Map<String, String> fileHashes,
            boolean enforced,
            LocalDateTime detectedAt
    ) {
        ExtensionThreat scanThreat = new ExtensionThreat();
        
        scanThreat.setScan(scan);
        scanThreat.setJobId(scanJobId);
        
        scanThreat.setType(scannerType);
        
        String fileName = threat.getFilePath();
        scanThreat.setFileName(fileName);
        
        // Determine file hash:
        // 1. First check if threat has hash directly (sync scanners)
        // 2. Then look up from stored fileHashes map (async scanners)
        // 3. Leave null if not available
        String fileHash = threat.getFileHash();
        if (fileHash == null && fileName != null && !fileHashes.isEmpty()) {
            fileHash = fileHashes.get(fileName);
            if (fileHash != null) {
                logger.debug("Looked up hash for file {}: {}", fileName, fileHash);
            }
        }
        scanThreat.setFileHash(fileHash);
        
        if (fileName != null && fileName.contains(".")) {
            int lastDot = fileName.lastIndexOf('.');
            scanThreat.setFileExtension(fileName.substring(lastDot + 1));
        }
        
        scanThreat.setRuleName(threat.getName());
        String reason = threat.getDescription();
        scanThreat.setSeverity(threat.getSeverity());
        scanThreat.setDetectedAt(detectedAt);
        
        // Check if this file hash is on the allowlist.
        // If allowed, set enforced=false so it doesn't block publication.
        // Add a note to the reason so admins know why it wasn't enforced.
        boolean finalEnforced = enforced;
        if (enforced && fileHash != null) {
            FileDecision decision = fileDecisionRepository.findByFileHash(fileHash);
            if (decision != null && decision.isAllowed()) {
                finalEnforced = false;
                String allowlistNote = " [NOT ENFORCED: File is on the allowlist]";
                reason = (reason != null ? reason + allowlistNote : allowlistNote);
                
                logger.debug("Threat for file '{}' not enforced because hash {} is on allowlist (scanner: {})",
                        fileName, fileHash, scannerType);
            }
        }
        
        scanThreat.setReason(reason);
        scanThreat.setEnforced(finalEnforced);
        
        return scanThreat;
    }

    /**
     * Parse the stored file hashes JSON into a Map.
     */
    private Map<String, String> parseFileHashes(String fileHashesJson) {
        if (fileHashesJson == null || fileHashesJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(fileHashesJson, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse file hashes JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Delete all scan-related data for a specific extension version.
     * 
     * This should be called when an extension version is deleted to prevent
     * orphaned scan jobs from trying to access deleted files.
     * 
     * Also marks any in-progress scans as ERRORED so they don't remain stuck.
     */
    @Transactional
    public void deleteScansForExtensionVersion(long extensionVersionId) {
        // First, find all scanner jobs and their unique scan IDs
        var jobs = scannerJobRepository.findByExtensionVersionId(extensionVersionId);
        var scanIds = jobs.stream()
                .map(ScannerJob::getScanId)
                .distinct()
                .toList();
        
        // Mark associated scans as ERRORED if they're not already in a terminal state
        for (String scanId : scanIds) {
            try {
                long scanIdLong = Long.parseLong(scanId);
                var scan = repositories.findExtensionScan(scanIdLong);
                if (scan != null && !scan.getStatus().isCompleted()) {
                    scan.setStatus(ScanStatus.ERRORED);
                    scan.setErrorMessage("Extension was deleted while scan was in progress");
                    scan.setCompletedAt(TimeUtil.getCurrentUTC());
                    repositories.saveExtensionScan(scan);
                    logger.debug("Marked scan {} as ERRORED due to extension deletion", scanId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid scan ID format: {}", scanId);
            }
        }
        
        // Delete scanner jobs for this extension version
        scannerJobRepository.deleteByExtensionVersionId(extensionVersionId);
        logger.debug("Deleted {} scanner jobs for extension version {}", jobs.size(), extensionVersionId);
    }
}
