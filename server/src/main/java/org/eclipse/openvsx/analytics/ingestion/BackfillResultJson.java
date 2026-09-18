/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionProcessor.BackfillResult;

@Schema(name = "BackfillResult", description = "Summary of an analytics backfill from one uploaded log file")
public record BackfillResultJson(
        @Schema(description = "Number of aggregated event rows written") int events,
        @Schema(description = "Total downloads those events represent") long downloads,
        @Schema(description = "Distinct extensions the downloads belong to") int extensions,
        @Schema(
            description = "Download records whose vsix file the registry did not recognize, and which were skipped"
        ) int unresolvedRecords,
        @Schema(description = "Earliest event timestamp written, if any") @Nullable Instant from,
        @Schema(description = "Latest event timestamp written, if any") @Nullable Instant to
) {

    public static BackfillResultJson from(BackfillResult result) {
        return new BackfillResultJson(
                result.events(),
                result.downloads(),
                result.extensions(),
                result.unresolvedRecords(),
                result.from(),
                result.to());
    }
}
