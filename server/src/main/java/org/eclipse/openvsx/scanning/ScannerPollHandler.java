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
import java.time.LocalDateTime;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * JobRunr handler that polls async scan jobs.
 * Self-schedules with exponential backoff until complete/failed.
 */
@Component
public class ScannerPollHandler implements JobRequestHandler<ScannerPollRequest> {

    protected final Logger logger = LoggerFactory.getLogger(ScannerPollHandler.class);

    private final ScannerJobRepository scanJobRepository;
    private final ScannerRegistry scannerRegistry;
    private final ExtensionScanPersistenceService persistenceService;
    private final JobRequestScheduler jobScheduler;
    private final ExtensionScanCompletionService completionService;

    public ScannerPollHandler(
            ScannerJobRepository scanJobRepository,
            ScannerRegistry scannerRegistry,
            ExtensionScanPersistenceService persistenceService,
            JobRequestScheduler jobScheduler,
            ExtensionScanCompletionService completionService
    ) {
        this.scanJobRepository = scanJobRepository;
        this.scannerRegistry = scannerRegistry;
        this.persistenceService = persistenceService;
        this.jobScheduler = jobScheduler;
        this.completionService = completionService;
    }

    /**
     * Poll a single scan job.
     * <p>
     * IMPORTANT: This method is NOT transactional as a whole because scanner.pollStatus()
     * and scanner.fetchResults() make HTTP calls to external services. Holding a database
     * connection during HTTP calls causes connection pool exhaustion.
     * <p>
     * This method:
     * 1. Loads the scan job from database (in transaction)
     * 2. Gets the appropriate scanner using the scanner type
     * 3. Polls the scanner for status (OUTSIDE transaction - HTTP call)
     * 4. Updates the database based on the result (in transaction)
     * <p>
     * JobRunr automatically:
     * - Runs this in a worker thread
     * - Retries on failure (up to 3 times)
     * - Tracks job status in database
     *
     * @param jobRequest The request containing the scan job ID to poll
     */
    @Override
    @Job(
        name = "Poll async scan job",
        retries = 3 // Retry 3 times on failure
    )
    public void run(ScannerPollRequest jobRequest) throws Exception {
        long scanJobId = jobRequest.getScanJobId();

        // Phase 1: Load job info (read-only, no transaction needed)
        var pollContext = loadJobForPolling(scanJobId);
        if (pollContext == null) {
            return; // Job not found, terminal, or invalid
        }

        try {
            // Phase 2: Poll external scanner (HTTP call)
            Scanner.PollStatus status = pollContext.scanner().pollStatus(pollContext.submission());

            logger.debug("Scan job {} status: {}", scanJobId, status);

            // Phase 3: Handle based on status
            // Note: processCompletedScan() has REQUIRES_NEW, so threats save in separate tx.
            // Other updates are single entity saves (auto-commit).
            if (status == Scanner.PollStatus.COMPLETED) {
                // Fetch results (another HTTP call)
                Scanner.Result result = pollContext.scanner().fetchResults(pollContext.submission());
                saveCompletedResults(scanJobId, pollContext.scanner(), result);
            } else {
                handlePollResult(scanJobId, pollContext.scanner(), pollContext.submission(), status);
            }

        } catch (Exception e) {
            // Error during polling - clear lease so job can be polled again
            logger.error("Error polling scan job " + scanJobId, e);
            clearJobLease(scanJobId);
            throw e; // Let JobRunr handle retry
        }
    }

