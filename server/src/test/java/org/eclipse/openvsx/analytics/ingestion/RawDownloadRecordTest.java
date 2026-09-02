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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RawDownloadRecordTest {

    private static final Instant TIME = Instant.parse("2026-07-01T14:23:45Z");

    @Test
    public void testRequiredFields() {
        var record = new RawDownloadRecord(TIME, "FOO.BAR-1.2.3.VSIX", "US", "9.9.9.9", "VSCode 1.90.2");
        assertEquals(TIME, record.time());
        assertEquals("FOO.BAR-1.2.3.VSIX", record.vsixFilename());
        assertThrows(NullPointerException.class, () -> new RawDownloadRecord(null, "A.VSIX", null, null, null));
        assertThrows(NullPointerException.class, () -> new RawDownloadRecord(TIME, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RawDownloadRecord(TIME, " ", null, null, null));
    }
}
