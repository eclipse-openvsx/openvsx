/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.UnicodeCommentExtraField;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TempFile;

import static org.junit.jupiter.api.Assertions.*;

class MaliciousZipCheckServiceTest {

    @TempDir
    Path tempDir;

    private MaliciousZipCheckService service;

    @BeforeEach
    void setUp() {
        service = new MaliciousZipCheckService();
    }

    // --- Extra fields checks ---

    @Test
    void check_passesWhenNoExtraFields() throws Exception {
        TempFile extensionFile = createZipWithContent("clean.vsix", "clean.txt", "This is clean content");

        var result = service.check(createContext(extensionFile));

        assertTrue(result.passed());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void check_failsWhenExtraFieldsAreFound() throws Exception {
        TempFile extensionFile = createZipWithExtraField("extra.vsix", "extra.txt", "The content is clean");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("EXTRA_FIELDS_DETECTED", result.failures().getFirst().ruleName());
        assertTrue(result.failures().getFirst().reason().contains("extension file contains zip entries"));
    }

    // --- Duplicate entries checks ---

    @Test
    void check_passesForCleanZip() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "clean-entries.vsix",
                "extension/package.json",
                "extension/README.md");

        var result = service.check(createContext(extensionFile));

        assertTrue(result.passed());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void check_failsWhenEntriesCollideAfterBackslashNormalization() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "duplicate-backslash.vsix",
                "extension/package.json",
                "extension\\package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
        assertTrue(result.failures().getFirst().reason().contains("duplicate zip entries"));
    }

    @Test
    void check_failsWhenEntriesCollideAfterDoubleBackslashNormalization() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "duplicate-backslash.vsix",
                "extension/package.json",
                "extension\\\\package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
        assertTrue(result.failures().getFirst().reason().contains("duplicate zip entries"));
    }

    @Test
    void check_failsWhenEntriesCollideAfterDotSegmentNormalization() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "dot-segment.vsix",
                "extension/package.json",
                "extension/./package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
    }

    @Test
    void check_failsWhenEntriesCollideAfterDotDotSegmentNormalization() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "dotdot-segment.vsix",
                "extension/package.json",
                "extension/sub/../package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(2, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
        assertEquals("UNSAFE_PATH_DETECTED", result.failures().getLast().ruleName());
    }

    @Test
    void check_failsWhenEntriesCollideAfterDotDotSegmentNormalizationMultipleSlashes() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "dotdot-segment.vsix",
                "extension\\/package.json",
                "extension/package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
    }

    @Test
    void check_failsWhenEntriesCollideAfterDotDotSegmentNormalizationMultipleSlashes2() throws Exception {
        TempFile extensionFile = createZipWithEntries(
                "dotdot-segment.vsix",
                "extension/\\package.json",
                "extension/package.json");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("DUPLICATE_NORMALIZED_ENTRIES", result.failures().getFirst().ruleName());
    }

    // --- Unsafe paths checks ---

    @Test
    void check_failsWhenAbsolutePathIsFound() throws Exception {
        TempFile extensionFile = createZipWithEntries("extra.vsix", "/etc/passwd");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("UNSAFE_PATH_DETECTED", result.failures().getFirst().ruleName());
        assertTrue(
                result.failures().getFirst().reason()
                        .contains("extension file contains zip entries with a suspicious path"));
    }

    @Test
    void check_failsWhenPathTraversalIsFound() throws Exception {
        TempFile extensionFile = createZipWithEntries("extra.vsix", "abc/../../test.txt");

        var result = service.check(createContext(extensionFile));

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("UNSAFE_PATH_DETECTED", result.failures().getFirst().ruleName());
        assertTrue(
                result.failures().getFirst().reason()
                        .contains("extension file contains zip entries with a suspicious path"));
    }

    // --- Helper methods ---

    private TempFile createZipWithContent(String zipFileName, String entryName, String content) throws Exception {
        Path zipPath = tempDir.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new TempFile(zipPath);
    }

    private TempFile createZipWithExtraField(String zipFileName, String entryName, String content) throws Exception {
        Path zipPath = tempDir.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry entry = new ZipEntry(entryName);
            var field = new UnicodeCommentExtraField("sample data", "sample data".getBytes());
            var data = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] { field });
            entry.setExtra(data);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new TempFile(zipPath);
    }

    private TempFile createZipWithEntries(String zipFileName, String... entryNames) throws Exception {
        Path zipPath = tempDir.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (var name : entryNames) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(new byte[0]);
                zos.closeEntry();
            }
        }
        return new TempFile(zipPath);
    }

    private PublishCheck.Context createContext(TempFile extensionFile) {
        ExtensionScan scan = new ExtensionScan();
        scan.setNamespaceName("test-namespace");
        scan.setExtensionName("test-extension");
        scan.setExtensionVersion("1.0.0");

        UserData user = new UserData();
        user.setLoginName("testuser");

        return new PublishCheck.Context(scan, extensionFile, user);
    }
}
