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
 * Result types for individual file decision operations.
 */
public final class FileDecisionResultJson {

    private FileDecisionResultJson() {
    }

    @Schema(
        name = "FileDecisionResult",
        description = "Individual result for a file decision create/update operation"
    )
    @JsonInclude(Include.NON_NULL)
    public static class Create {

        @Schema(description = "The file hash that was processed", example = "a3f5c8e9d2b1f4a6")
        private String fileHash;

        @Schema(description = "Whether the operation was successful", example = "true")
        private boolean success;

        @Schema(description = "Error message if the operation failed", example = "File hash not found")
        private String error;

        public String getFileHash() {
            return fileHash;
        }
        public void setFileHash(String fileHash) {
            this.fileHash = fileHash;
        }
        public boolean isSuccess() {
            return success;
        }
        public void setSuccess(boolean success) {
            this.success = success;
        }
        public String getError() {
            return error;
        }
        public void setError(String error) {
            this.error = error;
        }

        public static Create success(String fileHash) {
            var result = new Create();
            result.setFileHash(fileHash);
            result.setSuccess(true);
            return result;
        }

        public static Create failure(String fileHash, String error) {
            var result = new Create();
            result.setFileHash(fileHash);
            result.setSuccess(false);
            result.setError(error);
            return result;
        }
    }

    @Schema(
        name = "FileDecisionDeleteResult",
        description = "Individual result for a file decision delete operation"
    )
    @JsonInclude(Include.NON_NULL)
    public static class Delete {

        @Schema(description = "The file ID that was processed")
        private Long fileId;

        @Schema(description = "Whether the deletion was successful")
        private boolean success;

        @Schema(description = "Error message if the deletion failed")
        private String error;

        public Long getFileId() {
            return fileId;
        }
        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }
        public boolean isSuccess() {
            return success;
        }
        public void setSuccess(boolean success) {
            this.success = success;
        }
        public String getError() {
            return error;
        }
        public void setError(String error) {
            this.error = error;
        }

        public static Delete success(Long fileId) {
            var result = new Delete();
            result.setFileId(fileId);
            result.setSuccess(true);
            return result;
        }

        public static Delete failure(Long fileId, String error) {
            var result = new Delete();
            result.setFileId(fileId);
            result.setSuccess(false);
            result.setError(error);
            return result;
        }
    }
}
