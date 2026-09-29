/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
import org.jspecify.annotations.Nullable;

/**
 * The state of the search index, for the admin dashboard.
 */
@JsonInclude(Include.NON_NULL)
public class SearchIndexJson extends ResultJson {

    public static SearchIndexJson error(String message) {
        var result = new SearchIndexJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Whether searching is available at all")
    private boolean enabled;

    @Schema(
        description = "Which engine answers searches",
        allowableValues = { "elasticsearch", "database", "none" }
    )
    private String implementation;

    @Schema(description = "Whether the index has been created; only meaningful for elasticsearch")
    private boolean indexExists;

    @Schema(description = "How many extensions the index holds; omitted when there is no index to count")
    @Nullable
    private Long indexedDocuments;

    @Schema(description = "How many extensions the index is built from, so drift from the document count shows")
    private long activeExtensions;

    @Schema(description = "The deepest result offset the index will serve")
    @Nullable
    private Long maxResultWindow;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

    public boolean isIndexExists() {
        return indexExists;
    }

    public void setIndexExists(boolean indexExists) {
        this.indexExists = indexExists;
    }

    @Nullable
    public Long getIndexedDocuments() {
        return indexedDocuments;
    }

    public void setIndexedDocuments(@Nullable Long indexedDocuments) {
        this.indexedDocuments = indexedDocuments;
    }

    public long getActiveExtensions() {
        return activeExtensions;
    }

    public void setActiveExtensions(long activeExtensions) {
        this.activeExtensions = activeExtensions;
    }

    @Nullable
    public Long getMaxResultWindow() {
        return maxResultWindow;
    }

    public void setMaxResultWindow(@Nullable Long maxResultWindow) {
        this.maxResultWindow = maxResultWindow;
    }
}
