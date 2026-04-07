/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ScannerRegistry} scanner management.
 */
class ScannerRegistryTest {

    private ScannerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ScannerRegistry();
    }

    @Test
    void registerScanner_addsToRegistry() {
        var scanner = mockScanner("TYPE_A", false);

        registry.registerScanner(scanner);

        assertTrue(registry.hasScanner("TYPE_A"));
        assertEquals(scanner, registry.getScanner("TYPE_A"));
    }

    @Test
    void registerScanner_throwsOnDuplicate() {
        var scanner1 = mockScanner("TYPE_A", false);
        var scanner2 = mockScanner("TYPE_A", false);

        registry.registerScanner(scanner1);

        assertThrows(IllegalArgumentException.class, () -> registry.registerScanner(scanner2));
    }

    @Test
    void unregisterScanner_removesFromRegistry() {
        var scanner = mockScanner("TYPE_A", false);
        registry.registerScanner(scanner);

        var removed = registry.unregisterScanner("TYPE_A");

        assertEquals(scanner, removed);
        assertFalse(registry.hasScanner("TYPE_A"));
    }

    @Test
    void unregisterScanner_returnsNullIfNotFound() {
        var result = registry.unregisterScanner("NONEXISTENT");
        assertNull(result);
    }

    @Test
    void replaceScanner_replacesExisting() {
        var scanner1 = mockScanner("TYPE_A", false);
        var scanner2 = mockScanner("TYPE_A", true);
        registry.registerScanner(scanner1);

        var old = registry.replaceScanner(scanner2);

        assertEquals(scanner1, old);
        assertEquals(scanner2, registry.getScanner("TYPE_A"));
    }

    @Test
    void replaceScanner_addsIfNotExisting() {
        var scanner = mockScanner("TYPE_A", false);

        var old = registry.replaceScanner(scanner);

        assertNull(old);
        assertEquals(scanner, registry.getScanner("TYPE_A"));
    }

    @Test
    void getScanner_returnsNullIfNotFound() {
        assertNull(registry.getScanner("NONEXISTENT"));
    }

    @Test
    void hasScanner_returnsFalseIfNotFound() {
        assertFalse(registry.hasScanner("NONEXISTENT"));
    }

    @Test
    void getRegisteredTypes_returnsAllTypes() {
        registry.registerScanner(mockScanner("TYPE_A", false));
        registry.registerScanner(mockScanner("TYPE_B", false));
        registry.registerScanner(mockScanner("TYPE_C", false));

        var types = registry.getRegisteredTypes();

        assertEquals(3, types.size());
        assertTrue(types.contains("TYPE_A"));
        assertTrue(types.contains("TYPE_B"));
        assertTrue(types.contains("TYPE_C"));
    }

    @Test
    void getRegisteredTypes_returnsImmutableSet() {
        registry.registerScanner(mockScanner("TYPE_A", false));

        var types = registry.getRegisteredTypes();

        assertThrows(UnsupportedOperationException.class, () -> types.add("TYPE_B"));
    }

    @Test
    void getAllScanners_returnsAllScanners() {
        var scanner1 = mockScanner("TYPE_A", false);
        var scanner2 = mockScanner("TYPE_B", false);
        registry.registerScanner(scanner1);
        registry.registerScanner(scanner2);

        var scanners = registry.getAllScanners();

        assertEquals(2, scanners.size());
        assertTrue(scanners.contains(scanner1));
        assertTrue(scanners.contains(scanner2));
    }

    @Test
    void getAllScanners_returnsImmutableList() {
        registry.registerScanner(mockScanner("TYPE_A", false));

        var scanners = registry.getAllScanners();

        assertThrows(UnsupportedOperationException.class, scanners::clear);
    }

    @Test
    void getAsyncScanners_filtersCorrectly() {
        var syncScanner = mockScanner("SYNC", false);
        var asyncScanner1 = mockScanner("ASYNC1", true);
        var asyncScanner2 = mockScanner("ASYNC2", true);
        registry.registerScanner(syncScanner);
        registry.registerScanner(asyncScanner1);
        registry.registerScanner(asyncScanner2);

        var asyncScanners = registry.getAsyncScanners();

        assertEquals(2, asyncScanners.size());
        assertTrue(asyncScanners.contains(asyncScanner1));
        assertTrue(asyncScanners.contains(asyncScanner2));
        assertFalse(asyncScanners.contains(syncScanner));
    }

    @Test
    void getAsyncScanners_returnsEmptyIfNone() {
        registry.registerScanner(mockScanner("SYNC", false));

        var asyncScanners = registry.getAsyncScanners();

        assertTrue(asyncScanners.isEmpty());
    }

    private Scanner mockScanner(String type, boolean async) {
        var scanner = mock(Scanner.class);
        when(scanner.getScannerType()).thenReturn(type);
        when(scanner.isAsync()).thenReturn(async);
        return scanner;
    }
}