    /**
     * Load job information needed for polling.
     * Mostly read-only; edge case writes are single entity saves (auto-commit).
     */
    private PollContext loadJobForPolling(long scanJobId) {
        // Load the scan job from database
        // Job may have been deleted if extension was deleted - silently skip
        var optionalJob = scanJobRepository.findById(scanJobId);
        if (optionalJob.isEmpty()) {
            logger.debug("Scan job {} no longer exists (extension may have been deleted), skipping poll", scanJobId);
            return null;
        }

        ScannerJob job = optionalJob.get();

        // Skip if job is already in terminal state
        // This can happen if another poll completed the job before this one ran
        if (job.getStatus().isTerminal()) {
            logger.debug(
                    "Scan job {} already in terminal state {}, skipping poll",
                    scanJobId,
                    job.getStatus());
            return null;
        }

        logger.debug(
                "Polling scan job {} (scanner: {}, external ID: {})",
                scanJobId,
                job.getScannerType(),
                job.getExternalJobId());

        // Get the scanner for this job type
        Scanner scanner = scannerRegistry.getScanner(job.getScannerType());

        if (scanner == null) {
            logger.error("Scanner not found: {}", job.getScannerType());
            return null;
        }

        if (!scanner.isAsync()) {
            // Sync scanner shouldn't be in pending state
            logger.warn("Sync scanner found in pending jobs: {}", job.getScannerType());
            job.setPollLeaseUntil(null);
            scanJobRepository.save(job);
            return null;
        }

        // Validate external job ID
        String externalJobId = job.getExternalJobId();
        if (externalJobId == null) {
            logger.error("Scan job {} has null external job ID, cannot poll", scanJobId);
            markJobFailed(job, "Missing external job ID");
            return null;
        }

        var submission = new Scanner.Submission(externalJobId);
        return new PollContext(scanner, submission, job.getScanId());
    }

    /**
     * Handle the poll result and update database.
     * Called for non-COMPLETED statuses (PROCESSING, SUBMITTED, FAILED).
     */
    private void handlePollResult(
            long scanJobId,
            Scanner scanner,
            Scanner.Submission submission,
            Scanner.PollStatus status
    ) {
        ScannerJob job = scanJobRepository.findById(scanJobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) {
            return; // Job gone or already completed
        }

        switch (status) {
            case FAILED -> handleFailedStatus(job, scanner, submission);
            case PROCESSING, SUBMITTED -> handleProcessingStatus(job, scanner);
            case COMPLETED -> {
            } // Handled separately via saveCompletedResults
        }
    }

    /**
     * Save results for a completed scan.
     * Note: processCompletedScan() has REQUIRES_NEW so threats save in separate tx.
     */
    private void saveCompletedResults(long scanJobId, Scanner scanner, Scanner.Result result) {
        ScannerJob job = scanJobRepository.findById(scanJobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) {
            return; // Job gone or already completed
        }

        logger.debug("Scan job {} completed, saving results", job.getId());

        // Capture the processing start time before overwriting updatedAt.
        // For async scanners this is the submission time (set in handleSubmittedScan),
        // preserved because handleProcessingStatus no longer bumps updatedAt.
        LocalDateTime startedAt = job.getUpdatedAt();

        // Mark job complete and clear lease
        job.setStatus(ScannerJob.JobStatus.COMPLETE);
        job.setPollLeaseUntil(null);
        job.setUpdatedAt(TimeUtil.getCurrentUTC());
        scanJobRepository.save(job);

        // Process result: save threats, determine check result, record audit
        var processed = persistenceService.processCompletedScan(
                job,
                result,
                scanner.enforcesThreats(),
                startedAt);

        // Log based on result
        if (processed.threatCount() == 0) {
            logger.debug("Scan job {} found no threats", job.getId());
        } else if (processed.checkResult() == ScanCheckResult.CheckResult.QUARANTINE) {
            logger.warn("Scan job {} found threats: {}", job.getId(), processed.summary());
        } else {
            logger.info("Scan job {} found issues: {}", job.getId(), processed.summary());
        }

        // Check completion
        completionService.checkCompletionSafely(job.getScanId());
    }

    /**
     * Clear job lease after an error.
     */
    private void clearJobLease(long scanJobId) {
        scanJobRepository.findById(scanJobId).ifPresent(job -> {
            job.setPollLeaseUntil(null);
            scanJobRepository.save(job);
        });
    }

    /**
     * Data holder for poll context.
     */
    private record PollContext(Scanner scanner, Scanner.Submission submission, String scanId) {}

    /**
     * Handle FAILED status: mark job as failed, record result for audit, and trigger completion check.
     * Attempts to fetch error details from the scanner if possible.
     */
    private void handleFailedStatus(ScannerJob job, Scanner scanner, Scanner.Submission submission) {
        // Try to get the actual error from the scanner's result endpoint
        String errorDetail = fetchErrorDetail(scanner, submission);
        String errorMessage = errorDetail != null
                ? "External scan failed: " + errorDetail
                : "External scan failed (no details from scanner)";

        logger.error("Scan job {} failed at external scanner: {}", job.getId(), errorMessage);
        LocalDateTime startedAt = job.getUpdatedAt();
        markJobFailed(job, errorMessage);

        // Record scanner job result for audit trail
        persistenceService.recordScannerJobResult(
                job.getScanId(),
                job,
                ScanCheckResult.CheckResult.ERROR,
                startedAt,
                null, // filesScanned
                0, // threatCount
                errorMessage,
                errorMessage);

        // Still check completion - might need to mark scan as errored
        completionService.checkCompletionSafely(job.getScanId());
    }

