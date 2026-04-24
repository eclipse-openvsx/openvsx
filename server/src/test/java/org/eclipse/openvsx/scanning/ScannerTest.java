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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Scanner} nested types: Command, Submission, Result, Threat, PollStatus.
 */
class ScannerTest {

    // === Command record tests ===

    @Test
    void command_storesValues() {
        var command = new Scanner.Command(123L, "scan-456");

        assertEquals(123L, command.extensionVersionId());
        assertEquals("scan-456", command.scanId());
    }

    // === Submission record tests ===

    @Test
    void submission_storesExternalJobId() {
        var submission = new Scanner.Submission("job-123");

        assertEquals("job-123", submission.externalJobId());
        assertTrue(submission.fileHashes().isEmpty());
        assertFalse(submission.hasFileHashes());
    }

    @Test
    void submission_storesFileHashes() {
        var hashes = Map.of("file1.js", "abc123", "file2.js", "def456");
        var submission = new Scanner.Submission("job-123", hashes);

        assertEquals("job-123", submission.externalJobId());
        assertEquals(hashes, submission.fileHashes());
        assertTrue(submission.hasFileHashes());
    }

    @Test
    void submission_fileHashesNeverNull() {
        var submission = new Scanner.Submission("job-123", null);

        assertNotNull(submission.fileHashes());
        assertTrue(submission.fileHashes().isEmpty());
    }

    // === Result class tests ===

    @Test
    void result_cleanHasNoThreats() {
        var result = Scanner.Result.clean();

        assertTrue(result.isClean());
        assertTrue(result.getThreats().isEmpty());
    }

    @Test
    void result_withThreatsIsNotClean() {
        var threat = new Scanner.Threat("malware", "Found malware", "HIGH");
        var result = Scanner.Result.withThreats(List.of(threat));

        assertFalse(result.isClean());
        assertEquals(1, result.getThreats().size());
        assertEquals("malware", result.getThreats().getFirst().getName());
    }

    @Test
    void result_threatsListIsImmutable() {
        var threat = new Scanner.Threat("test", null, "LOW");
        var result = Scanner.Result.withThreats(List.of(threat));

        assertThrows(UnsupportedOperationException.class, () ->
                result.getThreats().clear());
    }

    @Test
    void result_cleanWithoutSummaryReturnsNullSummary() {
        var result = Scanner.Result.clean();

        assertNull(result.getSummary());
    }

    @Test
    void result_cleanWithSummaryStoresIt() {
        var result = Scanner.Result.clean("Extension looks benign.");

        assertTrue(result.isClean());
        assertEquals("Extension looks benign.", result.getSummary());
    }

    @Test
    void result_withThreatsAndSummaryStoresBoth() {
        var threat = new Scanner.Threat("malware", "found", "HIGH");
        var result = Scanner.Result.withThreats(List.of(threat), "Verdict: malicious");

        assertFalse(result.isClean());
        assertEquals(1, result.getThreats().size());
        assertEquals("Verdict: malicious", result.getSummary());
    }

    // === Scanner default methods ===

    @Test
    void scanner_buildExternalUrlDefaultsToNull() {
        // The default is used by scanners that have no external UI (internal
        // checks, self-hosted services without a dashboard). They simply don't
        // contribute a "View in scanner" deep link.
        Scanner scanner = new Scanner() {
            @Override public @jakarta.annotation.Nonnull String getScannerType() { return "TEST"; }
            @Override public boolean isAsync() { return false; }
            @Override public @jakarta.annotation.Nonnull Scanner.Invocation startScan(@jakarta.annotation.Nonnull Command command) { throw new UnsupportedOperationException(); }
        };

        assertNull(scanner.buildExternalUrl("any-job-id"));
        assertNull(scanner.buildExternalUrl(null));
    }

    // === Threat class tests ===

    @Test
    void threat_storesBasicFields() {
        var threat = new Scanner.Threat("virus", "Detected virus", "HIGH");

        assertEquals("virus", threat.getName());
        assertEquals("Detected virus", threat.getDescription());
        assertEquals("HIGH", threat.getSeverity());
        assertNull(threat.getFilePath());
        assertNull(threat.getFileHash());
    }

    @Test
    void threat_storesFilePath() {
        var threat = new Scanner.Threat("secret", "Found secret", "MEDIUM", "src/config.js");

        assertEquals("src/config.js", threat.getFilePath());
        assertNull(threat.getFileHash());
    }

    @Test
    void threat_storesAllFields() {
        var threat = new Scanner.Threat(
                "malware",
                "Malware detected",
                "CRITICAL",
                "bin/payload.exe",
                "abc123def456"
        );

        assertEquals("malware", threat.getName());
        assertEquals("Malware detected", threat.getDescription());
        assertEquals("CRITICAL", threat.getSeverity());
        assertEquals("bin/payload.exe", threat.getFilePath());
        assertEquals("abc123def456", threat.getFileHash());
    }

    @Test
    void threat_allowsNullDescription() {
        var threat = new Scanner.Threat("test", null, "LOW");

        assertEquals("test", threat.getName());
        assertNull(threat.getDescription());
        assertEquals("LOW", threat.getSeverity());
    }
}
