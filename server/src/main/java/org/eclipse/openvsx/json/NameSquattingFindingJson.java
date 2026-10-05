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
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single name squatting finding, together with the version whose publication triggered it.
 */
@Schema(
    name = "NameSquattingFinding",
    description = "One name squatting check failure recorded for an extension version"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingFindingJson {

    @Schema(description = "Identifier of the recorded validation failure")
    private String id;

    @Schema(description = "Identifier of the scan that recorded this finding")
    private String scanId;

    @Schema(description = "Version whose publication triggered the check")
    private String version;

    @Schema(description = "Target platform of that version")
    private String targetPlatform;

    @Schema(description = "Status of the scan that recorded this finding")
    private String scanStatus;

    @Schema(description = "Name of the rule that flagged the extension")
    private String ruleName;

    @Schema(description = "Explanation of why the extension was flagged")
    private String reason;

    @Schema(description = "When the check failed (UTC)")
    private String dateDetected;

    @Schema(description = "Whether the failure blocked publication when it was detected")
    private boolean enforcedFlag;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
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

    public boolean isEnforcedFlag() {
        return enforcedFlag;
    }

    public void setEnforcedFlag(boolean enforcedFlag) {
        this.enforcedFlag = enforcedFlag;
    }
}
