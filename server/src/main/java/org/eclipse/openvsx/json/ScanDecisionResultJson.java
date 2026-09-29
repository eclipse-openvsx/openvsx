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

@Schema(
    name = "ScanDecisionResult",
    description = "Individual result in a scan decision response"
)
@JsonInclude(Include.NON_NULL)
public class ScanDecisionResultJson {

    @Schema(description = "The scan ID that was processed")
    private String scanId;

    @Schema(description = "Whether the decision was applied successfully")
    private boolean success;

    @Schema(description = "Error message if the decision failed")
    private String error;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
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

    public static ScanDecisionResultJson success(String scanId) {
        var result = new ScanDecisionResultJson();
        result.setScanId(scanId);
        result.setSuccess(true);
        return result;
    }

    public static ScanDecisionResultJson failure(String scanId, String error) {
        var result = new ScanDecisionResultJson();
        result.setScanId(scanId);
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
