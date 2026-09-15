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
package org.eclipse.openvsx.analytics;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST payload of the download series endpoint. Points are dense and zero-filled; the last point
 * may still be partial (its bucket has not ended, or logs are still being ingested).
 */
@Schema(name = "DownloadSeries", description = "Time series of download counts")
public record DownloadSeriesJson(List<DownloadSeriesPointJson> points) {

    @Schema(name = "DownloadSeriesPoint")
    public record DownloadSeriesPointJson(
            @Schema(description = "UTC start date of the bucket", example = "2026-07-01") String t,
            @Schema(description = "Number of downloads in the bucket", example = "4321") long count
    ) {}
}
