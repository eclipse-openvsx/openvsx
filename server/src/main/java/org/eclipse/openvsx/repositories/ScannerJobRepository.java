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
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.entities.ScannerJob;

/**
 * Repository for ScanJob entities.
 *
 * Provides database access for scan job operations.
 * Used by the scanning service and polling service.
 */
public interface ScannerJobRepository extends Repository<ScannerJob, Long> {

    /**
     * Save a scan job.
     */
    ScannerJob save(ScannerJob scanJob);

    /**
     * Find a scan job by ID.
     */
    Optional<ScannerJob> findById(Long id);

    /**
     * Find all scan jobs for a specific scan ID.
     *
     * Used to check if all jobs for an extension scan are complete.
     *
     * @param scanId The scan ID that groups related jobs
     * @return List of all jobs with the given scan ID
     */
    List<ScannerJob> findByScanId(String scanId);

    /**
     * Find scan jobs by status.
     *
     * Used by recovery monitor to find async jobs.
     */
    List<ScannerJob> findByStatusIn(List<ScannerJob.JobStatus> statuses);

    /**
     * Find all scan jobs for a specific extension version.
     *
     * @param extensionVersionId The extension version ID
     * @return List of all jobs for the extension version
     */
    List<ScannerJob> findByExtensionVersionId(Long extensionVersionId);

    /**
     * Find a specific scan job by scan ID and scanner type.
     *
     * This is used to avoid creating duplicate jobs during JobRunr retries.
     * When a JobRunr job retries, we want to update the same ScanJob record
     * rather than creating a new one.
     */
    Optional<ScannerJob> findByScanIdAndScannerType(String scanId, String scannerType);

    /**
     * Count jobs matching any of the given statuses for a scanner type.
     */
    long countByStatusInAndScannerType(java.util.List<ScannerJob.JobStatus> statuses, String scannerType);

    /**
     * Atomically transition a job from QUEUED to PROCESSING.
     * Returns 1 if claimed, 0 if the job was already claimed by another worker.
     * The WHERE status='QUEUED' guard prevents double-processing across pods.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE ScannerJob j SET j.status = 'PROCESSING', j.recoveryInProgress = false, "
                + "j.updatedAt = :now WHERE j.id = :id AND j.status = 'QUEUED'"
    )
    int claimForProcessing(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * Find QUEUED jobs for a scanner type, ordered oldest first.
     * Used by the concurrency dispatcher to pick jobs in FIFO order.
     * Pass Pageable to limit results to the number of available slots.
     */
    List<ScannerJob> findByScannerTypeAndStatusOrderByCreatedAtAsc(
            String scannerType,
            ScannerJob.JobStatus status,
            Pageable pageable
    );

    /**
     * Find scan jobs by status that were created before a certain time.
     *
     * Used by the recovery service to find jobs stuck in QUEUED status.
     */
    List<ScannerJob> findByStatusAndCreatedAtBefore(ScannerJob.JobStatus status, LocalDateTime threshold);

    /**
     * Find scan jobs by status that were created before a certain time
     * and are not currently being recovered.
     *
     * Used by recovery monitor to find stuck QUEUED jobs.
     * The recoveryInProgress flag prevents duplicate recovery attempts
     * when multiple pods are running.
     */
    List<ScannerJob> findByStatusAndCreatedAtBeforeAndRecoveryInProgressFalse(
            ScannerJob.JobStatus status,
            LocalDateTime threshold
    );

    /**
     * Delete all scan jobs for a specific extension version.
     *
     * Used when deleting an extension to clean up orphaned scan jobs.
     */
    void deleteByExtensionVersionId(Long extensionVersionId);

}
