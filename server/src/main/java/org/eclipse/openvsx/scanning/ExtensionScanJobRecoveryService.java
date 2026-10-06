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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * Handles scan job recovery: startup recovery and runtime watchdog.
 */
@Service
public class ExtensionScanJobRecoveryService implements JobRequestHandler<HandlerJobRequest<?>> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionScanJobRecoveryService.class);

    // Max jobs to process per watchdog cycle
    private static final int MAX_PER_CYCLE = 20;

    // Arbitrary, fixed key for the Postgres advisory lock guarding startup recovery below. Only
    // needs to be distinct from any other advisory lock key this application uses - there are none
    // today.
    private static final long RECOVERY_LOCK_KEY = 891_234_567_890_123L;

    private final ScannerJobRepository scanJobRepository;
    private final ScannerRegistry scannerRegistry;
    private final RepositoryService repositories;
    private final ExtensionScanPersistenceService persistenceService;
    private final ExtensionScanService scanService;
    private final ExtensionScanCompletionService completionService;
    private final PublishCheckRunner publishCheckRunner;
    private final PublishExtensionVersionService publishService;
    private final ExtensionService extensionService;
    private final JobRequestScheduler jobScheduler;
    private final DSLContext dsl;

    public ExtensionScanJobRecoveryService(
            ScannerJobRepository scanJobRepository,
            ScannerRegistry scannerRegistry,
            RepositoryService repositories,
            ExtensionScanPersistenceService persistenceService,
            ExtensionScanService scanService,
            ExtensionScanCompletionService completionService,
            PublishCheckRunner publishCheckRunner,
            PublishExtensionVersionService publishService,
            ExtensionService extensionService,
            JobRequestScheduler jobScheduler,
            DSLContext dsl
    ) {
        this.scanJobRepository = scanJobRepository;
        this.scannerRegistry = scannerRegistry;
        this.repositories = repositories;
        this.persistenceService = persistenceService;
        this.scanService = scanService;
        this.completionService = completionService;
        this.publishCheckRunner = publishCheckRunner;
        this.publishService = publishService;
        this.extensionService = extensionService;
        this.jobScheduler = jobScheduler;
        this.dsl = dsl;
    }

    /**
     * Recover all scan state on server startup.
     * <p>
     * {@code ApplicationReadyEvent} fires independently, un-coordinated, in every instance's own JVM -
     * with multiple pods (e.g. during a rolling update) several of them can be starting up within the
     * same window, each running this whole pass against the same database rows. Beyond the merely
     * confusing duplicate log lines that produces, some of what recovery does is not idempotent across
     * instances (e.g. re-enqueueing the same stuck job from two pods submits it for scanning twice), so
     * a transaction-scoped Postgres advisory lock ensures only one instance actually performs recovery
     * per acquisition window. {@code pg_try_advisory_xact_lock} never blocks - it returns immediately -
     * and Postgres releases the lock automatically when this method's transaction ends (commit or
     * rollback), so there is no explicit unlock and no risk of a lock leaking onto a pooled connection.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        if (!scanService.isEnabled()) {
            return;
        }

        if (!tryAcquireRecoveryLock()) {
            logger.debug("Another instance already holds the scan recovery lock, skipping");
            return;
        }

        logger.info("Starting scan recovery on server startup");

        recoverPendingJobs();
        recoverValidatingScans();
        recoverStaleScans();
    }

    // Package-private so a test can stub it via a spy without needing two real database connections
    // to exercise recoverOnStartup()'s branching; the lock's own semantics are covered separately by
    // ExtensionScanJobRecoveryServiceTest against a real Postgres instance.
    boolean tryAcquireRecoveryLock() {
        return Boolean.TRUE.equals(
                dsl.fetchValue(
                        DSL.select(
                                DSL.function("pg_try_advisory_xact_lock", Boolean.class, DSL.val(RECOVERY_LOCK_KEY)))));
    }

    /**
     * Handle jobs pending when server restarted.
     * - Scanner removed: Mark job as REMOVED
     * - Async job (SUBMITTED/PROCESSING): Schedule immediate poll to resume
     */
    private void recoverPendingJobs() {
        List<ScannerJob> pendingJobs = scanJobRepository.findByStatusIn(
                List.of(
                        ScannerJob.JobStatus.QUEUED,
                        ScannerJob.JobStatus.PROCESSING,
                        ScannerJob.JobStatus.SUBMITTED));

        if (pendingJobs.isEmpty()) {
            logger.debug("No pending scan jobs found");
            return;
        }

        logger.info("Found {} pending scan jobs, checking for orphaned jobs", pendingJobs.size());

        int orphanedCount = 0;
        int validJobCount = 0;
        int asyncJobsScheduled = 0;
        int syncJobsRequeued = 0;
        int syncJobsReset = 0;
        int syncJobsDeferred = 0;

        for (ScannerJob job : pendingJobs) {
            String scannerType = job.getScannerType();
            Scanner scanner = scannerRegistry.getScanner(scannerType);

            if (scanner == null) {
                // Scanner no longer exists - mark as REMOVED
                logger.warn("Found orphaned job for removed scanner: {} scanId={}", scannerType, job.getScanId());
                job.setStatus(ScannerJob.JobStatus.REMOVED);
                job.setErrorMessage("Scanner '" + scannerType + "' removed from configuration");
                job.setUpdatedAt(TimeUtil.getCurrentUTC());
                scanJobRepository.save(job);
                orphanedCount++;
                continue;
            }

            validJobCount++;

            // --- Async jobs: schedule a recovery poll ---
            if (scanner.isAsync() &&
                    (job.getStatus() == ScannerJob.JobStatus.SUBMITTED ||
                            job.getStatus() == ScannerJob.JobStatus.PROCESSING)) {
                try {
                    Instant pollTime = Instant.now().plusSeconds(10);
                    jobScheduler.schedule(pollTime, new ScannerPollRequest(job.getId()));
                    asyncJobsScheduled++;
                    logger.info("Scheduled recovery poll for job {} (scanner: {})", job.getId(), scannerType);
                } catch (Exception e) {
                    logger.warn("Failed to schedule recovery poll for job {}: {}", job.getId(), e.getMessage());
                }
                continue;
            }

            // --- Sync jobs stuck in PROCESSING: reset to QUEUED ---
            if (!scanner.isAsync() && job.getStatus() == ScannerJob.JobStatus.PROCESSING) {
                job.setStatus(ScannerJob.JobStatus.QUEUED);
                job.setUpdatedAt(TimeUtil.getCurrentUTC());
                job.setRecoveryInProgress(false);
                scanJobRepository.save(job);
                syncJobsReset++;
                logger.info("Reset stuck PROCESSING job {} to QUEUED (scanner: {})", job.getId(), scannerType);
            }

            // Concurrency-limited QUEUED jobs are left for the dispatcher.
            // Jobs beyond the batch limit stay QUEUED and will be picked up by
            // the watchdog on its next cycle
            if (!scanner.isAsync() && job.getStatus() == ScannerJob.JobStatus.QUEUED
                    && scanner.getMaxConcurrency() <= 0) {
                if (syncJobsRequeued >= MAX_PER_CYCLE) {
                    syncJobsDeferred++;
                    continue;
                }
                try {
                    jobScheduler.enqueue(
                            new ScannerInvocationRequest(
                                    scannerType,
                                    job.getExtensionVersionId(),
                                    job.getScanId()));
                    syncJobsRequeued++;
                    logger.info("Re-enqueued QUEUED sync job {} (scanner: {})", job.getId(), scannerType);
                } catch (Exception e) {
                    logger.warn("Failed to re-enqueue sync job {}: {}", job.getId(), e.getMessage());
                }
            }
        }

        if (orphanedCount > 0) {
            logger.warn("Marked {} orphaned jobs as REMOVED, {} valid jobs remain", orphanedCount, validJobCount);
        }
        if (asyncJobsScheduled > 0) {
            logger.info("Scheduled {} recovery polls for async jobs", asyncJobsScheduled);
        }
        if (syncJobsReset > 0) {
            logger.info("Reset {} stuck PROCESSING sync jobs to QUEUED", syncJobsReset);
        }
        if (syncJobsRequeued > 0) {
            logger.info("Re-enqueued {} QUEUED sync jobs", syncJobsRequeued);
        }
        if (syncJobsDeferred > 0) {
            logger.info("Deferred {} QUEUED sync jobs to watchdog (batch limit: {})", syncJobsDeferred, MAX_PER_CYCLE);
        }
    }

    /**
     * Recover scans stuck in VALIDATING status.
     */
    private void recoverValidatingScans() {
        var validatingScans = repositories.findExtensionScansByStatus(ScanStatus.VALIDATING).toList();

        if (validatingScans.isEmpty()) {
            logger.debug("No scans stuck in VALIDATING status");
            return;
        }

        logger.info("Found {} scan(s) stuck in VALIDATING", validatingScans.size());

        int recovered = 0, errored = 0, rejected = 0, skipped = 0;

        for (var scan : validatingScans) {
            try {
                var result = recoverValidatingScan(scan);
                switch (result) {
                    case RECOVERED -> recovered++;
                    case ERRORED -> errored++;
                    case REJECTED -> rejected++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                logger.error(
                        "Failed to recover VALIDATING scan {} ({}.{}.{}): {}",
                        scan.getId(),
                        scan.getNamespaceName(),
                        scan.getExtensionName(),
                        scan.getExtensionVersion(),
                        e.getMessage());
                try {
                    persistenceService.markAsErrored(scan, "Recovery failed: " + e.getMessage());
                    errored++;
                } catch (Exception ex) {
                    logger.error(
                            "Failed to mark scan {} ({}.{}.{}) as ERRORED",
                            scan.getId(),
                            scan.getNamespaceName(),
                            scan.getExtensionName(),
                            scan.getExtensionVersion());
                }
            }
        }

        logger.info(
                "VALIDATING recovery: {} recovered, {} rejected, {} errored, {} skipped",
                recovered,
                rejected,
                errored,
                skipped);
    }

    private enum RecoveryResult {
        RECOVERED, ERRORED, REJECTED, SKIPPED
    }

    private RecoveryResult recoverValidatingScan(ExtensionScan scan) {
        var checkResults = repositories.findScanCheckResultsByScanId(scan.getId());
        var publishCheckResults = checkResults.stream()
                .filter(r -> r.getCategory() == ScanCheckResult.CheckCategory.PUBLISH_CHECK)
                .toList();

        if (publishCheckResults.isEmpty()) {
            logger.warn(
                    "Scan {} ({}.{}.{}) stuck in VALIDATING with no check results",
                    scan.getId(),
                    scan.getNamespaceName(),
                    scan.getExtensionName(),
                    scan.getExtensionVersion());
            persistenceService.markAsErrored(scan, "No publish check results found");
            return RecoveryResult.ERRORED;
        }

        // Verify all expected checks completed
        var expectedCheckTypes = publishCheckRunner.getExpectedCheckTypes();
        var recordedCheckTypes = publishCheckResults.stream()
                .map(ScanCheckResult::getCheckType)
                .toList();
        var missingChecks = expectedCheckTypes.stream()
                .filter(type -> !recordedCheckTypes.contains(type))
                .toList();

        if (!missingChecks.isEmpty()) {
            logger.info(
                    "Scan {} has {} of {} checks complete, skipping (may still be running)",
                    scan.getId(),
                    recordedCheckTypes.size(),
                    expectedCheckTypes.size());
            return RecoveryResult.SKIPPED;
        }

        // Check outcomes
        boolean hasRejection = publishCheckResults.stream()
                .anyMatch(r -> r.getResult() == ScanCheckResult.CheckResult.REJECT);
        if (hasRejection) {
            logger.warn(
                    "Scan {} ({}.{}.{}) with REJECT result, completing as REJECTED",
                    scan.getId(),
                    scan.getNamespaceName(),
                    scan.getExtensionName(),
                    scan.getExtensionVersion());
            scan.setStatus(ScanStatus.REJECTED);
            scan.setCompletedAt(TimeUtil.getCurrentUTC());
            repositories.saveExtensionScan(scan);
            return RecoveryResult.REJECTED;
        }

        boolean hasError = publishCheckResults.stream()
                .anyMatch(r -> r.getResult() == ScanCheckResult.CheckResult.ERROR);
        if (hasError) {
            persistenceService.markAsErrored(scan, "Publish check had error");
            return RecoveryResult.ERRORED;
        }

        // All checks passed - resume scan
        logger.info(
                "Scan {} ({}.{}.{}) has all {} checks passed, resuming",
                scan.getId(),
                scan.getNamespaceName(),
                scan.getExtensionName(),
                scan.getExtensionVersion(),
                publishCheckResults.size());

        var extVersion = findExtensionVersion(scan);
        if (extVersion == null) {
            persistenceService.markAsErrored(scan, "Extension version no longer exists");
            return RecoveryResult.ERRORED;
        }

        try {
            boolean submitted = scanService.submitScannerJobs(scan, extVersion);
            if (!submitted) {
                // No scanners configured — safe to activate
                logger.info(
                        "Scan {} ({}.{}.{}) has no scanners, activating extension",
                        scan.getId(),
                        scan.getNamespaceName(),
                        scan.getExtensionName(),
                        scan.getExtensionVersion());
                publishService.activateExtension(extVersion, extensionService);
                scanService.markScanPassed(scan);
            }
            return RecoveryResult.RECOVERED;
        } catch (Exception e) {
            // Enqueue attempts failed — do NOT activate. Mark as errored.
            logger.error(
                    "Scan {} ({}.{}.{}) failed to submit scanner jobs during recovery",
                    scan.getId(),
                    scan.getNamespaceName(),
                    scan.getExtensionName(),
                    scan.getExtensionVersion(),
                    e);
            persistenceService.markAsErrored(scan, "Recovery failed to submit scanner jobs: " + e.getMessage());
            return RecoveryResult.ERRORED;
        }
    }

    private ExtensionVersion findExtensionVersion(ExtensionScan scan) {
        try {
            var extension = repositories.findExtension(scan.getExtensionName(), scan.getNamespaceName());
            if (extension == null) {
                return null;
            }
            return repositories.findVersion(scan.getExtensionVersion(), scan.getTargetPlatform(), extension);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Handle scans stuck in incomplete status with no active jobs.
     * This can happen if:
     * - Server restarted while scans were in progress
     * - Completion check failed to transition scan after all jobs finished
     * - Jobs failed to enqueue properly
     */
    private void recoverStaleScans() {
        int erroredCount = 0;
        int recoverableCount = 0;
        int completedCount = 0;

        for (ScanStatus status : ScanStatus.values()) {
            if (status.isCompleted() || status == ScanStatus.VALIDATING) {
                continue;
            }

            var staleScans = repositories.findExtensionScansByStatus(status).toList();

            for (var scan : staleScans) {
                String scanIdStr = String.valueOf(scan.getId());
                List<ScannerJob> jobs = scanJobRepository.findByScanId(scanIdStr);
                boolean hasRecoverableJobs = jobs.stream().anyMatch(
                        job -> job.getStatus() == ScannerJob.JobStatus.QUEUED ||
                                job.getStatus() == ScannerJob.JobStatus.PROCESSING ||
                                job.getStatus() == ScannerJob.JobStatus.SUBMITTED);

                if (hasRecoverableJobs) {
                    logger.info(
                            "Scan {} ({}.{}.{}) has recoverable jobs, leaving in {} status",
                            scan.getId(),
                            scan.getNamespaceName(),
                            scan.getExtensionName(),
                            scan.getExtensionVersion(),
                            status);
                    recoverableCount++;
                    continue;
                }

                // Check if all jobs are complete (no failures)
                boolean allJobsComplete = !jobs.isEmpty() && jobs.stream()
                        .allMatch(j -> j.getStatus() == ScannerJob.JobStatus.COMPLETE);

                if (allJobsComplete) {
                    // All jobs complete - retry completion check
                    logger.info(
                            "Retrying completion for scan {} ({}.{}.{}) - all {} jobs complete",
                            scan.getId(),
                            scan.getNamespaceName(),
                            scan.getExtensionName(),
                            scan.getExtensionVersion(),
                            jobs.size());
                    try {
                        completionService.checkSingleScanCompletion(scanIdStr);
                        completedCount++;
                    } catch (Exception e) {
                        logger.error(
                                "Failed to complete scan {} ({}.{}.{}): {}",
                                scan.getId(),
                                scan.getNamespaceName(),
                                scan.getExtensionName(),
                                scan.getExtensionVersion(),
                                e.getMessage());
                        persistenceService.markAsErrored(scan, "Completion retry failed: " + e.getMessage());
                        erroredCount++;
                    }
                    continue;
                }

                // Determine reason based on job state
                String reason;
                if (jobs.isEmpty()) {
                    reason = String.format("Scan stuck in %s with no scanner jobs created", status);
                } else {
                    long completedJobs = jobs.stream()
                            .filter(j -> j.getStatus() == ScannerJob.JobStatus.COMPLETE)
                            .count();
                    long failedJobs = jobs.stream()
                            .filter(j -> j.getStatus() == ScannerJob.JobStatus.FAILED)
                            .count();
                    reason = String.format(
                            "Scan stuck in %s with %d complete, %d failed jobs",
                            status,
                            completedJobs,
                            failedJobs);
                }

                try {
                    logger.warn(
                            "Marking stale scan {} ({}.{}.{}) as ERRORED: {}",
                            scan.getId(),
                            scan.getNamespaceName(),
                            scan.getExtensionName(),
                            scan.getExtensionVersion(),
                            reason);
                    persistenceService.markAsErrored(scan, reason);
                    erroredCount++;
                } catch (Exception e) {
                    logger.error(
                            "Failed to recover stale scan {} ({}.{}.{})",
                            scan.getId(),
                            scan.getNamespaceName(),
                            scan.getExtensionName(),
                            scan.getExtensionVersion());
                }
            }
        }

        if (recoverableCount > 0) {
            logger.info("Left {} scan(s) for recovery", recoverableCount);
        }
        if (completedCount > 0) {
            logger.info("Completed {} scan(s) via recovery", completedCount);
        }
        if (erroredCount > 0) {
            logger.info("Marked {} scan(s) as ERRORED", erroredCount);
        }
    }

    /**
     * Monitor stuck jobs and timeouts.
     */
    @Job(name = "Scan job watchdog", retries = 0)
    @Transactional
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        recoverStuckQueuedJobs();
        checkTimeouts();
    }

    /**
     * Recover jobs stuck in QUEUED status.
     * Re-enqueue if stuck 5-60 min, fail if stuck > 60 min.
     */
    private void recoverStuckQueuedJobs() {
        LocalDateTime fiveMinutesAgo = TimeUtil.getCurrentUTC().minusMinutes(5);
        LocalDateTime oneHourAgo = TimeUtil.getCurrentUTC().minusMinutes(60);

        List<ScannerJob> stuckJobs = scanJobRepository.findByStatusAndCreatedAtBeforeAndRecoveryInProgressFalse(
                ScannerJob.JobStatus.QUEUED,
                fiveMinutesAgo);

        if (stuckJobs.isEmpty()) {
            return;
        }

        logger.warn("Found {} jobs stuck in QUEUED", stuckJobs.size());

        int processed = 0;
        for (ScannerJob job : stuckJobs) {
            if (processed >= MAX_PER_CYCLE) {
                break;
            }

            // Skip concurrency-limited scanners — their QUEUED state is intentional
            // and managed by the ScannerConcurrencyDispatcher, not recovery.
            Scanner scanner = scannerRegistry.getScanner(job.getScannerType());
            if (scanner != null && scanner.getMaxConcurrency() > 0) {
                continue;
            }

            processed++;

            if (job.getCreatedAt().isBefore(oneHourAgo)) {
                markFailed(job, "Stuck in QUEUED for over 1 hour");
                completionService.checkCompletionSafely(job.getScanId());
            } else {
                try {
                    job.setRecoveryInProgress(true);
                    job.setUpdatedAt(TimeUtil.getCurrentUTC());
                    scanJobRepository.save(job);

                    jobScheduler.enqueue(
                            new ScannerInvocationRequest(
                                    job.getScannerType(),
                                    job.getExtensionVersionId(),
                                    job.getScanId()));
                    logger.info("Re-enqueued stuck job: scanId={}, scanner={}", job.getScanId(), job.getScannerType());
                } catch (Exception e) {
                    logger.error("Failed to re-enqueue job {}", job.getScanId());
                    // markFailed() clears recoveryInProgress itself, on the entity it re-fetches
                    // after its claim - setting it here would land on a now-discarded instance.
                    markFailed(job, "Recovery failed: " + e.getMessage());
                    completionService.checkCompletionSafely(job.getScanId());
                }
            }
        }
    }

    /**
     * Check for jobs exceeding scanner timeout.
     */
    private void checkTimeouts() {
        LocalDateTime now = TimeUtil.getCurrentUTC();

        List<ScannerJob> asyncJobs = scanJobRepository.findByStatusIn(
                List.of(ScannerJob.JobStatus.SUBMITTED, ScannerJob.JobStatus.PROCESSING));

        int timedOut = 0;
        for (ScannerJob job : asyncJobs) {
            Scanner scanner = scannerRegistry.getScanner(job.getScannerType());
            if (scanner == null) {
                continue;
            }

            int timeoutMinutes = scanner.getTimeoutMinutes();
            LocalDateTime baseline = job.getStatus() == ScannerJob.JobStatus.PROCESSING
                    ? job.getUpdatedAt()
                    : job.getCreatedAt();
            long activeMinutes = Duration.between(baseline, now).toMinutes();

            if (activeMinutes >= timeoutMinutes) {
                markFailed(
                        job,
                        String.format(
                                "Timeout: exceeded %d min limit (active: %d min)",
                                timeoutMinutes,
                                activeMinutes));
                completionService.checkCompletionSafely(job.getScanId());
                timedOut++;
                logger.error(
                        "Job {} timed out: scanner={}, active={}min",
                        job.getId(),
                        job.getScannerType(),
                        activeMinutes);
            }
        }

        // Fail concurrency-limited QUEUED jobs that have been waiting too long.
        // These are managed by the dispatcher, not the regular stuck-job recovery,
        // so they need their own safety-net timeout.
        // Only fetch jobs old enough to possibly be expired
        int minQueueWait = scannerRegistry.getAllScanners().stream()
                .filter(s -> s.getMaxConcurrency() > 0)
                .mapToInt(Scanner::getMaxQueueWaitMinutes)
                .min()
                .orElse(Integer.MAX_VALUE);
        if (minQueueWait < Integer.MAX_VALUE) {
            LocalDateTime queueCutoff = now.minusMinutes(minQueueWait);
            List<ScannerJob> queuedJobs = scanJobRepository.findByStatusAndCreatedAtBefore(
                    ScannerJob.JobStatus.QUEUED,
                    queueCutoff);
            for (ScannerJob job : queuedJobs) {
                Scanner scanner = scannerRegistry.getScanner(job.getScannerType());
                if (scanner == null || scanner.getMaxConcurrency() <= 0) {
                    continue;
                }

                int maxWait = scanner.getMaxQueueWaitMinutes();
                long waitMinutes = Duration.between(job.getCreatedAt(), now).toMinutes();
                if (waitMinutes >= maxWait) {
                    markFailed(
                            job,
                            String.format(
                                    "Queued too long: waited %d min (limit: %d min)",
                                    waitMinutes,
                                    maxWait));
                    completionService.checkCompletionSafely(job.getScanId());
                    timedOut++;
                    logger.warn(
                            "QUEUED job {} timed out: scanner={}, waited={}min",
                            job.getId(),
                            job.getScannerType(),
                            waitMinutes);
                }
            }
        }

        if (timedOut > 0) {
            logger.info("Timed out {} jobs", timedOut);
        }
    }

    private void markFailed(ScannerJob job, String errorMessage) {
        // Atomic guard against racing ScannerInvocationHandler.saveResults(): a scanner call
        // this watchdog judged to have timed out can complete right after this check would
        // have passed, so the real decision is this WHERE-status claim, not an isTerminal() read.
        if (scanJobRepository
                .claimTerminalStatus(job.getId(), ScannerJob.JobStatus.FAILED, TimeUtil.getCurrentUTC()) == 0) {
            return;
        }
        // Re-fetch: `job` was loaded by the caller before this claim and is now stale - the
        // claim was a bulk UPDATE the persistence context doesn't know about, and save()ing
        // the pre-claim instance would merge its stale fields back over the row it just wrote.
        // recoveryInProgress is cleared here rather than by callers, since it would otherwise
        // be set on that same stale, now-discarded instance.
        ScannerJob failedJob = scanJobRepository.findById(job.getId())
                .orElseThrow(() -> new IllegalStateException("Job not found: " + job.getId()));
        failedJob.setErrorMessage(errorMessage);
        failedJob.setPollLeaseUntil(null);
        failedJob.setRecoveryInProgress(false);
        scanJobRepository.save(failedJob);
    }
}
