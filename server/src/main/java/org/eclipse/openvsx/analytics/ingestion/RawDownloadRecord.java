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
package org.eclipse.openvsx.analytics.ingestion;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A single extension package download as produced by a {@link DownloadRecordSource}, carrying
 * the client IP and raw user agent as found in the source. The vsix filename is upper-cased;
 * country values are normalized during ingestion.
 */
public record RawDownloadRecord(
        Instant time,
        String vsixFilename,
        @Nullable String country,
        @Nullable String ip,
        @Nullable String rawUserAgent
) {
    public RawDownloadRecord {
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(vsixFilename, "vsixFilename must not be null");
        if (vsixFilename.isBlank()) {
            throw new IllegalArgumentException("vsixFilename must not be blank");
        }
    }
}
