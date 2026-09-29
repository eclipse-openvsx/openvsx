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
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Records the result of each check/scan executed on an extension.
 * <p>
 * Unlike ExtensionThreat and ExtensionValidationFailure which only record
 * failures, this entity records ALL check executions - both pass and fail.
 * This provides admins with a complete audit trail of what scans were run.
 */
@Entity
@Table(
    name = "scan_check_result",
    indexes = {
        @Index(name = "scan_check_result_scan_id_idx", columnList = "scan_id"),
        @Index(name = "scan_check_result_check_type_idx", columnList = "check_type")
    }
)
public class ScanCheckResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Category of check - distinguishes publish-time checks from async scanners.
     */
    public enum CheckCategory {
        /** Synchronous publish-time checks */
        PUBLISH_CHECK,
        /** Async/sync scanner jobs */
        SCANNER_JOB
    }

    /**
     * Result of the check execution.
     */
    public enum CheckResult {
        /** Check passed with no issues */
        PASSED,
        /** Scanner found enforced threats - recommends quarantine for admin review */
        QUARANTINE,
        /** Publish check found enforced issues - recommends rejection */
        REJECT,
        /** Check encountered an error during execution */
        ERROR
    }

    @Id
    @GeneratedValue(generator = "scanCheckResultSeq")
    @SequenceGenerator(name = "scanCheckResultSeq", sequenceName = "scan_check_result_seq", allocationSize = 1)
    private long id;

    /** Reference to the parent scan */
    @ManyToOne
    @JoinColumn(name = "scan_id", nullable = false)
    private ExtensionScan scan;

    /**
     * Type of check performed.
     */
    @Column(name = "check_type", nullable = false, length = 100)
    private String checkType;

    /** Category: PUBLISH_CHECK or SCANNER_JOB */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private CheckCategory category;

    /** Result of the check: PASSED, QUARANTINE, REJECT, ERROR */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private CheckResult result;

    /** When the check started */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** When the check completed */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Duration of check in milliseconds */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** Number of files scanned (for file-based scanners) */
    @Column(name = "files_scanned")
    private Integer filesScanned;

    /** Number of findings (threats/failures) detected */
    @Column(name = "findings_count")
    private Integer findingsCount;

    /**
     * Brief summary of the check result.
     */
    @Column(name = "summary", length = 512)
    private String summary;

    /** Error message if check encountered an error */
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    /**
     * Reference to ScannerJob ID for SCANNER_JOB category.
     * Null for PUBLISH_CHECK category.
     */
    @Column(name = "scanner_job_id")
    private Long scannerJobId;

    /**
     * Whether this check was required (errors block publishing).
     * When false, errors are logged but don't block publishing.
     */
    @Column(name = "required")
    private Boolean required;

    // Getters and setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ExtensionScan getScan() {
        return scan;
    }

    public void setScan(ExtensionScan scan) {
        this.scan = scan;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public CheckCategory getCategory() {
        return category;
    }

    public void setCategory(CheckCategory category) {
        this.category = category;
    }

    public CheckResult getResult() {
        return result;
    }

    public void setResult(CheckResult result) {
        this.result = result;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(Integer filesScanned) {
        this.filesScanned = filesScanned;
    }

    public Integer getFindingsCount() {
        return findingsCount;
    }

    public void setFindingsCount(Integer findingsCount) {
        this.findingsCount = findingsCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getScannerJobId() {
        return scannerJobId;
    }

    public void setScannerJobId(Long scannerJobId) {
        this.scannerJobId = scannerJobId;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * Factory method for creating a passed check result.
     */
    public static ScanCheckResult passed(
            String checkType,
            CheckCategory category,
            LocalDateTime startedAt,
            Integer filesScanned,
            String summary
    ) {
        var result = new ScanCheckResult();
        result.setCheckType(checkType);
        result.setCategory(category);
        result.setResult(CheckResult.PASSED);
        result.setStartedAt(startedAt);
        result.setCompletedAt(TimeUtil.getCurrentUTC());
        result.setDurationMs(java.time.Duration.between(startedAt, result.getCompletedAt()).toMillis());
        result.setFilesScanned(filesScanned);
        result.setFindingsCount(0);
        result.setSummary(summary);
        return result;
    }

    /**
     * Factory method for creating a quarantined check result.
     * Used when scanner finds enforced threats - extension needs admin review.
     */
    public static ScanCheckResult quarantined(
            String checkType,
            CheckCategory category,
            LocalDateTime startedAt,
            Integer filesScanned,
            int findingsCount,
            String summary
    ) {
        var result = new ScanCheckResult();
        result.setCheckType(checkType);
        result.setCategory(category);
        result.setResult(CheckResult.QUARANTINE);
        result.setStartedAt(startedAt);
        result.setCompletedAt(TimeUtil.getCurrentUTC());
        result.setDurationMs(java.time.Duration.between(startedAt, result.getCompletedAt()).toMillis());
        result.setFilesScanned(filesScanned);
        result.setFindingsCount(findingsCount);
        result.setSummary(summary);
        return result;
    }

    /**
     * Factory method for creating a rejected check result.
     * Used when publish check finds enforced issues - extension is rejected.
     */
    public static ScanCheckResult rejected(
            String checkType,
            CheckCategory category,
            LocalDateTime startedAt,
            Integer filesScanned,
            int findingsCount,
            String summary
    ) {
        var result = new ScanCheckResult();
        result.setCheckType(checkType);
        result.setCategory(category);
        result.setResult(CheckResult.REJECT);
        result.setStartedAt(startedAt);
        result.setCompletedAt(TimeUtil.getCurrentUTC());
        result.setDurationMs(java.time.Duration.between(startedAt, result.getCompletedAt()).toMillis());
        result.setFilesScanned(filesScanned);
        result.setFindingsCount(findingsCount);
        result.setSummary(summary);
        return result;
    }

    /**
     * Factory method for creating an error check result.
     */
    public static ScanCheckResult error(
            String checkType,
            CheckCategory category,
            LocalDateTime startedAt,
            String errorMessage
    ) {
        var result = new ScanCheckResult();
        result.setCheckType(checkType);
        result.setCategory(category);
        result.setResult(CheckResult.ERROR);
        result.setStartedAt(startedAt);
        result.setCompletedAt(TimeUtil.getCurrentUTC());
        result.setDurationMs(java.time.Duration.between(startedAt, result.getCompletedAt()).toMillis());
        result.setErrorMessage(errorMessage);
        result.setSummary(
                "Error: " + (errorMessage != null && errorMessage.length() > 100
                        ? errorMessage.substring(0, 100) + "..."
                        : errorMessage));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScanCheckResult that = (ScanCheckResult) o;
        return id == that.id
                && Objects.equals(getScanId(scan), getScanId(that.scan))
                && Objects.equals(checkType, that.checkType)
                && category == that.category
                && result == that.result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getScanId(scan), checkType, category, result);
    }

    private Long getScanId(ExtensionScan scan) {
        return scan != null ? scan.getId() : null;
    }
}
