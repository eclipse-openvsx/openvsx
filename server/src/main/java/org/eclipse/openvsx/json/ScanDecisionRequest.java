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
    name = "ScanDecisionRequest",
    description = "Request body for making security decisions on quarantined scans"
)
@JsonInclude(Include.NON_NULL)
public class ScanDecisionRequest {

    @Schema(description = "List of scan IDs to apply the decision to (can be single or multiple)")
    private List<String> scanIds;

    @Schema(description = "Security decision to apply to all specified scans")
    private String decision;

    public List<String> getScanIds() {
        return scanIds;
    }

    public void setScanIds(List<String> scanIds) {
        this.scanIds = scanIds;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
