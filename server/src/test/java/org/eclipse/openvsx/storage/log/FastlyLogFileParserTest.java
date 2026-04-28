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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FastlyLogFileParserTest {

    @Test
    public void testParse() throws IOException {
        LogFileParser parser = new FastlyLogFileParser();

        try (var is = CloudFrontLogFileParser.class.getResourceAsStream("fastly.log")) {
            assertNotNull(is);
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                var record = parser.parse(reader.readLine());
                assertNotNull(record);
                assertEquals("GET", record.method());
                assertEquals(301, record.status());
                assertEquals("/favicon.ico", record.url());
                assertEquals("1.1.1.1", record.clientIp());
                assertEquals("Mozilla/5.0", record.userAgent());
                // "2026-02-09T04:20:50+0000" → UTC Instant
                assertEquals(Instant.parse("2026-02-09T04:20:50Z"), record.eventTime());
            }
        }
    }
}
