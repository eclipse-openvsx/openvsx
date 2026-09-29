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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Paginated list of scan results.
 * Includes paging metadata so the admin UI can request additional pages.
 */
@Schema(
    name = "ScanResultList",
    description = "Paginated list of extension scan results"
)
@JsonInclude(Include.NON_NULL)
public class ScanResultListJson extends ResultJson {

    public static ScanResultListJson error(String message) {
        var result = new ScanResultListJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Number of skipped entries")
    @NotNull
    @Min(0)
    private int offset;

    @Schema(description = "Total number of matching scan results")
    @NotNull
    @Min(0)
    private int totalSize;

    @Schema(description = "Current page of scan results")
    @NotNull
    private List<ScanResultJson> scans;

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public List<ScanResultJson> getScans() {
        return scans;
    }

    public void setScans(List<ScanResultJson> scans) {
        this.scans = scans;
    }
}
