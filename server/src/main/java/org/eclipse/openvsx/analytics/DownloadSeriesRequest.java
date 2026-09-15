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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A download series query: one or more extensions, a UTC time range ({@code from} inclusive,
 * {@code to} exclusive), a bucket interval and an optional grouping dimension. Deliberately
 * richer than what the REST endpoint exposes, so downstream deployments can compose on it.
 */
public record DownloadSeriesRequest(
        List<Long> extensionIds,
        Instant from,
        Instant to,
        DownloadSeriesInterval interval,
        DownloadSeriesGroupBy groupBy
) {
    public DownloadSeriesRequest {
        Objects.requireNonNull(extensionIds, "extensionIds must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(groupBy, "groupBy must not be null");
        if (extensionIds.isEmpty()) {
            throw new IllegalArgumentException("extensionIds must not be empty");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }

        extensionIds = List.copyOf(extensionIds);
    }

    public static DownloadSeriesRequest of(
            long extensionId,
            Instant from,
            Instant to,
            DownloadSeriesInterval interval
    ) {
        return new DownloadSeriesRequest(List.of(extensionId), from, to, interval, DownloadSeriesGroupBy.NONE);
    }
}
