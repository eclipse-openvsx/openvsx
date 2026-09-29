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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.*;

/**
 * Main scan record that tracks the complete lifecycle of an extension
 * from upload through validation, scanning, and admin review.
 */
@Entity
@Table(name = "extension_scan")
public class ExtensionScan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionScanSeq")
    @SequenceGenerator(name = "extensionScanSeq", sequenceName = "extension_scan_seq", allocationSize = 1)
    private long id;

    /**
     * Raw metadata about the extension being scanned.
     * These are stored as strings (not foreign keys) so scan history is preserved
     * even if the extension/namespace/version is deleted.
     */
    @Column(name = "namespace_name", nullable = false, length = 255)
    private String namespaceName;

    @Column(name = "extension_name", nullable = false, length = 255)
    private String extensionName;

    @Column(name = "extension_version", nullable = false, length = 100)
    private String extensionVersion;

    @Column(name = "target_platform", nullable = false, length = 255)
    private String targetPlatform;

    /** Login name of user account that published the extension */
    @Column(name = "publisher", nullable = false, length = 255)
    private String publisher;

    /** Profile URL of user account that published the extension (e.g., GitHub profile) */
    @Column(name = "publisher_url", length = 255)
    private String publisherUrl;

    /** Display name captured at scan creation to survive fast-fail rollbacks */
    @Column(name = "extension_display_name", length = 255)
    private String extensionDisplayName;

    /** Target platform flag captured at scan creation */
    @Column(name = "universal_target_platform")
    private boolean universalTargetPlatform;

    /** Timestamp when the scan was initiated */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Timestamp when the scan was completed (null if still in progress) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Current status of the scan process */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScanStatus status;

    /** Error message if scan encountered an error (null otherwise) */
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    /** List of validation failures detected during pre-validation */
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtensionValidationFailure> validationFailures = new ArrayList<>();

    /** List of threats detected by security scanners */
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtensionThreat> threats = new ArrayList<>();

    /** List of all check results (both pass and fail) for audit trail */
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScanCheckResult> checkResults = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(String extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getPublisherUrl() {
        return publisherUrl;
    }

    public void setPublisherUrl(String publisherUrl) {
        this.publisherUrl = publisherUrl;
    }

    public String getExtensionDisplayName() {
        return extensionDisplayName;
    }

    public void setExtensionDisplayName(String extensionDisplayName) {
        this.extensionDisplayName = extensionDisplayName;
    }

    public boolean isUniversalTargetPlatform() {
        return universalTargetPlatform;
    }

    public void setUniversalTargetPlatform(boolean universalTargetPlatform) {
        this.universalTargetPlatform = universalTargetPlatform;
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

    public ScanStatus getStatus() {
        return status;
    }

    public boolean isCompleted() {
        return status.isCompleted();
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ExtensionValidationFailure> getValidationFailures() {
        return validationFailures;
    }

    public void setValidationFailures(List<ExtensionValidationFailure> validationFailures) {
        this.validationFailures = validationFailures;
    }

    public void addValidationFailure(ExtensionValidationFailure failure) {
        validationFailures.add(failure);
        failure.setScan(this);
    }

    public boolean hasValidationFailures() {
        return validationFailures != null && !validationFailures.isEmpty();
    }

    public List<ExtensionThreat> getThreats() {
        return threats;
    }

    public void setThreats(List<ExtensionThreat> threats) {
        this.threats = threats;
    }

    public void addThreat(ExtensionThreat threat) {
        threats.add(threat);
        threat.setScan(this);
    }

    public boolean hasThreats() {
        return threats != null && !threats.isEmpty();
    }

    public List<ScanCheckResult> getCheckResults() {
        return checkResults;
    }

    public void setCheckResults(List<ScanCheckResult> checkResults) {
        this.checkResults = checkResults;
    }

    public void addCheckResult(ScanCheckResult result) {
        checkResults.add(result);
        result.setScan(this);
    }

    /**
     * Resets this {@code ExtensionScan} instance back to status {@code SCANNING}
     * to retry a failed scanner jobs.
     */
    public void resetToScanning() {
        setStatus(ScanStatus.SCANNING);
        setCompletedAt(null);
        setErrorMessage(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionScan that = (ExtensionScan) o;
        return id == that.id
                && Objects.equals(namespaceName, that.namespaceName)
                && Objects.equals(extensionName, that.extensionName)
                && Objects.equals(extensionVersion, that.extensionVersion)
                && Objects.equals(targetPlatform, that.targetPlatform)
                && Objects.equals(publisher, that.publisher)
                && Objects.equals(publisherUrl, that.publisherUrl)
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(completedAt, that.completedAt)
                && status == that.status
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                namespaceName,
                extensionName,
                extensionVersion,
                targetPlatform,
                publisher,
                publisherUrl,
                startedAt,
                completedAt,
                status,
                errorMessage);
    }
}
