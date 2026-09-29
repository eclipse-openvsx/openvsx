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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON representation of a validation failure.
 * Represents a specific validation check that failed during pre-validation.
 */
@Schema(
    name = "ValidationFailure",
    description = "Details of a validation check that failed"
)
@JsonInclude(Include.NON_NULL)
public class ValidationFailureJson {

    /** Unique identifier for the validation failure */
    @Schema(description = "Unique identifier for the validation failure")
    private String id;

    /** Human-friendly validation type label */
    @Schema(description = "Type of validation that failed")
    private String type;

    /** Specific rule/file/algorithm name */
    @Schema(description = "Specific rule name for the validation failure")
    private String ruleName;

    /** Detailed explanation of why validation failed */
    @Schema(description = "Detailed explanation of why validation failed")
    private String reason;

    /** When the validation failure occurred (UTC) */
    @Schema(description = "When the validation failure occurred (UTC)")
    private String dateDetected;

    /** Whether this validation failure is enforced */
    @Schema(description = "Whether this validation failure is enforced")
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

    public Boolean getEnforcedFlag() {
        return enforcedFlag;
    }

    public void setEnforcedFlag(Boolean enforcedFlag) {
        this.enforcedFlag = enforcedFlag;
    }
}
