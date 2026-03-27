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

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.FileDecisionRepository;
import org.eclipse.openvsx.util.TempFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

/**
 * Tests for BlocklistCheckService.
 */
class BlocklistCheckServiceTest {

    @TempDir
    Path tempDir;

    private BlocklistCheckConfig config;
    private FileDecisionRepository fileDecisionRepository;
    private BlocklistCheckService service;

    @BeforeEach
    void setUp() throws Exception {
        config = new BlocklistCheckConfig();
        setField(config, "enabled", true);
        setField(config, "enforced", true);
        setField(config, "userMessage", "Extension blocked due to policy violation");

        ExtensionScanConfig scanConfig = new ExtensionScanConfig();
        setField(scanConfig, "enabled", true);
        setField(scanConfig, "maxArchiveSizeBytes", 100 * 1024 * 1024L);  // 100MB
        setField(scanConfig, "maxSingleFileBytes", 10 * 1024 * 1024L);    // 10MB
        setField(scanConfig, "maxEntryCount", 10000);

        fileDecisionRepository = mock(FileDecisionRepository.class);
        
        // Use SimpleAsyncTaskExecutor for test execution
        AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        service = new BlocklistCheckService(config, scanConfig, fileDecisionRepository, taskExecutor);
    }

    @Test
    void getCheckType_returnsBlocklist() {
        assertEquals("BLOCKLIST", service.getCheckType());
    }

    @Test
    void isEnabled_delegatesToConfig() throws Exception {
        setField(config, "enabled", true);
        assertTrue(service.isEnabled());

        setField(config, "enabled", false);
        assertFalse(service.isEnabled());
    }

    @Test
    void isEnforced_delegatesToConfig() throws Exception {
        setField(config, "enforced", true);
        assertTrue(service.isEnforced());

        setField(config, "enforced", false);
        assertFalse(service.isEnforced());
    }

    @Test
    void getUserFacingMessage_returnsConfiguredMessage() {
        String message = service.getUserFacingMessage(List.of());
        assertEquals("Extension blocked due to policy violation", message);
    }

    @Test
    void check_passesWhenNoBlockedFiles() throws Exception {
        // Create a test zip with clean files
        TempFile extensionFile = createTestZip("clean.txt", "This is clean content");

        // No blocked files in repository
        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenReturn(List.of());

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertTrue(result.passed());
        assertTrue(result.failures().isEmpty());
        verify(fileDecisionRepository).findBlockedByFileHashIn(anySet());
    }

    @Test
    void check_failsWhenBlockedFileFound() throws Exception {
        // Create a test zip with a file that will be blocked
        String blockedContent = "This is blocked content";
        String blockedHash = DigestUtils.sha256Hex(blockedContent.getBytes(StandardCharsets.UTF_8));
        TempFile extensionFile = createTestZip("blocked.txt", blockedContent);

        // Set up blocked file decision
        FileDecision blockedDecision = createBlockedDecision(blockedHash, "blocked.txt");
        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenReturn(List.of(blockedDecision));

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertEquals("BLOCKED_FILE", result.failures().getFirst().ruleName());
        assertTrue(result.failures().getFirst().reason().contains("blocked.txt"));
        assertTrue(result.failures().getFirst().reason().contains(blockedHash));
    }

    @Test
    void check_detectsMultipleBlockedFiles() throws Exception {
        // Create zip with multiple files
        String content1 = "Blocked content one";
        String content2 = "Blocked content two";
        String hash1 = DigestUtils.sha256Hex(content1.getBytes(StandardCharsets.UTF_8));
        String hash2 = DigestUtils.sha256Hex(content2.getBytes(StandardCharsets.UTF_8));
        TempFile extensionFile = createTestZipMultipleFiles(
                new String[]{"bad1.txt", "bad2.txt"},
                new String[]{content1, content2}
        );

        // Both files are blocked
        FileDecision decision1 = createBlockedDecision(hash1, "bad1.txt");
        FileDecision decision2 = createBlockedDecision(hash2, "bad2.txt");
        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenReturn(List.of(decision1, decision2));

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertFalse(result.passed());
        assertEquals(2, result.failures().size());
    }

    @Test
    void check_passesWhenFileIsAllowed() throws Exception {
        // Create a test zip
        String content = "Some content";
        TempFile extensionFile = createTestZip("file.txt", content);

        // No blocked files (only allowed files in repo don't block)
        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenReturn(List.of());

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertTrue(result.passed());
    }

    @Test
    void check_handlesEmptyZip() throws Exception {
        // Create empty zip
        TempFile extensionFile = createEmptyZip();

        var context = createContext(extensionFile);
        var result = service.check(context);

        // Should pass - no files to check
        assertTrue(result.passed());
        // Repository should not be called with empty set
        verify(fileDecisionRepository, never()).findBlockedByFileHashIn(anySet());
    }

    @Test
    void check_skipsDirectories() throws Exception {
        // Create zip with directories
        TempFile extensionFile = createZipWithDirectory();

        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenReturn(List.of());

        var context = createContext(extensionFile);
        var result = service.check(context);

        assertTrue(result.passed());
        // Should have called repository with file hash only, not directory
        verify(fileDecisionRepository).findBlockedByFileHashIn(argThat(set -> 
            set.size() == 1  // Only the file, not the directory
        ));
    }

    @Test
    void check_computesCorrectSha256Hash() throws Exception {
        // Create file with known content
        String content = "test content for hashing";
        String expectedHash = DigestUtils.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
        TempFile extensionFile = createTestZip("hash-test.txt", content);

        // Capture the hashes passed to repository
        when(fileDecisionRepository.findBlockedByFileHashIn(anySet()))
                .thenAnswer(invocation -> {
                    Set<String> hashes = invocation.getArgument(0);
                    assertTrue(hashes.contains(expectedHash), 
                            "Expected hash " + expectedHash + " not found in " + hashes);
                    return List.of();
                });

        var context = createContext(extensionFile);
        service.check(context);

        verify(fileDecisionRepository).findBlockedByFileHashIn(anySet());
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

    private TempFile createTestZipMultipleFiles(String[] fileNames, String[] contents) throws Exception {
        Path zipPath = tempDir.resolve("test-extension-multi.vsix");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (int i = 0; i < fileNames.length; i++) {
                ZipEntry entry = new ZipEntry(fileNames[i]);
                zos.putNextEntry(entry);
                zos.write(contents[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new TempFile(zipPath);
    }

    private TempFile createEmptyZip() throws Exception {
        Path zipPath = tempDir.resolve("empty.vsix");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Empty zip - no entries
        }
        return new TempFile(zipPath);
    }

    private TempFile createZipWithDirectory() throws Exception {
        Path zipPath = tempDir.resolve("with-dir.vsix");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Add directory entry
            ZipEntry dirEntry = new ZipEntry("subdir/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            // Add file in directory
            ZipEntry fileEntry = new ZipEntry("subdir/file.txt");
            zos.putNextEntry(fileEntry);
            zos.write("file content".getBytes(StandardCharsets.UTF_8));
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

    private FileDecision createBlockedDecision(String fileHash, String fileName) {
        FileDecision decision = new FileDecision();
        decision.setFileHash(fileHash);
        decision.setFileName(fileName);
        decision.setDecision(FileDecision.BLOCKED);
        decision.setDecidedAt(LocalDateTime.now());
        
        UserData admin = new UserData();
        admin.setLoginName("admin");
        decision.setDecidedBy(admin);
        
        return decision;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
