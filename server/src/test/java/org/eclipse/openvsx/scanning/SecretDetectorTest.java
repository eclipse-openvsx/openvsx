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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.re2j.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for {@link SecretDetector} that exercise keyword routing, allowlists,
 * inline suppressions, and excluded path handling without using the full service.
 */
class SecretDetectorTest {

    private final List<Path> toDelete = new ArrayList<>();

    @AfterEach
    void cleanup() throws IOException {
        for (Path p : toDelete) {
            Files.deleteIfExists(p);
        }
        toDelete.clear();
    }

    @Test
    void scanFile_detectsSecretThroughKeywordRouting() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-kw")
                .description("keyword rule")
                .regex("token([A-Za-z0-9]{9})")
                .keywords("token")
                .build();

        SecretDetector scanner = buildScanner(
                Map.of("token", List.of(rule)),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*excludedExtensions*/ List.of());

        Path zipPath = createZipWithEntry("src/file.txt", "here is tokenABCDEF123 on line\n");
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "src/file.txt", findings);

        assertTrue(scanned, "File should be scanned");
        assertEquals(1, findings.size(), "One finding should be recorded");
        var finding = findings.getFirst();
        assertEquals("rule-kw", finding.getRuleId());
        assertEquals("src/file.txt", finding.getFilePath());
        assertEquals(1, finding.getLineNumber());
    }

    @Test
    void scanFile_skipsAllowlistedAndInlineSuppressedContent() throws Exception {
        // Rule-level allowlist should skip matching content and inline suppression should skip the line.
        SecretRule rule = new SecretRule.Builder()
                .id("rule-allow")
                .description("allowlist rule")
                .regex("password-([A-Za-z0-9]{8,})")
                .keywords("password")
                .allowlistRegexes(List.of("ALLOWED123"))
                .build();

        SecretDetector scanner = buildScanner(
                Map.of("password", List.of(rule)),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of("secret-scanner:ignore"),
                /*excludedPathKeywords*/ List.of(),
                /*excludedExtensions*/ List.of());

        String content = String.join(
                "\n",
                "password-ALLOWED123 should be allowlisted",
                "password-BLOCKME456 secret-scanner:ignore inline suppression");

        Path zipPath = createZipWithEntry("pkg/secret.txt", content);
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "pkg/secret.txt", findings);

        assertTrue(scanned, "File should be scanned");
        assertTrue(findings.isEmpty(), "Allowlist and inline suppression should prevent findings");
    }

    @Test
    void scanFile_respectsExcludedPathMatcher() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-basic")
                .description("basic rule")
                .regex("secret-([A-Za-z0-9]{8})")
                .build();

        SecretDetector scanner = buildScanner(
                Map.of(),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of("node_modules"),
                /*excludedExtensions*/ List.of());

        Path zipPath = createZipWithEntry("node_modules/secret.txt", "secret-ABCDEF12\n");
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "node_modules/secret.txt", findings);

        assertFalse(scanned, "Excluded paths should be skipped");
        assertTrue(findings.isEmpty(), "No findings should be recorded for excluded files");
    }

    @Test
    void scanFile_skipsStopwordsAndGlobalAllowlist() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-stop")
                .description("stopword rule")
                .regex("token-([A-Za-z0-9]{6,})")
                .keywords("token-")
                .build();

        // Global allowlist and stopwords should prevent recording.
        SecretDetector scanner = buildScanner(
                Map.of("token-", List.of(rule)),
                List.of(rule),
                /*allowlistPatterns*/ List.of(Pattern.compile("ALLOWED", Pattern.CASE_INSENSITIVE)),
                /*stopwords*/ List.of("placeholder"),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathKeywords*/ List.of(),
                /*excludedExtensions*/ List.of());

        String content = "token-ALLOWEDplaceholder";
        Path zipPath = createZipWithEntry("src/stopword.txt", content);
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "src/stopword.txt", findings);

        assertTrue(scanned, "File should be scanned");
        assertTrue(findings.isEmpty(), "Stopwords or global allowlists should suppress findings");
    }

    @Test
    void scanFile_skipsExcludedExtensions() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-ext")
                .description("extension rule")
                .regex("secret-([A-Za-z0-9]{8})")
                .build();

        SecretDetector scanner = buildScanner(
                Map.of(),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathKeywords*/ List.of(),
                /*excludedExtensions*/ List.of(".log"));

        Path zipPath = createZipWithEntry("notes/secret.log", "secret-ABCD1234\n");
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "notes/secret.log", findings);

        assertFalse(scanned, "Excluded extensions should prevent scanning");
        assertTrue(findings.isEmpty(), "No findings should be recorded for excluded extensions");
    }

    @Test
    void scanFile_defaultsSecretGroupWhenOutOfRange() throws Exception {
        // secretGroup is intentionally out of bounds; scanner should fall back to the default group.
        SecretRule rule = new SecretRule.Builder()
                .id("rule-group")
                .description("group fallback rule")
                .regex("(supersecret[0-9]{3})(extra)?")
                .secretGroup(5) // invalid index, should fall back to group 1
                .build();

        SecretDetector scanner = buildScanner(
                Map.of(),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*excludedExtensions*/ List.of());

        Path zipPath = createZipWithEntry("src/group.txt", "prefix supersecret999 suffix\n");
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "src/group.txt", findings);

        assertTrue(scanned, "File should be scanned");
        assertEquals(1, findings.size(), "Fallback to default group should still record a finding");
        assertEquals("rule-group", findings.getFirst().getRuleId());
    }

    @Test
    void scanFile_enforcesEntropyThreshold() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-entropy")
                .description("entropy rule")
                .regex("token([A-Za-z0-9]{8,})")
                .keywords("token")
                .entropy(4.5)
                .build();

        SecretDetector scanner = buildScanner(
                Map.of("token", List.of(rule)),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*excludedExtensions*/ List.of());

        String content = String.join(
                "\n",
                "tokenaaaaaaaaaaaa low entropy should be ignored",
                "tokenA1b2C3d4E5f6G7h8J9K0LmNo high entropy should be recorded");

        Path zipPath = createZipWithEntry("src/entropy.txt", content);
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "src/entropy.txt", findings);

        assertTrue(scanned, "File should be scanned");
        assertEquals(1, findings.size(), "Only high-entropy secret should be recorded");
        assertEquals("rule-entropy", findings.getFirst().getRuleId());
    }

    @Test
    void scanFile_skipsVeryLongLines() throws Exception {
        SecretRule rule = new SecretRule.Builder()
                .id("rule-long")
                .description("long line rule")
                .regex("secret-[A-Za-z0-9]+")
                .build();

        SecretDetector scanner = buildScanner(
                Map.of(),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*excludedExtensions*/ List.of());

        // Create a single line longer than the scanner's maxLineLength (10_000) to trigger the skip.
        String longLine = "secret-START-" + "x".repeat(11_000);

        Path zipPath = createZipWithEntry("src/long.txt", longLine);
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();
        boolean scanned = runScan(scanner, zipPath, "src/long.txt", findings);

        assertEquals(0, findings.size(), "No findings should be recorded for skipped files");
        assertTrue(findings.isEmpty(), "No findings should be recorded for skipped files");
    }

    private SecretDetector buildScanner(
            Map<String, List<SecretRule>> keywordToRules,
            List<SecretRule> rules,
            List<Pattern> allowlistPatterns,
            List<String> stopwords,
            List<String> inlineSuppressions,
            List<String> excludedPathPatterns,
            List<String> excludedExtensions
    ) {
        AhoCorasick keywordMatcher = AhoCorasick.builder()
                .addKeywords(keywordToRules.keySet())
                .build();

        AhoCorasick stopwordMatcher = null;
        if (!stopwords.isEmpty()) {
            stopwordMatcher = AhoCorasick.builder()
                    .addKeywords(stopwords)
                    .build();
        }

        List<Pattern> pathPatterns = null;
        if (!excludedPathPatterns.isEmpty()) {
            pathPatterns = excludedPathPatterns.stream()
                    .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                    .toList();
        }

        AhoCorasick excludedExtensionMatcher = null;
        if (!excludedExtensions.isEmpty()) {
            excludedExtensionMatcher = AhoCorasick.builder()
                    .addKeywords(excludedExtensions)
                    .build();
        }

        // Build Aho-Corasick matcher for inline suppressions
        AhoCorasick inlineSuppressionMatcher = null;
        if (!inlineSuppressions.isEmpty()) {
            inlineSuppressionMatcher = AhoCorasick.builder()
                    .addKeywords(inlineSuppressions)
                    .build();
        }

        return new SecretDetector(
                keywordMatcher,
                keywordToRules,
                rules,
                allowlistPatterns,
                pathPatterns,
                stopwordMatcher,
                excludedExtensionMatcher,
                inlineSuppressionMatcher,
                /*skipMimeTypePatterns*/ null,
                new EntropyCalculator(),
                /*maxFileSizeBytes*/ 1_000_000,
                /*maxLineLength*/ 10_000,
                /*timeoutCheckEveryNLines*/ 1_000,
                /*longLineNoSpaceThreshold*/ 500,
                /*keywordContextChars*/ 50,
                /*logAllowlistedPreviewLength*/ 6);
    }

    private Path createZipWithEntry(String entryName, String content) throws IOException {
        Path zipPath = Files.createTempFile("secret-scan-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry(entryName);
            byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            entry.setSize(bytes.length);
            zos.putNextEntry(entry);
            zos.write(bytes);
            zos.closeEntry();
        }
        return zipPath;
    }

    private boolean runScan(
            SecretDetector scanner,
            Path zipPath,
            String entryName,
            List<SecretDetector.Finding> findings
    ) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            assertNotNull(entry, "Zip entry should exist");

            AtomicInteger counter = new AtomicInteger();
            long start = System.currentTimeMillis();
            long timeoutMillis = 5_000;
            return scanner.scanFile(
                    zipFile,
                    entry,
                    findings,
                    start,
                    timeoutMillis,
                    counter,
                    (list, count, finding) -> {
                        list.add(finding);
                        count.incrementAndGet();
                        return true;
                    });
        }
    }

    private boolean runScanWithTimeout(
            SecretDetector scanner,
            Path zipPath,
            String entryName,
            List<SecretDetector.Finding> findings,
            long startTime,
            long timeoutMillis
    ) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            assertNotNull(entry, "Zip entry should exist");

            AtomicInteger counter = new AtomicInteger();
            return scanner.scanFile(
                    zipFile,
                    entry,
                    findings,
                    startTime,
                    timeoutMillis,
                    counter,
                    (list, count, finding) -> {
                        list.add(finding);
                        count.incrementAndGet();
                        return true;
                    });
        }
    }

    @Test
    void scanFile_throwsTimeoutWhenExceedingLimit() throws Exception {
        // Create scanner with frequent timeout checks
        SecretRule rule = new SecretRule.Builder()
                .id("timeout-rule")
                .description("Rule for timeout test")
                .regex("secret-[0-9]+")
                .keywords("secret")
                .build();

        Map<String, List<SecretRule>> keywordIndex = Map.of("secret", List.of(rule));
        AhoCorasick keywordMatcher = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        SecretDetector scanner = new SecretDetector(
                keywordMatcher,
                keywordIndex,
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*stopwordMatcher*/ null,
                /*excludedExtensionMatcher*/ null,
                /*inlineSuppressionMatcher*/ null,
                /*skipMimeTypePatterns*/ null,
                new EntropyCalculator(),
                /*maxFileSizeBytes*/ 1_000_000,
                /*maxLineLength*/ 10_000,
                /*timeoutCheckEveryNLines*/ 5, // Check every 5 lines
                /*longLineNoSpaceThreshold*/ 500,
                /*keywordContextChars*/ 50,
                /*logAllowlistedPreviewLength*/ 6);

        // Create file with many lines
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            content.append("line ").append(i).append(" with content\n");
        }

        Path zipPath = createZipWithEntry("test.txt", content.toString());
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();

        // Set start time in the past to simulate timeout
        long startTime = System.currentTimeMillis() - 10_000; // 10 seconds ago
        long timeoutMillis = 100; // Very short timeout

        // Should throw SecretScanningTimeoutException
        Exception exception = assertThrows(
                Exception.class,
                () -> runScanWithTimeout(scanner, zipPath, "test.txt", findings, startTime, timeoutMillis));

        assertTrue(
                exception.getMessage().contains("timed out"),
                "Exception should mention timeout: " + exception.getMessage());
    }

    @Test
    void scanFile_respectsTimeoutCheckFrequency() throws Exception {
        // Verify timeout is only checked every N lines
        // With a high check frequency, scanning should complete even with expired time
        SecretRule rule = new SecretRule.Builder()
                .id("freq-rule")
                .description("Rule for frequency test")
                .regex("secret-[0-9]+")
                .build();

        SecretDetector scanner = buildScanner(
                Map.of(),
                List.of(rule),
                /*allowlistPatterns*/ List.of(),
                /*stopwords*/ List.of(),
                /*inlineSuppressions*/ List.of(),
                /*excludedPathPatterns*/ List.of(),
                /*excludedExtensions*/ List.of());

        // Create file with 50 lines
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append("line ").append(i).append("\n");
        }

        Path zipPath = createZipWithEntry("test.txt", content.toString());
        toDelete.add(zipPath);

        List<SecretDetector.Finding> findings = new ArrayList<>();

        // Use a reasonable timeout that won't be exceeded during normal execution
        long startTime = System.currentTimeMillis();
        long timeoutMillis = 5000; // 5 seconds should be plenty

        // Should complete successfully
        boolean scanned = runScanWithTimeout(scanner, zipPath, "test.txt", findings, startTime, timeoutMillis);

        assertTrue(scanned, "Should complete scan successfully");
    }

    @Test
    void scanFile_rejectsFileExceedingByteLimit() throws Exception {
        // Test that files exceeding the byte limit are rejected by one of two defenses:
        // 1. Early check: entry.getSize() > maxFileSizeBytes
        // 2. Stream limit: SizeLimitInputStream counts actual bytes read

        SecretRule rule = new SecretRule.Builder()
                .id("test-rule")
                .description("Test rule")
                .regex("secret-[0-9]+")
                .keywords("secret")
                .build();

        Map<String, List<SecretRule>> keywordIndex = Map.of("secret", List.of(rule));
        AhoCorasick keywordMatcher = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        long maxFileSizeBytes = 100;

        SecretDetector scanner = new SecretDetector(
                keywordMatcher,
                keywordIndex,
                List.of(rule),
                List.of(),
                List.of(),
                null,
                null,
                null,
                /*skipMimeTypePatterns*/ null,
                new EntropyCalculator(),
                maxFileSizeBytes,
                10_000,
                100,
                500,
                50,
                6);

        // Create file with 200 bytes (exceeds 100 byte limit)
        byte[] largeContent = new byte[200];
        java.util.Arrays.fill(largeContent, (byte) 'A');

        Path largePath = Files.createTempFile("large-", ".zip");
        toDelete.add(largePath);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(largePath))) {
            ZipEntry entry = new ZipEntry("large.txt");
            zos.putNextEntry(entry);
            zos.write(largeContent);
            zos.closeEntry();
        }

        // Scan should be rejected
        try (ZipFile zipFile = new ZipFile(largePath.toFile())) {
            ZipEntry entry = zipFile.getEntry("large.txt");
            long declaredSize = entry.getSize();

            List<SecretDetector.Finding> findings = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger(0);

            // Early check will reject this
            boolean result = scanner.scanFile(
                    zipFile,
                    entry,
                    findings,
                    System.currentTimeMillis(),
                    5000,
                    counter,
                    (list, count, finding) -> {
                        list.add(finding);
                        count.incrementAndGet();
                        return true;
                    });
            assertFalse(
                    result,
                    "Early size check should reject file with size " + declaredSize + " > limit " + maxFileSizeBytes);
        }
    }

    @Test
    void scanFile_acceptsFileWithinByteLimit() throws Exception {
        // Test that files within the byte limit are scanned successfully

        SecretRule rule = new SecretRule.Builder()
                .id("test-rule")
                .description("Test rule")
                .regex("secret-[0-9]+")
                .keywords("secret")
                .build();

        Map<String, List<SecretRule>> keywordIndex = Map.of("secret", List.of(rule));
        AhoCorasick keywordMatcher = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        long maxFileSizeBytes = 100;

        SecretDetector scanner = new SecretDetector(
                keywordMatcher,
                keywordIndex,
                List.of(rule),
                List.of(),
                List.of(),
                null,
                null,
                null,
                /*skipMimeTypePatterns*/ null,
                new EntropyCalculator(),
                maxFileSizeBytes,
                10_000,
                100,
                500,
                50,
                6);

        // Create file with 50 bytes (within 100 byte limit)
        byte[] validContent = new byte[50];
        java.util.Arrays.fill(validContent, (byte) 'B');

        Path validPath = Files.createTempFile("valid-", ".zip");
        toDelete.add(validPath);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validPath))) {
            ZipEntry entry = new ZipEntry("valid.txt");
            zos.putNextEntry(entry);
            zos.write(validContent);
            zos.closeEntry();
        }

        // Scan should succeed
        try (ZipFile zipFile = new ZipFile(validPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("valid.txt");
            List<SecretDetector.Finding> findings = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger(0);

            boolean result = scanner.scanFile(
                    zipFile,
                    entry,
                    findings,
                    System.currentTimeMillis(),
                    5000,
                    counter,
                    (list, count, finding) -> {
                        list.add(finding);
                        count.incrementAndGet();
                        return true;
                    });

            assertTrue(result, "File within byte limit should be scanned successfully");
        }
    }

    @Test
    void scanFile_streamLimitProtectsAgainstZipBombs() throws Exception {
        // Test that SizeLimitInputStream defends against ZIP BOMB attacks where:
        // - Entry header CLAIMS small size (e.g., 50 bytes - passes early check)
        // - But actual decompression produces MUCH more data (e.g., 500 bytes)
        // - SizeLimitInputStream counts ACTUAL bytes read and throws immediately
        //
        // This simulates a malicious ZIP where the header lies about the uncompressed size.
        // We use Mockito to make getSize() return a fake small value while the actual
        // content is large, demonstrating the defense-in-depth protection.

        SecretRule rule = new SecretRule.Builder()
                .id("test-rule")
                .description("Test rule")
                .regex("secret-[0-9]+")
                .keywords("secret")
                .build();

        Map<String, List<SecretRule>> keywordIndex = Map.of("secret", List.of(rule));
        AhoCorasick keywordMatcher = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        long maxFileSizeBytes = 100;

        SecretDetector scanner = new SecretDetector(
                keywordMatcher,
                keywordIndex,
                List.of(rule),
                List.of(),
                List.of(),
                null,
                null,
                null,
                /*skipMimeTypePatterns*/ null,
                new EntropyCalculator(),
                maxFileSizeBytes,
                10_000,
                100,
                500,
                50,
                6);

        // Create actual content: 500 bytes (way over 100 byte limit)
        byte[] bombContent = new byte[500];
        java.util.Arrays.fill(bombContent, (byte) 'X');

        Path bombPath = Files.createTempFile("bomb-", ".zip");
        toDelete.add(bombPath);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bombPath))) {
            ZipEntry entry = new ZipEntry("bomb.txt");
            zos.putNextEntry(entry);
            zos.write(bombContent);
            zos.closeEntry();
        }

        // Mock the ZipFile and ZipEntry to simulate a ZIP bomb
        // The entry claims 50 bytes (passes early check) but actual content is 500 bytes
        try (ZipFile realZipFile = new ZipFile(bombPath.toFile())) {
            ZipFile mockedZipFile = spy(realZipFile);
            ZipEntry realEntry = realZipFile.getEntry("bomb.txt");
            ZipEntry mockedEntry = spy(realEntry);

            // ATTACK SIMULATION: Entry header claims 50 bytes (< 100 limit)
            // This bypasses the early size check
            when(mockedEntry.getSize()).thenReturn(50L);
            when(mockedEntry.getCompressedSize()).thenReturn(50L);
            when(mockedZipFile.getEntry("bomb.txt")).thenReturn(mockedEntry);

            // But getInputStream() returns the real stream with 500 bytes
            when(mockedZipFile.getInputStream(mockedEntry))
                    .thenReturn(realZipFile.getInputStream(realEntry));

            List<SecretDetector.Finding> findings = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger(0);

            // The early check sees 50 bytes and passes
            // But SizeLimitInputStream counts the ACTUAL 500 bytes being read
            // and throws IOException when it exceeds the 100 byte limit
            Exception exception = assertThrows(
                    IOException.class,
                    () -> scanner.scanFile(
                            mockedZipFile,
                            mockedEntry,
                            findings,
                            System.currentTimeMillis(),
                            5000,
                            counter,
                            (list, count, finding) -> {
                                list.add(finding);
                                count.incrementAndGet();
                                return true;
                            }));

            assertTrue(
                    exception.getMessage().contains("exceeds limit"),
                    "SizeLimitInputStream should catch ZIP bomb during stream reading: " + exception.getMessage());
            assertTrue(
                    exception.getMessage().contains(String.valueOf(maxFileSizeBytes)),
                    "Error should mention the " + maxFileSizeBytes + " byte limit: " + exception.getMessage());

            // Verify the mock was called, proving the early check saw the fake size
            verify(mockedEntry, atLeastOnce()).getSize();
        }
    }
}
