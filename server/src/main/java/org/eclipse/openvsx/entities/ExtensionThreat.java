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

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Threat detection result from security scanners.
 * Each row represents one file flagged by one scanner.
 */
@Entity
@Table(name = "extension_threat")
public class ExtensionThreat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionThreatSeq")
    @SequenceGenerator(name = "extensionThreatSeq", sequenceName = "extension_threat_seq", allocationSize = 1)
    private long id;

    /** Reference to the parent scan that this threat belongs to */
    @ManyToOne
    @JoinColumn(name = "scan_id", nullable = false)
    private ExtensionScan scan;

    /** Reference to the parent job that this threat belongs to */
    @Column(name = "job_id", nullable = false)
    private long jobId;

    /** Path to file within extension package (null if scanner scans whole package) */
    @Column(name = "file_name", length = 1024)
    private String fileName;

    /** SHA256 hash of the flagged file (null if not available) */
    @Column(name = "file_hash", length = 128)
    private String fileHash;

    /** File extension */
    @Column(name = "file_extension", length = 50)
    private String fileExtension;

    /** Type of security scanner */
    @Column(name = "scanner_type", nullable = false, length = 100)
    private String type;

    /** Name of scanner rule that triggered detection */
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    /** Human-readable reason for threat detection */
    @Column(name = "reason", length = 2048)
    private String reason;

    /** Severity level of the threat (e.g., "Critical", "High", "Medium", "Low") */
    @Column(name = "severity", length = 50)
    private String severity;

    /** Whether this failure was enforced at the time it was detected. */
    @Column(name = "enforced", nullable = false)
    private boolean enforced = true;

    /** Timestamp when threat was detected */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

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

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public void setEnforced(boolean enforced) {
        this.enforced = enforced;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    /**
     * Factory method to create a threat detection record.
     */
    public static ExtensionThreat create(
            String fileName,
            String fileHash,
            String fileExtension,
            String type,
            String ruleName,
            String reason,
            String severity
    ) {
        var threat = new ExtensionThreat();
        threat.setFileName(fileName);
        threat.setFileHash(fileHash);
        threat.setFileExtension(fileExtension);
        threat.setType(type);
        threat.setRuleName(ruleName);
        threat.setReason(reason);
        threat.setSeverity(severity);
        threat.setEnforced(true);
        threat.setDetectedAt(TimeUtil.getCurrentUTC());
        return threat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionThreat that = (ExtensionThreat) o;
        return id == that.id
                && Objects.equals(getScanId(scan), getScanId(that.scan))
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(fileHash, that.fileHash)
                && Objects.equals(type, that.type)
                && Objects.equals(ruleName, that.ruleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getScanId(scan), fileName, fileHash, type, ruleName);
    }

    private Long getScanId(ExtensionScan scan) {
        return scan != null ? scan.getId() : null;
    }
}
