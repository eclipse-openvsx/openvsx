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

import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.TimeUtil;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic dispatcher for concurrency-limited scanners.
 * <p>
 * Runs every 15 seconds as a JobRunr recurring job. JobRunr guarantees only one
 * instance runs across all pods at any time, making this a single-writer that
 * eliminates race conditions in the concurrency gate.
 * <p>
 * For each scanner type with maxConcurrency > 0:
 * 1. Count how many jobs are currently PROCESSING or SUBMITTED (both count as active)
 * 2. Calculate available slots (maxConcurrency - active)
 * 3. Find the oldest QUEUED jobs up to available slots (FIFO)
 * 4. Atomically claim each (QUEUED -> PROCESSING)
 * 5. Enqueue a ScannerInvocationRequest so a worker picks it up
 */
@Service
public class ScannerConcurrencyDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ScannerConcurrencyDispatcher.class);

    private final ScannerJobRepository scanJobRepository;
    private final ScannerRegistry scannerRegistry;
    private final JobRequestScheduler jobScheduler;

    public ScannerConcurrencyDispatcher(
            ScannerJobRepository scanJobRepository,
            ScannerRegistry scannerRegistry,
            JobRequestScheduler jobScheduler
    ) {
        this.scanJobRepository = scanJobRepository;
        this.scannerRegistry = scannerRegistry;
        this.jobScheduler = jobScheduler;
    }

    /**
     * Promote QUEUED scan jobs to PROCESSING for concurrency-limited scanners.
     * Only one instance of this recurring job runs across all pods at a time.
     */
    @Job(name = "Scanner concurrency dispatcher", retries = 0)
    @Recurring(id = "scanner-concurrency-dispatcher", interval = "PT15S")
    public void dispatch() {
        boolean anyLimited = scannerRegistry.getAllScanners().stream()
            .anyMatch(s -> s.getMaxConcurrency() > 0);
        if (!anyLimited) {
            return;
        }
        
        for (Scanner scanner : scannerRegistry.getAllScanners()) {
            int maxConcurrency = scanner.getMaxConcurrency();
            if (maxConcurrency <= 0) {
                continue;
            }

            String scannerType = scanner.getScannerType();
            // Single atomic count of both PROCESSING and SUBMITTED as active.
            // Async scanners transition to SUBMITTED while still running at the
            // external service, so both count toward the concurrency limit.
            long active = scanJobRepository.countByStatusInAndScannerType(
                List.of(ScannerJob.JobStatus.PROCESSING, ScannerJob.JobStatus.SUBMITTED),
                scannerType);
            int available = (int) (maxConcurrency - active);
            if (available <= 0) {
                continue;
            }

            // Find oldest QUEUED jobs, limited to available concurrency slots
            var queued = scanJobRepository.findByScannerTypeAndStatusOrderByCreatedAtAsc(
                scannerType, ScannerJob.JobStatus.QUEUED, Pageable.ofSize(available));

            int dispatched = 0;
            for (ScannerJob job : queued) {
                int claimed = scanJobRepository.claimForProcessing(job.getId(), TimeUtil.getCurrentUTC());
                if (claimed > 0) {
                    dispatched++;
                    jobScheduler.enqueue(new ScannerInvocationRequest(
                        scannerType, job.getExtensionVersionId(), job.getScanId()));
                    logger.debug("Dispatched queued scan job {} for scanner {} ({}/{} slots used)",
                        job.getId(), scannerType, active + dispatched, maxConcurrency);
                }
            }
        }
    }
}
