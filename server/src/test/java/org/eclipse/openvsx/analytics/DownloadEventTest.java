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
package org.eclipse.openvsx.analytics;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DownloadEventTest {

    private static final Instant TIME = Instant.parse("2026-07-01T14:00:00Z");

    @Test
    public void testValidEvent() {
        var event = new DownloadEvent(
                TIME,
                42L,
                7L,
                "redhat",
                "java",
                "1.2.3",
                "universal",
                "US",
                "9.9.9.9",
                "VSCode 1.90.2",
                7);
        assertEquals(TIME, event.time());
        assertEquals(42L, event.extensionId());
        assertEquals(7L, event.extensionVersionId());
        assertEquals("redhat", event.namespace());
        assertEquals("java", event.extensionName());
        assertEquals("1.2.3", event.version());
        assertEquals("universal", event.targetPlatform());
        assertEquals("US", event.country());
        assertEquals("9.9.9.9", event.ip());
        assertEquals("VSCode 1.90.2", event.userAgent());
        assertEquals(7, event.count());
    }

    @Test
    public void testIpAndUserAgentAreOptional() {
        var event = new DownloadEvent(TIME, 42L, 7L, "redhat", "java", "1.2.3", "universal", null, null, null, 1);
        assertNull(event.ip());
        assertNull(event.userAgent());
        // blank values are normalized to null
        var blank = new DownloadEvent(TIME, 42L, 7L, "redhat", "java", "1.2.3", "universal", null, " ", " ", 1);
        assertNull(blank.ip());
        assertNull(blank.userAgent());
    }

    @Test
    public void testCountMustBeAdditive() {
        assertThrows(IllegalArgumentException.class, () -> event("US", 0));
        assertThrows(IllegalArgumentException.class, () -> event("US", -1));
        assertEquals(1, event("US", 1).count());
    }

    @Test
    public void testCountryIsOptionalAndNormalized() {
        assertNull(event(null, 1).country());
        assertEquals("DE", event("de", 1).country());
        assertThrows(IllegalArgumentException.class, () -> event("DEU", 1));
        assertThrows(IllegalArgumentException.class, () -> event("1!", 1));
    }

    @Test
    public void testRequiredFields() {
        assertThrows(
                NullPointerException.class,
                () -> new DownloadEvent(null, 42L, 7L, "n", "e", "1.0.0", "universal", null, null, null, 1));
        assertThrows(
                NullPointerException.class,
                () -> new DownloadEvent(TIME, 42L, 7L, null, "e", "1.0.0", "universal", null, null, null, 1));
        assertThrows(
                NullPointerException.class,
                () -> new DownloadEvent(TIME, 42L, 7L, "n", null, "1.0.0", "universal", null, null, null, 1));
        assertThrows(
                NullPointerException.class,
                () -> new DownloadEvent(TIME, 42L, 7L, "n", "e", null, "universal", null, null, null, 1));
        assertThrows(
                NullPointerException.class,
                () -> new DownloadEvent(TIME, 42L, 7L, "n", "e", "1.0.0", null, null, null, null, 1));
    }

    private DownloadEvent event(String country, int count) {
        return new DownloadEvent(
                TIME,
                42L,
                7L,
                "redhat",
                "java",
                "1.2.3",
                "universal",
                country,
                "9.9.9.9",
                "agent",
                count);
    }
}
