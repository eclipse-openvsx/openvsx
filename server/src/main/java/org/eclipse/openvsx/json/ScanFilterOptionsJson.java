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
import jakarta.validation.constraints.NotNull;

/**
 * Filter options for the scan listing endpoint.
 *
 * Why this exists:
 * - The admin UI needs to know which values actually exist in the DB.
 * - Returning the DISTINCT values avoids hard-coding lists client-side.
 */
@Schema(
    name = "ScanFilterOptions",
    description = "Lists of unique values that can be used to filter scan results"
)
@JsonInclude(Include.NON_NULL)
public class ScanFilterOptionsJson extends ResultJson {

    @Schema(description = "List of unique validation types from all scans.")
    @NotNull
    private List<String> validationTypes;

    @Schema(description = "List of unique threat scanner names.")
    @NotNull
    private List<String> threatScannerNames;

    public List<String> getValidationTypes() {
        return validationTypes;
    }

    public void setValidationTypes(List<String> validationTypes) {
        this.validationTypes = validationTypes;
    }

    public List<String> getThreatScannerNames() {
        return threatScannerNames;
    }

    public void setThreatScannerNames(List<String> threatScannerNames) {
        this.threatScannerNames = threatScannerNames;
    }
}
