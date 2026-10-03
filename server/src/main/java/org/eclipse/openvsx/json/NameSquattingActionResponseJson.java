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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for a name squatting moderation action. Reports per-extension outcomes so that a bulk
 * request can partly succeed.
 */
@Schema(
    name = "NameSquattingActionResponse",
    description = "Result of a name squatting moderation action"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingActionResponseJson extends ResultJson {

    @Schema(description = "Total number of extensions processed")
    private int processed;

    @Schema(description = "Number of extensions the action was applied to")
    private int successful;

    @Schema(description = "Number of extensions the action could not be applied to")
    private int failed;

    @Schema(description = "Detailed result for each extension")
    private List<NameSquattingActionResultJson> results;

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

    public List<NameSquattingActionResultJson> getResults() {
        return results;
    }

    public void setResults(List<NameSquattingActionResultJson> results) {
        this.results = results;
    }
}
