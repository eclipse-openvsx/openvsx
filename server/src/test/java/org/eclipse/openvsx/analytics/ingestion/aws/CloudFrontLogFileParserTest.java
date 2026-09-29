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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CloudFrontLogFileParserTest {

    @Test
    public void testHeaderLinesAreSkipped() throws IOException {
        var records = readFixture();
        assertNull(records.get(0));
        assertNull(records.get(1));
    }

    @Test
    public void testParse() throws IOException {
        var record = readFixture().get(2);
        assertNotNull(record);
        assertEquals("OPTIONS", record.method());
        assertEquals(200, record.status());
        assertEquals("/vscjava/vscode-java-pack/0.30.4/package.json", record.url());
        assertEquals(Instant.parse("2025-12-03T13:17:20Z"), record.timestamp());
        // CloudFront standard logs carry no country information
        assertNull(record.country());
        assertEquals("1.1.1.1", record.ip());
        assertEquals("Mozilla/5.0", record.userAgent());
    }

    @Test
    public void testParseDownloadLine() throws IOException {
        var record = readFixture().get(3);
        assertNotNull(record);
        assertEquals("GET", record.method());
        assertEquals(200, record.status());
        assertEquals("/vscjava/vscode-java-pack/0.30.4/file/vscjava.vscode-java-pack-0.30.4.vsix", record.url());
        assertEquals(Instant.parse("2025-12-03T13:20:01Z"), record.timestamp());
        assertNull(record.country());
        assertEquals("VSCode 1.90.2 (Microsoft Visual Studio Code)", record.userAgent());
    }

    @Test
    public void testMissingTimestampAndUserAgent() throws IOException {
        var record = readFixture().get(4);
        assertNotNull(record);
        assertEquals("GET", record.method());
        assertNull(record.timestamp());
        assertNull(record.userAgent());
    }

    @Test
    public void testMalformedLineIsSkipped() throws IOException {
        assertNull(readFixture().get(5));
    }

    private List<AccessLogRecord> readFixture() throws IOException {
        LogFileParser parser = new CloudFrontLogFileParser();
        var records = new ArrayList<AccessLogRecord>();
        try (var is = CloudFrontLogFileParser.class.getResourceAsStream("cloudfront.log")) {
            assertNotNull(is);
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    records.add(parser.parse(line));
                }
            }
        }
        return records;
    }
}
