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
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON representation of a security threat detected during scanning.
 * Represents a file flagged by security scanners.
 */
@Schema(
    name = "Threat",
    description = "Security threat detected by scanner"
)
@JsonInclude(Include.NON_NULL)
public class ThreatJson {

    /** Unique identifier for the threat */
    @Schema(description = "Unique identifier for the threat")
    private String id;

    /** Type of security scanner that flagged this file */
    @Schema(description = "Type of security scanner that flagged this file")
    private String type;

    /** Name of the scanner rule that triggered the detection */
    @Schema(description = "Name of the scanner rule that triggered the detection")
    private String ruleName;

    /** Human-readable reason for threat detection */
    @Schema(description = "Human-readable reason for threat detection")
    private String reason;

    /** When the threat was detected (UTC) */
    @Schema(description = "When the threat was detected (UTC)")
    private String dateDetected;

    /** Path to the flagged file within the extension */
    @Schema(description = "Path to the flagged file within the extension")
    private String fileName;

    /** SHA256 hash of the flagged file */
    @Schema(description = "SHA256 hash of the flagged file")
    private String fileHash;

    /** File extension of the flagged file */
    @Schema(description = "File extension of the flagged file")
    private String fileExtension;

    /** Severity level of the threat */
    @Schema(description = "Severity level of the threat")
    private String severity;

    /** Whether this threat is enforced (affects extension status) */
    @Schema(description = "Whether this threat is enforced (affects extension status)")
    private Boolean enforcedFlag;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDateDetected() {
        return dateDetected;
    }

    public void setDateDetected(String dateDetected) {
        this.dateDetected = dateDetected;
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

    public Boolean getEnforcedFlag() {
        return enforcedFlag;
    }

    public void setEnforcedFlag(Boolean enforcedFlag) {
        this.enforcedFlag = enforcedFlag;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
