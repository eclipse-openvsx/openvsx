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
import jakarta.annotation.Nullable;

/**
 * JSON representation of a scan check execution result.
 * Shows what checks/scans were run on an extension, regardless of pass/fail.
 */
@Schema(
    name = "CheckResult",
    description = "Result of a single check or scan execution"
)
@JsonInclude(Include.NON_NULL)
public class CheckResultJson {

    @Schema(description = "Type of check")
    private String checkType;

    @Schema(description = "Category: PUBLISH_CHECK or SCANNER_JOB")
    private String category;

    @Schema(description = "Result: PASSED, QUARANTINE, REJECT, or ERROR")
    private String result;

    @Schema(description = "When the check started (UTC)")
    private String startedAt;

    @Schema(description = "When the check completed (UTC)")
    private String completedAt;

    @Schema(description = "Duration of check in milliseconds")
    private Long durationMs;

    @Schema(description = "Number of files scanned (if applicable)")
    private Integer filesScanned;

    @Schema(description = "Number of findings/issues detected")
    private Integer findingsCount;

    @Schema(description = "Brief summary of the result")
    private String summary;

    @Schema(description = "Error message if check failed with error")
    private String errorMessage;

    @Schema(description = "Whether this check was required (errors block publishing). Null for scanner jobs.")
    private Boolean required;

    @Schema(description = "Deep link to the external scanner's own dashboard for this job. " +
        "Only populated for SCANNER_JOB rows whose scanner configures external-url-template.")
    @Nullable
    private String externalUrl;

    // Getters and setters

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
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

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    @Nullable
    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(@Nullable String externalUrl) {
        this.externalUrl = externalUrl;
    }
}
