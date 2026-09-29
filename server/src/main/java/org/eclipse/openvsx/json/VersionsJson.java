/******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "Versions",
    description = "Map of versions matching an extension"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionsJson extends ResultJson {

    public static VersionsJson error(String message) {
        var result = new VersionsJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Number of skipped entries according to the versions request")
    @NotNull
    @Min(0)
    private int offset;

    @Schema(description = "Total number of versions the extension has")
    @NotNull
    @Min(0)
    private int totalSize;

    @Schema(description = "Map of versions, limited to the size specified in the versions request")
    @NotNull
    private Map<String, String> versions;

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

    public Map<String, String> getVersions() {
        return versions;
    }

    public void setVersions(Map<String, String> versions) {
        this.versions = versions;
    }
}
