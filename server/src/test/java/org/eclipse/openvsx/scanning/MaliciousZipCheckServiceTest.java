/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.UnicodeCommentExtraField;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TempFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MaliciousZipCheckService.
 */
class MaliciousZipCheckServiceTest {

    @TempDir
    Path tempDir;

    private MaliciousZipCheckService service;

    @BeforeEach
    void setUp() {
        service = new MaliciousZipCheckService();
    }

    @Test
    void check_passesWhenNoExtraFields() throws Exception {
        // Create a test zip with clean files
        TempFile extensionFile = createTestZip("clean.txt", "This is clean content");

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertTrue(result.passed());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void check_failsWhenExtraFieldsAreFound() throws Exception {
        // Create a test zip with a file that contains extra fields
        TempFile extensionFile = createTestZipWithExtraField("extra.txt", "The content is clean");

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("EXTRA_FIELDS_DETECTED", result.failures().getFirst().ruleName());
        assertTrue(result.failures().getFirst().reason().contains("extension file contains zip entries"));
    }

    // --- Helper methods ---

    private TempFile createTestZip(String fileName, String content) throws Exception {
        Path zipPath = tempDir.resolve("test-extension.vsix");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new TempFile(zipPath);
    }

    private TempFile createTestZipWithExtraField(String fileName, String content) throws Exception {
        Path zipPath = tempDir.resolve("test-extension-extra.vsix");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry entry = new ZipEntry(fileName);
            var field = new UnicodeCommentExtraField("sample data", "sample data".getBytes());
            var data = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] { field });
            entry.setExtra(data);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
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
