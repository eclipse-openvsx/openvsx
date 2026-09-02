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

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AccessLogRecordTest {

    private static final Instant TIME = Instant.parse("2026-07-01T14:23:45Z");
    private static final Instant FALLBACK = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    public void testToDownloadRecordExtractsDownload() {
        var log = new AccessLogRecord(
                "GET",
                200,
                "/vscjava/vscode-java-pack/0.30.4/file/vscjava.vscode-java-pack-0.30.4.vsix",
                TIME,
                "US",
                "9.9.9.9",
                "VSCode 1.90.2");
        var record = log.toDownloadRecord(FALLBACK);
        assertEquals(TIME, record.time());
        assertEquals("VSCJAVA.VSCODE-JAVA-PACK-0.30.4.VSIX", record.vsixFilename());
        assertEquals("US", record.country());
        assertEquals("9.9.9.9", record.ip());
        assertEquals("VSCode 1.90.2", record.rawUserAgent());
    }

    @Test
    public void testToDownloadRecordDecodesFilename() {
        var log = new AccessLogRecord("GET", 200, "/ns/ext/1.0.0/file/ns.ext%2B1-1.0.0.vsix", TIME, null, null, null);
        var record = log.toDownloadRecord(FALLBACK);
        assertEquals("NS.EXT+1-1.0.0.VSIX", record.vsixFilename());
    }

    @Test
    public void testToDownloadRecordFallsBackToFileTime() {
        var log = new AccessLogRecord("GET", 200, "/ns/ext/file/ns.ext-1.0.0.vsix", null, null, null, null);
        var record = log.toDownloadRecord(FALLBACK);
        assertEquals(FALLBACK, record.time());
    }

    @Test
    public void testToDownloadRecordFiltersNonDownloads() {
        assertNull(
                new AccessLogRecord("OPTIONS", 200, "/ns/ext/file/a.vsix", TIME, null, null, null)
                        .toDownloadRecord(FALLBACK));
        assertNull(
                new AccessLogRecord("GET", 404, "/ns/ext/file/a.vsix", TIME, null, null, null)
                        .toDownloadRecord(FALLBACK));
        assertNull(
                new AccessLogRecord("GET", 200, "/favicon.ico", TIME, null, null, null)
                        .toDownloadRecord(FALLBACK));
    }
}
