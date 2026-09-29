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
    name = "ScanDecisionResponse",
    description = "Response for security decisions on scans"
)
@JsonInclude(Include.NON_NULL)
public class ScanDecisionResponseJson extends ResultJson {

    @Schema(description = "Total number of scan IDs processed")
    private int processed;

    @Schema(description = "Number of decisions applied successfully")
    private int successful;

    @Schema(description = "Number of decisions that failed")
    private int failed;

    @Schema(description = "Detailed results for each scan ID")
    private List<ScanDecisionResultJson> results;

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getSuccessful() {
        return successful;
    }

    public void setSuccessful(int successful) {
        this.successful = successful;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<ScanDecisionResultJson> getResults() {
        return results;
    }

    public void setResults(List<ScanDecisionResultJson> results) {
        this.results = results;
    }
}
