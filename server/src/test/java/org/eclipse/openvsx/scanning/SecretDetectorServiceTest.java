/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.util.ArchiveUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for guardrails and helpers inside SecretScanningService.
 * We use reflection to keep production visibility unchanged while ensuring coverage.
 */
class SecretDetectorServiceTest {

    @Test
    void enforceArchiveLimits_throwsWhenEntryCountExceeded() {
        List<ZipEntry> entries = List.of(new ZipEntry("a"), new ZipEntry("b"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> ArchiveUtil.enforceArchiveLimits(entries, 1, 10_000));
        assertTrue(ex.getMessage().contains("too many entries"));
    }

    @Test
    void enforceArchiveLimits_throwsWhenTotalSizeExceeded() {
        ZipEntry e1 = new ZipEntry("a");
        e1.setSize(6);
        ZipEntry e2 = new ZipEntry("b");
        e2.setSize(6);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> ArchiveUtil.enforceArchiveLimits(List.of(e1, e2), 10, 10));
        assertTrue(ex.getMessage().contains("Uncompressed archive size exceeds"));
    }

    @Test
    void recordFinding_respectsGlobalCap() throws Exception {
        SecretCheckService service = buildServiceWithLimits(10, 100, 1);
        List<SecretDetector.Finding> findings = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);

        var first = new SecretDetector.Finding(
                "a.txt",
                1,
                4.0,
                "secretvalue",
                "rule1");
        var second = new SecretDetector.Finding(
                "b.txt",
                2,
                4.0,
                "secretvalue2",
                "rule2");

        assertTrue(invokeRecordFinding(service, findings, count, first));
        assertEquals(1, findings.size());
        assertThrows(
                SecretCheckService.ScanCancelledException.class,
                () -> invokeRecordFinding(service, findings, count, second));
        assertEquals(1, findings.size());
    }

    @Test
    void loadRules_failsFastWhenMissing() {
        var loader = new SecretRuleLoader();
        assertThrows(IllegalStateException.class, () -> loader.load("non-existent-rules.yaml"));
    }

    // --- Helpers ----------------------------------------------------------------

    private SecretCheckService buildServiceWithLimits(int maxEntries, long maxBytes, int maxFindings) throws Exception {
        SecretDetectorConfig config = new SecretDetectorConfig();
        // Disable scanning so we can construct the service without loading rule files.
        setField(config, "enabled", false);
        setField(config, "minifiedLineThreshold", 10_000);
        setField(config, "timeoutSeconds", 10);
        setField(config, "maxFindings", maxFindings);
        setField(config, "rulesPath", "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml");
        setField(config, "timeoutCheckInterval", 100);
        setField(config, "longLineNoSpaceThreshold", 1000);
        setField(config, "regexContextChars", 100);
        setField(config, "debugPreviewChars", 10);

        ExtensionScanConfig scanConfig = new ExtensionScanConfig();
        setField(scanConfig, "enabled", true);
        setField(scanConfig, "maxArchiveSizeBytes", maxBytes);
        setField(scanConfig, "maxSingleFileBytes", 1024 * 1024L);
        setField(scanConfig, "maxEntryCount", maxEntries);

        SecretRuleLoader loader = new SecretRuleLoader();
        // Pass null for gitleaksService since we're using explicit rules, not auto-generated
        SecretDetectorFactory factory = new SecretDetectorFactory(loader, config, scanConfig, null);
        factory.initialize(); // manually trigger wiring outside of Spring context for the test
        var executor = new org.springframework.core.task.SimpleAsyncTaskExecutor();
        return new SecretCheckService(config, scanConfig, factory, executor);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private boolean invokeRecordFinding(
            SecretCheckService service,
            List<SecretDetector.Finding> findings,
            AtomicInteger count,
            SecretDetector.Finding finding
    ) throws Exception {
        Method m = SecretCheckService.class.getDeclaredMethod(
                "recordFinding",
                List.class,
                AtomicInteger.class,
                SecretDetector.Finding.class);
        m.setAccessible(true);
        try {
            return (boolean) m.invoke(service, findings, count, finding);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        }
    }
}
