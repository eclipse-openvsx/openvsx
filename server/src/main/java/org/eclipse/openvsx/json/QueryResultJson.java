/*******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "QueryResult",
    description = "Metadata query result"
)
@JsonInclude(Include.NON_NULL)
public class QueryResultJson extends ResultJson {

    public static QueryResultJson error(String message) {
        var result = new QueryResultJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Number of skipped entries according to the query")
    @NotNull
    @Min(0)
    private int offset;

    @Schema(description = "Total number of entries that match the query")
    @NotNull
    @Min(0)
    private int totalSize;

    @Schema(description = "Extensions that match the given query (may be empty)")
    private List<ExtensionJson> extensions;

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

    public List<ExtensionJson> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<ExtensionJson> extensions) {
        this.extensions = extensions;
    }
}
