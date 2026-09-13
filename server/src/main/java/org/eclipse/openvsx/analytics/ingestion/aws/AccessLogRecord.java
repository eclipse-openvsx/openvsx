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
package org.eclipse.openvsx.analytics.ingestion.aws;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.UriUtils;

import org.eclipse.openvsx.analytics.ingestion.RawDownloadRecord;

/**
 * A parsed access log line. Timestamp, country, client IP and user agent are optional: not
 * every log format carries them. Values are kept as found in the log; normalization happens
 * during ingestion.
 */
record AccessLogRecord(
        @NonNull String method,
        int status,
        @NonNull String url,
        @Nullable Instant timestamp,
        @Nullable String country,
        @Nullable String ip,
        @Nullable String userAgent
) {
    /**
     * Turns this log line into a download record, or returns {@code null} if it is not a
     * successful extension package download. Lines without a timestamp fall back to
     * {@code fallbackTime} (typically the log file's date).
     */
    @Nullable
    RawDownloadRecord toDownloadRecord(Instant fallbackTime) {
        if (!isVsixDownload()) {
            return null;
        }

        var uriComponents = url.split("/");
        var vsixFilename = UriUtils.decode(uriComponents[uriComponents.length - 1], StandardCharsets.UTF_8)
                .toUpperCase();
        var time = timestamp != null
                ? timestamp
                : Objects.requireNonNull(fallbackTime, "fallbackTime must not be null");
        return new RawDownloadRecord(time, vsixFilename, country, ip, userAgent);
    }

    private boolean isVsixDownload() {
        return method.equalsIgnoreCase("GET") && status == 200 && url.endsWith(".vsix");
    }
}
