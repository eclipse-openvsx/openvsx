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
package org.eclipse.openvsx.storage.log;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class CloudFrontLogFileParserTest {

    @Test
    public void testParse() throws IOException {
        LogFileParser parser = new CloudFrontLogFileParser();

        try (var is = CloudFrontLogFileParser.class.getResourceAsStream("cloudfront.log")) {
            assertNotNull(is);
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                var record = parser.parse(reader.readLine());
                assertNull(record);

                record = parser.parse(reader.readLine());
                assertNull(record);

                record = parser.parse(reader.readLine());
                assertEquals("OPTIONS", record.method());
                assertEquals(200, record.status());
                assertEquals("/vscjava/vscode-java-pack/0.30.4/package.json", record.url());
                assertEquals("1.1.1.1", record.clientIp());
                assertEquals("Mozilla/5.0", record.userAgent());
                // Date "2025-12-03" + time "13:17:20" UTC → verify event-time is parsed correctly
                assertEquals(Instant.parse("2025-12-03T13:17:20Z"), record.eventTime());
            }
        }
    }
}
