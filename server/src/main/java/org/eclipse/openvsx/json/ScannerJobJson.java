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
import org.jspecify.annotations.Nullable;

/**
 * JSON representation of a scanner job, exposing its current lifecycle state.
 * Used by the admin dashboard to distinguish ongoing/queued scanner work from
 * completed check results (which live on {@link CheckResultJson}).
 */
@Schema(
    name = "ScannerJob",
    description = "Lifecycle state of a scanner job for an extension scan"
)
@JsonInclude(Include.NON_NULL)
public class ScannerJobJson {

    @Schema(description = "Unique identifier of the scanner job")
    private String id;

    @Schema(description = "Identifies the scanner type that runs this job")
    private String scannerType;

    @Schema(description = "Current lifecycle status: QUEUED, PROCESSING, SUBMITTED, COMPLETE, FAILED, REMOVED")
    private String status;

    @Schema(description = "When the job was created (UTC)")
    private String createdAt;

    @Schema(description = "When the job was last updated (UTC)")
    private String updatedAt;

    @Schema(description = "Error message if the job failed or was removed")
    @Nullable
    private String errorMessage;

    @Schema(
        description = "Deep link to the external scanner's own dashboard for this job. " +
                "Only populated for async scanners that configure external-url-template."
    )
    @Nullable
    private String externalUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScannerType() {
        return scannerType;
    }

    public void setScannerType(String scannerType) {
        this.scannerType = scannerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Nullable
    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(@Nullable String externalUrl) {
        this.externalUrl = externalUrl;
    }
}
