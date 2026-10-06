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
import java.util.Map;

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
 * JobRunr handler that invokes a single scanner.
 * Runs in background worker threads with automatic retry.
 */
@Component
public class ScannerInvocationHandler implements JobRequestHandler<ScannerInvocationRequest> {

    protected final Logger logger = LoggerFactory.getLogger(ScannerInvocationHandler.class);

    private final ScannerJobRepository scanJobRepository;
    private final ScannerRegistry scannerRegistry;
    private final ExtensionScanPersistenceService persistenceService;
    private final JobRequestScheduler jobScheduler;
    private final ExtensionScanCompletionService completionService;

    public ScannerInvocationHandler(
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
     * Invoke a single scanner and persist the result.
     * <p>
     * This method creates the ScanJob record BEFORE invoking the scanner
     * to avoid creating duplicate records when JobRunr retries.
     * <p>
     * IMPORTANT: This method is NOT transactional as a whole because scanner.startScan()
     * makes HTTP calls to external services that can take minutes. Holding a database
     * connection during HTTP calls causes connection pool exhaustion.
     * <p>
     * Flow:
     * 1. Find or create ScanJob record (unique by scanId + scannerType) - status: QUEUED
     * 2. Get scanner from registry
     * 3. Concurrency gate:
     *    - If scanner has maxConcurrency > 0 and job is QUEUED: return early
     *      (the periodic ScannerConcurrencyDispatcher will claim and re-enqueue it)
     *    - If scanner has no limit and job is QUEUED: claim immediately (QUEUED -> PROCESSING)
     *    - If job is already PROCESSING: proceed (dispatcher already claimed it)
     * 4. Invoke scanner.startScan() (OUTSIDE transaction - may take minutes)
     * 5. Update job based on result (in transaction):
     *    - Sync scanner: COMPLETE (with results)
     *    - Async scanner: SUBMITTED (with external job ID for polling)
     * <p>
     * On failure (if step 4 fails):
     * - Job is marked FAILED immediately (frees concurrency slot)
     * - Completion check is triggered for the parent scan
     * - No re-throw (retries disabled to avoid mark-FAILED + retry conflict)
     * <p>
     * For sync scanners:
     * - startScan() returns immediate results
     * - Job marked COMPLETE
     * - Threats saved via ExtensionScanPersistenceService
     * <p>
     * For async scanners:
     * - startScan() returns external job ID
     * - Job marked SUBMITTED
     * - Polling service will check status periodically
     * <p>
     * JobRunr automatically:
     * - Runs this in a worker thread
     * - Tracks job status in database
     */
    @Override
    @Job(
        name = "Invoke scanner for extension scan",
        retries = 2 // Retry 2 times on failure
    )
    public void run(ScannerInvocationRequest jobRequest) throws Exception {
        String scannerType = jobRequest.getScannerType();
        long extensionVersionId = jobRequest.getExtensionVersionId();
        String scanId = jobRequest.getScanId();

        logger.debug(
                "Invoking scanner: {} for extension version {}",
                scannerType,
                extensionVersionId);

        // Phase 1: Prepare job (find-or-create, get scanner)
        // Returns null if job already terminal or scanner not found
        var prepared = prepareJob(scanId, scannerType, extensionVersionId);
        if (prepared == null) {
            return;
        }

        Scanner scanner = prepared.scanner();
        ScannerJob job = prepared.job();
        Long jobId = job.getId();
        int maxConcurrency = scanner.getMaxConcurrency();

        // Phase 2: Concurrency gate
        // For concurrency-limited scanners, the periodic dispatcher handles promotion.
        // If the job is still QUEUED, it hasn't been dispatched yet — return early.
        if (maxConcurrency > 0 && job.getStatus() == ScannerJob.JobStatus.QUEUED) {
            logger.debug(
                    "Job {} deferred to dispatcher for scanner {} (max-concurrency: {})",
                    jobId,
                    scannerType,
                    maxConcurrency);
            return;
        }

        // For unlimited scanners with QUEUED jobs, claim immediately (existing behavior)
        if (job.getStatus() == ScannerJob.JobStatus.QUEUED) {
            int claimed = scanJobRepository.claimForProcessing(jobId, TimeUtil.getCurrentUTC());
            if (claimed == 0) {
                logger.debug("Job {} already claimed by another worker, skipping", jobId);
                return;
            }
            // Refresh entity so in-memory state matches DB after the bulk UPDATE.
            job = scanJobRepository.findById(jobId).orElseThrow();
        }

        // Phase 3: Invoke scanner (OUTSIDE transaction - may take minutes).
        Scanner.Invocation invocation;
        try {
            var command = new Scanner.Command(extensionVersionId, scanId);
            invocation = scanner.startScan(command);
        } catch (Exception e) {
            // Mark FAILED immediately so the concurrency slot is freed.
            logger.error(
                    "Scanner {} failed for extension version {}: {}",
                    scannerType,
                    extensionVersionId,
                    e.getMessage());
            markJobFailed(jobId, e);
            completionService.checkCompletionSafely(scanId);
            return;
        }

        // Phase 4: Save results (outside the scanner catch block).
        // DB failures here will propagate to JobRunr as an unhandled exception.
        saveResults(jobId, invocation, scanner, scannerType, extensionVersionId);
    }

    /**
     * Prepare the scanner job for invocation.
     * Creates or finds the job, validates it's not terminal, looks up the scanner.
     * Does NOT mark PROCESSING — that's handled by the caller or the concurrency dispatcher.
     */
    private PreparedJob prepareJob(String scanId, String scannerType, long extensionVersionId) {
        // 1. CREATE OR FIND EXISTING SCANJOB FIRST
        // This handles JobRunr retries - we update the same job, not create duplicates
        ScannerJob job = scanJobRepository
                .findByScanIdAndScannerType(scanId, scannerType)
                .orElseGet(() -> {
                    ScannerJob newJob = new ScannerJob();
                    newJob.setScanId(scanId);
                    newJob.setScannerType(scannerType);
                    newJob.setExtensionVersionId(extensionVersionId);
                    newJob.setStatus(ScannerJob.JobStatus.QUEUED); // Queued, waiting to invoke scanner
                    newJob.setCreatedAt(TimeUtil.getCurrentUTC());
                    newJob.setUpdatedAt(TimeUtil.getCurrentUTC());
                    newJob.setPollLeaseUntil(null); // No lease yet
                    newJob.setPollAttempts(0); // No poll attempts yet
                    newJob.setRecoveryInProgress(false);
                    return scanJobRepository.save(newJob);
                });

        // Skip if job is already in terminal state
        // This can happen if this is a duplicate enqueue or recovery attempt
        if (job.getStatus().isTerminal()) {
            logger.debug(
                    "Scan job for scanner {} already in terminal state {}, skipping invocation",
                    scannerType,
                    job.getStatus());
            return null;
        }

        // 2. Get the scanner from registry
        Scanner scanner = scannerRegistry.getScanner(scannerType);

        if (scanner == null) {
            job.setStatus(ScannerJob.JobStatus.FAILED);
            job.setErrorMessage("Scanner not found: " + scannerType);
            job.setUpdatedAt(TimeUtil.getCurrentUTC());
            scanJobRepository.save(job);
            logger.error("Scanner not found: {}", scannerType);
            return null; // Don't retry - scanner will never exist
        }

        return new PreparedJob(scanner, job);
    }

    /**
     * Save the scanner invocation results.
     */
    private void saveResults(
            Long jobId,
            Scanner.Invocation invocation,
            Scanner scanner,
            String scannerType,
            long extensionVersionId
    ) {
        ScannerJob job = scanJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        // Fast-path discard; this read is stale the instant it happens, so it cannot be the
        // actual guard against the watchdog finalizing the job while scanner.startScan() was
        // blocked. For a Completed result, claimCompletion() below closes that gap atomically.
        if (job.getStatus().isTerminal()) {
            logger.debug(
                    "Scan job {} already in terminal state {}, discarding late scanner result",
                    jobId,
                    job.getStatus());
            return;
        }

        // Determine the scan ID before processing (needed for completion check)
        String scanId = job.getScanId();

        switch (invocation) {
            case Scanner.Invocation.Completed c -> {
                // Persist threats/audit data (its own REQUIRES_NEW transaction) BEFORE claiming
                // COMPLETE, not after: claiming first would make the job visible as terminal to
                // a concurrent completion check before its enforcement data exists, and would
                // let a later persistence failure retry into a job already (wrongly) terminal.
                handleCompletedScan(job, c, scanner, scannerType, extensionVersionId);
                // Atomically claim COMPLETE now that the result is already committed. If the
                // watchdog's own claim (markFailed) won the race instead, the threat/audit data
                // just persisted is simply orphaned under a FAILED job - harmless, since
                // completion status is decided from ScannerJob.status alone, not that data.
                if (scanJobRepository
                        .claimTerminalStatus(jobId, ScannerJob.JobStatus.COMPLETE, TimeUtil.getCurrentUTC()) == 0) {
                    logger.debug("Scan job {} was finalized elsewhere, discarding late scanner result", jobId);
                    return;
                }
                // Re-fetch: `job` was loaded before the claim and is now stale - the claim was
                // a bulk UPDATE the persistence context doesn't know about, and save()ing the
                // pre-claim instance would merge its stale fields back over the row it just wrote.
                ScannerJob claimedJob = scanJobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
                claimedJob.setExternalJobId(null); // No external job for sync scanners
                scanJobRepository.save(claimedJob);
                completionService.checkCompletionSafely(scanId);
            }
            case Scanner.Invocation.Submitted s -> {
                // SUBMITTED is not terminal, so there is no verdict here for the watchdog to
                // clobber - it may legitimately fail this job later if polling times out.
                handleSubmittedScan(job, s, scanner, scannerType, extensionVersionId);
                scanJobRepository.save(job);
            }
        }
    }

    /**
     * Mark a job as failed after an exception.
     */
    private void markJobFailed(Long jobId, Exception e) {
        // claimTerminalStatus() is the actual guard against racing the watchdog's own
        // markFailed(); the isTerminal() read it replaces here would be stale by the time
        // this method's save() ran.
        if (scanJobRepository.claimTerminalStatus(jobId, ScannerJob.JobStatus.FAILED, TimeUtil.getCurrentUTC()) == 0) {
            return;
        }
        // Loaded after the claim, so this already reflects it (status, updatedAt) - only the
        // error message still needs setting.
        ScannerJob job = scanJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        job.setErrorMessage("Scanner invocation failed: " + e.getMessage());
        scanJobRepository.save(job);
    }

    /**
     * Data holder for prepared job information.
     * Holds the scanner and the full job entity (still in QUEUED or PROCESSING state).
     */
    public record PreparedJob(Scanner scanner, ScannerJob job) {}

    /**
     * Handle a completed (synchronous) scan result.
     * Marks job as complete, saves any threats found, and records result for audit.
     */
    private void handleCompletedScan(
            ScannerJob job,
            Scanner.Invocation.Completed completed,
            Scanner scanner,
            String scannerType,
            long extensionVersionId
    ) {
        // Capture the processing start time before overwriting updatedAt.
        // For sync scanners this is the claimForProcessing time.
        LocalDateTime startedAt = job.getUpdatedAt();

        // Mark job as complete
        job.setStatus(ScannerJob.JobStatus.COMPLETE);
        job.setExternalJobId(null); // No external job for sync scanners
        job.setUpdatedAt(TimeUtil.getCurrentUTC());

        // Process result: save threats, determine check result, record audit
        var processed = persistenceService.processCompletedScan(
                job,
                completed.result(),
                scanner.enforcesThreats(),
                startedAt);

        // Only log when threats were found
        if (processed.checkResult() == ScanCheckResult.CheckResult.QUARANTINE) {
            logger.warn(
                    "Scanner {} found threats: {} (extension version: {})",
                    scannerType,
                    processed.summary(),
                    extensionVersionId);
        } else if (processed.threatCount() > 0) {
            // Threats found but not enforced (warnings only)
            logger.debug(
                    "Scanner {} found issues: {} (extension version: {})",
                    scannerType,
                    processed.summary(),
                    extensionVersionId);
        }
    }

    /**
     * Handle a submitted (asynchronous) scan.
     * Marks job as submitted, stores file hashes, and schedules first poll.
     */
    private void handleSubmittedScan(
            ScannerJob job,
            Scanner.Invocation.Submitted submitted,
            Scanner scanner,
            String scannerType,
            long extensionVersionId
    ) {
        var submission = submitted.submission();
        String externalJobId = submission.externalJobId();

        // Mark job as submitted to external service (requires polling)
        job.setStatus(ScannerJob.JobStatus.SUBMITTED);
        job.setExternalJobId(externalJobId);
        job.setUpdatedAt(TimeUtil.getCurrentUTC());

        // Store file hashes for async scanners with file extraction
        // This allows looking up hashes when results come back later
        if (submission.hasFileHashes()) {
            Map<String, String> fileHashes = submission.fileHashes();
            String fileHashesJson = persistenceService.serializeFileHashes(fileHashes);
            if (fileHashesJson != null) {
                job.setFileHashesJson(fileHashesJson);
                logger.debug(
                        "Stored {} file hashes for async scan job {}",
                        fileHashes.size(),
                        externalJobId);
            }
        }

        logger.debug(
                "Scanner {} submitted to external service. Job ID: {} (extension version: {})",
                scannerType,
                externalJobId,
                extensionVersionId);

        // Schedule first poll using scanner's poll configuration
        // No need for a separate polling monitor - polls chain themselves
        scheduleFirstPoll(job.getId(), scanner);
    }

    /**
     * Schedule the first poll for an async scan job.
     * Uses JobRunr's schedule() for delayed execution.
     * Subsequent polls are scheduled by PollScannerHandler with backoff.
     */
    private void scheduleFirstPoll(Long jobId, Scanner scanner) {
        try {
            // Get poll config from scanner, or use default if not specified
            var pollConfig = scanner.getPollConfig();
            if (pollConfig == null) {
                pollConfig = RemoteScannerProperties.PollConfig.DEFAULT;
            }
            int delaySeconds = pollConfig.getInitialDelaySeconds();

            Instant pollTime = Instant.now().plusSeconds(delaySeconds);
            jobScheduler.schedule(pollTime, new ScannerPollRequest(jobId));
            logger.debug("Scheduled first poll for job {} at {} (delay: {}s)", jobId, pollTime, delaySeconds);
        } catch (Exception e) {
            // Log but don't fail - recovery monitor will catch orphaned jobs
            logger.warn("Failed to schedule first poll for job {}: {}", jobId, e.getMessage());
        }
    }
}
