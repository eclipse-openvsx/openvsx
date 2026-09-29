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
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Represents a scan job in the database.
 * Each scan job tracks the status of a specific scanner's work on an extension version.
 * <p>
 * Multiple ScanJobs can be linked together via scanId to represent
 * all the scanning work for a single extension version upload.
 */
@Entity
@Table(name = "scan_job")
public class ScannerJob {

    /**
     * Tracks the current state of the scan job.
     * <p>
     * Status flow for synchronous scanners:
     * QUEUED → PROCESSING → COMPLETE/FAILED
     * <p>
     * Status flow for asynchronous scanners:
     * QUEUED → PROCESSING → SUBMITTED → COMPLETE/FAILED
     * <p>
     * REMOVED is set by cleanup service when scanner was removed from configuration.
     */
    public enum JobStatus {
        QUEUED, // Job created, waiting to invoke scanner
        PROCESSING, // Scanner.startScan() is being executed
        SUBMITTED, // Successfully submitted to external scanner service (async only)
        COMPLETE, // Job finished successfully
        FAILED, // Job failed with error
        REMOVED; // Scanner was removed from configuration (set by cleanup service)

        /**
         * Check if this status is terminal (job has finished processing).
         * <p>
         * Terminal states: COMPLETE, FAILED, REMOVED
         * Non-terminal states: QUEUED, PROCESSING, SUBMITTED
         *
         * @return true if this is a terminal state
         */
        public boolean isTerminal() {
            return this == COMPLETE || this == FAILED || this == REMOVED;
        }

        /**
         * Check if this status represents an active/pending job.
         * <p>
         * Active states: QUEUED, PROCESSING, SUBMITTED
         *
         */
        public boolean isActive() {
            return !isTerminal();
        }
    }

    @Id
    @GeneratedValue(generator = "scanJobSeq")
    @SequenceGenerator(name = "scanJobSeq", sequenceName = "scan_job_seq")
    private long id;

    // Links multiple scan jobs together for one extension scan
    // All scan jobs for a single extension version upload share the same scanId
    @Column(nullable = false)
    private String scanId;

    // Identifies the scanner type
    // This is used by the polling service to determine which scanner to invoke
    @Column(nullable = false)
    private String scannerType;

    // The extension version being scanned
    @Column(nullable = false)
    private long extensionVersionId;

    // External job ID (for async scanners only)
    // Null for synchronous scanners
    @Column(length = 512)
    private String externalJobId;

    // Current status of the scan job
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    // Lease-based polling lock to prevent duplicate polling jobs
    // When null: job is available for polling
    // When set: job is being polled, lease expires at this timestamp
    // If current time > pollLeaseUntil, lease has expired and job can be polled again
    @Column
    private LocalDateTime pollLeaseUntil;

    // Number of times this job has been polled (for async scanners)
    // Used to detect jobs stuck in external scanner and enforce max poll attempts
    @Column(nullable = false)
    private int pollAttempts = 0;

    // Flag to prevent duplicate recovery attempts across multiple servers
    // Set to true when stuck job recovery re-enqueues a scanner invocation
    // Reset to false when the handler picks up the job or after timeout
    @Column(nullable = false)
    private boolean recoveryInProgress = false;

    // Timestamp when job was created
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Timestamp when job was last updated
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Optional error message if job fails
    @Column(length = 2048)
    private String errorMessage;

    /**
     * JSON map of file names to SHA256 hashes for async scanners with file extraction.
     * <p>
     * When an async scanner extracts files from the .vsix and sends them for scanning,
     * the temp files are cleaned up after submission. This field stores the filename→hash
     * mapping so we can properly set file hashes when results come back later.
     * <p>
     * Format: {"extension/main.js": "abc123...", "extension/util.js": "def456..."}
     * Null when: no file extraction was used, or sync scanner
     */
    @Column(columnDefinition = "TEXT")
    private String fileHashesJson;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getScannerType() {
        return scannerType;
    }

    public void setScannerType(String scannerType) {
        this.scannerType = scannerType;
    }

    public long getExtensionVersionId() {
        return extensionVersionId;
    }

    public void setExtensionVersionId(long extensionVersionId) {
        this.extensionVersionId = extensionVersionId;
    }

    public String getExternalJobId() {
        return externalJobId;
    }

    public void setExternalJobId(String externalJobId) {
        this.externalJobId = externalJobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public LocalDateTime getPollLeaseUntil() {
        return pollLeaseUntil;
    }

    public void setPollLeaseUntil(LocalDateTime pollLeaseUntil) {
        this.pollLeaseUntil = pollLeaseUntil;
    }

    public int getPollAttempts() {
        return pollAttempts;
    }

    public void setPollAttempts(int pollAttempts) {
        this.pollAttempts = pollAttempts;
    }

    public void incrementPollAttempts() {
        this.pollAttempts++;
    }

    public boolean isRecoveryInProgress() {
        return recoveryInProgress;
    }

    public void setRecoveryInProgress(boolean recoveryInProgress) {
        this.recoveryInProgress = recoveryInProgress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFileHashesJson() {
        return fileHashesJson;
    }

    public void setFileHashesJson(String fileHashesJson) {
        this.fileHashesJson = fileHashesJson;
    }

    /**
     * Resets this {@code ScannerJob} instance back to status {@code QUEUED}
     * to retry a specific scanner job.
     */
    public void resetToQueued() {
        setStatus(ScannerJob.JobStatus.QUEUED);
        setErrorMessage(null);
        setExternalJobId(null);
        setPollAttempts(0);
        setPollLeaseUntil(null);
        setRecoveryInProgress(false);

        var now = TimeUtil.getCurrentUTC();
        setCreatedAt(now);
        setUpdatedAt(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScannerJob scanJob = (ScannerJob) o;
        return id == scanJob.id
                && extensionVersionId == scanJob.extensionVersionId
                && pollAttempts == scanJob.pollAttempts
                && Objects.equals(scanId, scanJob.scanId)
                && Objects.equals(scannerType, scanJob.scannerType)
                && Objects.equals(externalJobId, scanJob.externalJobId)
                && status == scanJob.status
                && Objects.equals(pollLeaseUntil, scanJob.pollLeaseUntil)
                && Objects.equals(createdAt, scanJob.createdAt)
                && Objects.equals(updatedAt, scanJob.updatedAt)
                && Objects.equals(errorMessage, scanJob.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                scanId,
                scannerType,
                extensionVersionId,
                externalJobId,
                status,
                pollLeaseUntil,
                pollAttempts,
                createdAt,
                updatedAt,
                errorMessage);
    }
}
