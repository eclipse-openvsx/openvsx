/******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecretDetector.Result}.
 * These keep the simple value object behavior well-defined and documented.
 */
class SecretDetectorResultTest {

    @Test
    void secretsFound_rejectsEmptyFindingList() {
        // We should never create a positive result with no findings.
        assertThrows(IllegalArgumentException.class, () -> SecretDetector.Result.secretsFound(List.of()));
    }

    @Test
    void secretsFound_wrapsFindingsImmutably() {
        // Build a single finding to ensure the result reflects its data.
        var finding = new SecretDetector.Finding("file.txt", 2, 4.2, "supersecret", "rule-a");
        var result = SecretDetector.Result.secretsFound(List.of(finding));

        assertTrue(result.isSecretsFound());
        assertEquals(1, result.getFindings().size());
        assertEquals("Found 1 potential secret in extension package", result.getSummaryMessage());

        // Verify callers cannot mutate the internal list.
        assertThrows(UnsupportedOperationException.class, () -> result.getFindings().add(finding));
    }

    @Test
    void noSecretsFoundAndSkippedShareSafeDefaults() {
        // Both helpers should produce empty, immutable findings and the "no secrets" summary.
        List<SecretDetector.Result> results = List.of(
                SecretDetector.Result.noSecretsFound(),
                SecretDetector.Result.skipped());

        for (var result : results) {
            assertFalse(result.isSecretsFound());
            assertTrue(result.getFindings().isEmpty());
            assertEquals("No secrets detected", result.getSummaryMessage());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> result.getFindings().add(
                            new SecretDetector.Finding("file", 1, 1.0, "value", "rule")));
        }
    }
}
