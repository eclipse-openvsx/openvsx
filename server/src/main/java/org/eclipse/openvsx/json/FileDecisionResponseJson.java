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

/**
 * Response types for file decision operations.
 */
public final class FileDecisionResponseJson {

    private FileDecisionResponseJson() {
    }

    @Schema(
        name = "FileDecisionResponse",
        description = "Response for file decision create/update operations"
    )
    @JsonInclude(Include.NON_NULL)
    public static class Create extends ResultJson {

        @Schema(description = "Total number of file hashes processed", example = "5")
        private int processed;

        @Schema(description = "Number of decisions applied successfully", example = "4")
        private int successful;

        @Schema(description = "Number of decisions that failed", example = "1")
        private int failed;

        @Schema(description = "Detailed results for each file hash")
        private List<FileDecisionResultJson.Create> results;

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
        public List<FileDecisionResultJson.Create> getResults() {
            return results;
        }
        public void setResults(List<FileDecisionResultJson.Create> results) {
            this.results = results;
        }
    }

    @Schema(
        name = "FileDecisionDeleteResponse",
        description = "Response for file decision delete operations"
    )
    @JsonInclude(Include.NON_NULL)
    public static class Delete extends ResultJson {

        @Schema(description = "Total number of file IDs processed")
        private int processed;

        @Schema(description = "Number of deletions completed successfully")
        private int successful;

        @Schema(description = "Number of deletions that failed")
        private int failed;

        @Schema(description = "Detailed results for each file ID")
        private List<FileDecisionResultJson.Delete> results;

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
        public List<FileDecisionResultJson.Delete> getResults() {
            return results;
        }
        public void setResults(List<FileDecisionResultJson.Delete> results) {
            this.results = results;
        }
    }
}