    /**
     * Try to fetch error details from the scanner's result endpoint.
     * Returns null if unable to get details (best-effort).
     */
    private String fetchErrorDetail(Scanner scanner, Scanner.Submission submission) {
        try {
            Scanner.Result result = scanner.fetchResults(submission);
            if (result != null && !result.isClean()) {
                return result.getThreats().stream()
                        .map(Scanner.Threat::getDescription)
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            logger.debug("Could not fetch error details from scanner: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Handle PROCESSING/SUBMITTED status: schedule next poll.
     * <p>
     * Uses scanner's poll config for interval, backoff, and max attempts.
     * Falls back to defaults if scanner has no poll config.
     */
    private void handleProcessingStatus(ScannerJob job, Scanner scanner) {
        // Get poll config from scanner, or use default if not specified
        var pollConfig = scanner.getPollConfig();
        if (pollConfig == null) {
            pollConfig = RemoteScannerProperties.PollConfig.DEFAULT;
        }

        // Increment poll attempts
        job.incrementPollAttempts();
        int attempts = job.getPollAttempts();

        // Check if we've exceeded max attempts
        int maxAttempts = pollConfig.getMaxAttempts();
        if (attempts >= maxAttempts) {
            logger.error(
                    "Scan job {} exceeded max poll attempts ({}), marking as failed",
                    job.getId(),
                    maxAttempts);
            markJobFailed(job, "Exceeded max poll attempts (" + maxAttempts + ")");
            completionService.checkCompletionSafely(job.getScanId());
            return;
        }

        // Update job status — do NOT bump updatedAt here so it preserves the
        // submission time (set in handleSubmittedScan). This is important for
        // accurate duration display and timeout calculation.
        job.setStatus(ScannerJob.JobStatus.PROCESSING);
        job.setPollLeaseUntil(null); // No lease needed with self-scheduling
        scanJobRepository.save(job);

        // Calculate delay based on poll config
        int delaySeconds = calculatePollDelay(attempts, pollConfig);

        // Schedule next poll
        scheduleNextPoll(job.getId(), delaySeconds);

        logger.debug(
                "Scan job {} still processing (attempt {}), next poll in {}s",
                job.getId(),
                attempts,
                delaySeconds);
    }

    /**
     * Calculate poll delay based on scanner's poll configuration.
     * <p>
     * If exponential backoff is enabled:
     *   delay = min(interval * multiplier^(attempt-1), maxInterval)
     * Otherwise:
     *   delay = interval (fixed)
     */
    private int calculatePollDelay(int attempts, RemoteScannerProperties.PollConfig pollConfig) {
        int interval = pollConfig.getIntervalSeconds();
        int maxInterval = pollConfig.getMaxIntervalSeconds();
        boolean useBackoff = pollConfig.isExponentialBackoff();
        double multiplier = pollConfig.getBackoffMultiplier();

        if (!useBackoff) {
            // Fixed interval polling
            return interval;
        }

        // Exponential backoff: interval * multiplier^(attempt-1)
        // maxInterval caps the result, so just calculate and cap
        double delay = interval * Math.pow(multiplier, attempts - 1);
        return (int) Math.min(delay, maxInterval);
    }

    /**
     * Schedule the next poll using JobRunr's built-in delay.
     */
    private void scheduleNextPoll(Long jobId, int delaySeconds) {
        try {
            Instant pollTime = Instant.now().plusSeconds(delaySeconds);
            jobScheduler.schedule(pollTime, new ScannerPollRequest(jobId));
        } catch (Exception e) {
            // Log but don't fail - recovery monitor will catch orphaned jobs
            logger.warn("Failed to schedule next poll for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Mark a job as failed with error message.
     */
    private void markJobFailed(ScannerJob job, String errorMessage) {
        job.setStatus(ScannerJob.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setPollLeaseUntil(null); // Clear lease - no more polling needed
        job.setUpdatedAt(TimeUtil.getCurrentUTC());
        scanJobRepository.save(job);
    }
}
