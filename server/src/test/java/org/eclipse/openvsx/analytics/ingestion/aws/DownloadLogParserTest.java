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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

/**
 * Exercises the one stream-level parse path shared by CDN log ingestion and the admin backfill
 * upload: it wraps the per-format {@link LogFileParser}s, so this covers the download filter, the
 * timestamp fallback and gzip detection in one place.
 */
class DownloadLogParserTest {

    private static final Instant FALLBACK = Instant.parse("2026-02-09T00:00:00Z");

    private final DownloadIngestionMetrics metrics = Mockito.mock(DownloadIngestionMetrics.class);
    private final DownloadLogParser parser = new DownloadLogParser(metrics);

    @Test
    void keepsOnlyVsixDownloadsAndReportsLineCounts() throws IOException {
        var records = parser.parse(fixture(), DownloadLogParser.Format.CLOUDFRONT, FALLBACK);

        // of the fixture's 6 lines only the two GET/200/.vsix lines are downloads
        assertEquals(2, records.size());
        assertEquals("VSCJAVA.VSCODE-JAVA-PACK-0.30.4.VSIX", records.get(0).vsixFilename());
        assertEquals(Instant.parse("2025-12-03T13:20:01Z"), records.get(0).time());
        assertEquals("VSCode 1.90.2 (Microsoft Visual Studio Code)", records.get(0).rawUserAgent());

        // the second download line has no timestamp of its own, so it takes the fallback
        assertEquals("FOO.BAR-1.0.0.VSIX", records.get(1).vsixFilename());
        assertEquals(FALLBACK, records.get(1).time());
        assertNull(records.get(1).rawUserAgent());

        // 6 lines read, 3 not parseable into a record (2 headers + 1 garbage line)
        verify(metrics).recordParsedLines(6, 3);
    }

    @Test
    void detectsGzipFromContentNotFileName() throws IOException {
        var plain = parser.parse(fixture(), DownloadLogParser.Format.CLOUDFRONT, FALLBACK);
        var gzipped = parser.parse(gzip(fixtureBytes()), DownloadLogParser.Format.CLOUDFRONT, FALLBACK);

        assertEquals(plain.size(), gzipped.size());
        assertEquals(plain.get(0).vsixFilename(), gzipped.get(0).vsixFilename());
    }

    private InputStream fixture() {
        return new ByteArrayInputStream(fixtureBytes());
    }

    private byte[] fixtureBytes() {
        try (var in = CloudFrontLogFileParser.class.getResourceAsStream("cloudfront.log")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream gzip(byte[] bytes) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(buffer)) {
            gzip.write(bytes);
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }
}
