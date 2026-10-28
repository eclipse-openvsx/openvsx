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
 * The state of download analytics, for the admin dashboard.
 */
@JsonInclude(Include.NON_NULL)
public class AnalyticsStatusJson extends ResultJson {

    @Schema(description = "Whether download analytics is enabled; when false everything but the file counts is absent")
    private boolean enabled;

    @Schema(description = "Whether the time-series database is currently reachable")
    private @Nullable Boolean available;

    @Schema(description = "UTC time since which the database has been reachable, or unreachable, as ISO 8601")
    private @Nullable String since;

    @Schema(description = "UTC time of the last probe of the database, as ISO 8601")
    private @Nullable String lastProbe;

    @Schema(description = "Why the last probe failed; absent when it succeeded")
    private @Nullable String lastError;

    @Schema(description = "Ingested log files kept until their events can be stored in the analytics database")
    private long pendingFiles;

    @Schema(description = "Log files that failed ingestion and wait for manual attention")
    private long failedFiles;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @Nullable Boolean getAvailable() {
        return available;
    }

    public void setAvailable(@Nullable Boolean available) {
        this.available = available;
    }

    public @Nullable String getSince() {
        return since;
    }

    public void setSince(@Nullable String since) {
        this.since = since;
    }

    public @Nullable String getLastProbe() {
        return lastProbe;
    }

    public void setLastProbe(@Nullable String lastProbe) {
        this.lastProbe = lastProbe;
    }

    public @Nullable String getLastError() {
        return lastError;
    }

    public void setLastError(@Nullable String lastError) {
        this.lastError = lastError;
    }

    public long getPendingFiles() {
        return pendingFiles;
    }

    public void setPendingFiles(long pendingFiles) {
        this.pendingFiles = pendingFiles;
    }

    public long getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(long failedFiles) {
        this.failedFiles = failedFiles;
    }
}
