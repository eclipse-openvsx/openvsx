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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated scan statistics for all statuses and quarantine decisions.
 */
@Schema(
    name = "ScanStatistics",
    description = "Total counts for scan statuses and quarantine decisions"
)
public class ScanStatisticsJson extends ResultJson {

    @JsonIgnore
    private long STARTED;

    @JsonIgnore
    private long VALIDATING;

    @JsonIgnore
    private long SCANNING;

    @JsonIgnore
    private long PASSED;

    @JsonIgnore
    private long QUARANTINED;

    @JsonIgnore
    private long AUTO_REJECTED;

    @JsonIgnore
    private long ERROR;

    @JsonIgnore
    private long ALLOWED;

    @JsonIgnore
    private long BLOCKED;

    @JsonIgnore
    private long NEEDS_REVIEW;

    @JsonProperty("STARTED")
    public long getSTARTED() {
        return STARTED;
    }

    public void setSTARTED(long STARTED) {
        this.STARTED = STARTED;
    }

    @JsonProperty("VALIDATING")
    public long getVALIDATING() {
        return VALIDATING;
    }

    public void setVALIDATING(long VALIDATING) {
        this.VALIDATING = VALIDATING;
    }

    @JsonProperty("SCANNING")
    public long getSCANNING() {
        return SCANNING;
    }

    public void setSCANNING(long SCANNING) {
        this.SCANNING = SCANNING;
    }

    @JsonProperty("PASSED")
    public long getPASSED() {
        return PASSED;
    }

    public void setPASSED(long PASSED) {
        this.PASSED = PASSED;
    }

    @JsonProperty("QUARANTINED")
    public long getQUARANTINED() {
        return QUARANTINED;
    }

    public void setQUARANTINED(long QUARANTINED) {
        this.QUARANTINED = QUARANTINED;
    }

    @JsonProperty("AUTO_REJECTED")
    public long getAUTO_REJECTED() {
        return AUTO_REJECTED;
    }

    public void setAUTO_REJECTED(long AUTO_REJECTED) {
        this.AUTO_REJECTED = AUTO_REJECTED;
    }

    @JsonProperty("ERROR")
    public long getERROR() {
        return ERROR;
    }

    public void setERROR(long ERROR) {
        this.ERROR = ERROR;
    }

    @JsonProperty("ALLOWED")
    public long getALLOWED() {
        return ALLOWED;
    }

    public void setALLOWED(long ALLOWED) {
        this.ALLOWED = ALLOWED;
    }

    @JsonProperty("BLOCKED")
    public long getBLOCKED() {
        return BLOCKED;
    }

    public void setBLOCKED(long BLOCKED) {
        this.BLOCKED = BLOCKED;
    }

    @JsonProperty("NEEDS_REVIEW")
    public long getNEEDS_REVIEW() {
        return NEEDS_REVIEW;
    }

    public void setNEEDS_REVIEW(long NEEDS_REVIEW) {
        this.NEEDS_REVIEW = NEEDS_REVIEW;
    }
}
