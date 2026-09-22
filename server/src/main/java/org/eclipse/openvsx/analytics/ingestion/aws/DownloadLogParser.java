/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion.aws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;
import org.eclipse.openvsx.analytics.ingestion.RawDownloadRecord;
import org.eclipse.openvsx.util.SizeLimitInputStream;

/**
 * Parses an access-log stream into download records. gzip is detected from the content, so plain
 * or compressed input both work.
 */
@Component
public class DownloadLogParser {

    public enum Format {
        CLOUDFRONT, FASTLY;

        public static Format from(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "unsupported log format '" + value + "', expected cloudfront or fastly");
            }
        }
    }

    private final DownloadIngestionMetrics metrics;

    /**
     * Caps the decompressed size of a parsed stream, so a small but highly-compressible gzip
     * upload cannot grow the in-memory record list without bound.
     */
    @Value("${ovsx.analytics.ingestion.max-decompressed-bytes:536870912}")
    long maxDecompressedBytes = 536_870_912L;

    public DownloadLogParser(DownloadIngestionMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Reads every line of the (optionally gzipped) stream and returns the successful vsix downloads
     * it contains. Records without their own timestamp fall back to {@code fallbackTime}.
     */
    public List<RawDownloadRecord> parse(InputStream input, Format format, Instant fallbackTime) throws IOException {
        LogFileParser lineParser = switch (format) {
            case CLOUDFRONT -> new CloudFrontLogFileParser();
            case FASTLY -> new FastlyLogFileParser();
        };

        var limited = new SizeLimitInputStream(gunzipIfNeeded(input), maxDecompressedBytes);
        try (var reader = new BufferedReader(new InputStreamReader(limited, StandardCharsets.UTF_8))) {
            var records = new ArrayList<RawDownloadRecord>();
            var total = 0;
            var skipped = 0;
            var lines = reader.lines().iterator();
            while (lines.hasNext()) {
                total++;
                var line = lineParser.parse(lines.next());
                if (line == null) {
                    skipped++;
                    continue;
                }
                var download = line.toDownloadRecord(fallbackTime);
                if (download != null) {
                    records.add(download);
                }
            }
            metrics.recordParsedLines(total, skipped);
            return records;
        }
    }

    /** Wraps the stream in a gzip decoder when it starts with the gzip magic bytes. */
    private InputStream gunzipIfNeeded(InputStream input) throws IOException {
        var pushback = new PushbackInputStream(input, 2);
        var first = pushback.read();
        var second = pushback.read();
        if (second != -1) {
            pushback.unread(second);
        }
        if (first != -1) {
            pushback.unread(first);
        }
        var gzipped = first == 0x1f && second == 0x8b;
        return gzipped ? new GZIPInputStream(pushback) : pushback;
    }
}
