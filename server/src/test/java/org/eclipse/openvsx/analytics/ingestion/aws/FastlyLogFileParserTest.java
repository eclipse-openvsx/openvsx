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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FastlyLogFileParserTest {

    @Test
    public void testParse() throws IOException {
        var record = readFixture().get(0);
        assertNotNull(record);
        assertEquals("GET", record.method());
        assertEquals(301, record.status());
        assertEquals("/favicon.ico", record.url());
        assertEquals(Instant.parse("2026-02-09T04:20:50Z"), record.timestamp());
        assertEquals("united states", record.country());
        assertEquals("1.1.1.1", record.ip());
        assertEquals("Mozilla/5.0", record.userAgent());
    }

    @Test
    public void testParseDownloadLine() throws IOException {
        var record = readFixture().get(1);
        assertNotNull(record);
        assertEquals("GET", record.method());
        assertEquals(200, record.status());
        assertEquals("/vscjava/vscode-java-pack/0.30.4/file/vscjava.vscode-java-pack-0.30.4.vsix", record.url());
        assertEquals("united states", record.country());
        assertEquals("1.1.1.1", record.ip());
        assertEquals("VSCode 1.90.2 (Microsoft Visual Studio Code)", record.userAgent());
    }

    @Test
    public void testMalformedLineIsSkipped() throws IOException {
        assertNull(readFixture().get(2));
    }

    @Test
    public void testMissingOptionalFields() throws IOException {
        var record = readFixture().get(3);
        assertNotNull(record);
        assertEquals("GET", record.method());
        assertEquals(200, record.status());
        assertNull(record.timestamp());
        assertNull(record.country());
        assertNull(record.ip());
        assertNull(record.userAgent());
    }

    @Test
    public void testLineWithoutJsonIsSkipped() throws IOException {
        assertNull(readFixture().get(4));
    }

    private List<AccessLogRecord> readFixture() throws IOException {
        LogFileParser parser = new FastlyLogFileParser();
        var records = new ArrayList<AccessLogRecord>();
        try (var is = FastlyLogFileParser.class.getResourceAsStream("fastly.log")) {
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
