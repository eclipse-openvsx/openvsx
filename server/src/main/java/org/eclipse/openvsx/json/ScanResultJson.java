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
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ScanResult",
    description = "Extension scan result with status and validation details"
)
@JsonInclude(Include.NON_NULL)
public class ScanResultJson extends ResultJson {

    @Schema(description = "Unique identifier for the scan")
    private String id;

    @Schema(description = "Current status of the scan")
    private String status;

    @Schema(description = "URL to extension icon")
    private String extensionIcon;

    @Schema(description = "Display name of the extension")
    private String displayName;

    @Schema(description = "Extension namespace")
    private String namespace;

    @Schema(description = "Name of the extension")
    private String extensionName;

    @Schema(description = "Login name of the user who published the extension")
    private String publisher;

    @Schema(description = "Profile URL of the user who published the extension")
    private String publisherUrl;

    @Schema(description = "Extension version")
    private String version;

    @Schema(description = "URL to download the extension package")
    private String downloadUrl;

    @Schema(description = "Target platform for the scan")
    private String targetPlatform;

    @Schema(description = "True if the scan target platform is universal")
    private Boolean universalTargetPlatform;

    @Schema(description = "When the scan started (UTC)")
    private String dateScanStarted;

    @Schema(description = "When the scan completed (UTC)")
    private String dateScanEnded;

    @Schema(description = "When the extension was quarantined (UTC)")
    private String dateQuarantined;

    @Schema(description = "When the extension was auto-rejected (UTC)")
    private String dateRejected;

    @Schema(description = "Admin decision on quarantined extension")
    private AdminDecisionJson adminDecision;

    @Schema(description = "Files flagged by security scanner")
    private List<ThreatJson> threats;

    @Schema(description = "Validation failures that caused auto-rejection")
    private List<ValidationFailureJson> validationFailures;

    @Schema(description = "All checks/scans that were executed (pass, fail, or skip)")
    private List<CheckResultJson> checkResults;

    @Schema(
        description = "Scanner jobs for this scan with their current lifecycle state " +
                "(QUEUED, PROCESSING, SUBMITTED, COMPLETE, FAILED, REMOVED). Lets the UI surface " +
                "ongoing or queued scanners without inferring their state from checkResults."
    )
    private List<ScannerJobJson> scannerJobs;

    @Schema(description = "Error message if the scan failed with an error")
    private String errorMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExtensionIcon() {
        return extensionIcon;
    }

    public void setExtensionIcon(String extensionIcon) {
        this.extensionIcon = extensionIcon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public Boolean getUniversalTargetPlatform() {
        return universalTargetPlatform;
    }

    public void setUniversalTargetPlatform(Boolean universalTargetPlatform) {
        this.universalTargetPlatform = universalTargetPlatform;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDateScanStarted() {
        return dateScanStarted;
    }

    public void setDateScanStarted(String dateScanStarted) {
        this.dateScanStarted = dateScanStarted;
    }

    public String getDateScanEnded() {
        return dateScanEnded;
    }

    public void setDateScanEnded(String dateScanEnded) {
        this.dateScanEnded = dateScanEnded;
    }

    public String getDateQuarantined() {
        return dateQuarantined;
    }

    public void setDateQuarantined(String dateQuarantined) {
        this.dateQuarantined = dateQuarantined;
    }

    public String getDateRejected() {
        return dateRejected;
    }

    public void setDateRejected(String dateRejected) {
        this.dateRejected = dateRejected;
    }

    public AdminDecisionJson getAdminDecision() {
        return adminDecision;
    }

    public void setAdminDecision(AdminDecisionJson adminDecision) {
        this.adminDecision = adminDecision;
    }

    public List<ThreatJson> getThreats() {
        return threats;
    }

    public void setThreats(List<ThreatJson> threats) {
        this.threats = threats;
    }

    public List<ValidationFailureJson> getValidationFailures() {
        return validationFailures;
    }

    public void setValidationFailures(List<ValidationFailureJson> validationFailures) {
        this.validationFailures = validationFailures;
    }

    public List<CheckResultJson> getCheckResults() {
        return checkResults;
    }

    public void setCheckResults(List<CheckResultJson> checkResults) {
        this.checkResults = checkResults;
    }

    public List<ScannerJobJson> getScannerJobs() {
        return scannerJobs;
    }

    public void setScannerJobs(List<ScannerJobJson> scannerJobs) {
        this.scannerJobs = scannerJobs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
