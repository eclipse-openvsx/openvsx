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

import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.ArchiveUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Service for scanning extension packages for potential secrets before publication.
 * This service analyzes VSIX files to detect potential secrets like API keys, tokens,
 * passwords, and other sensitive credentials that should not be published publicly.
 * <p>
 * Uses Spring's default async executor for parallel file scanning within extension packages.
 * Implements ValidationCheck to be auto-discovered by ExtensionScanService.
 * Only loaded when secret detection is enabled via configuration.
 */
@Service
@Order(3)  // Run third: secret detection
@ConditionalOnProperty(name = "ovsx.scanning.secret-detection.enabled", havingValue = "true")
public class SecretCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "SECRET";
    
    private static final Logger logger = LoggerFactory.getLogger(SecretCheckService.class);
    
    private final SecretDetectorConfig config;
    private final ExtensionScanConfig scanConfig;
    private final SecretDetector fileContentScanner;
    private final AsyncTaskExecutor taskExecutor;

    private final int maxFindings;
    
    /**
     * Exception thrown when scan is cancelled due to finding limits or other constraints.
     */
    static class ScanCancelledException extends RuntimeException {
        ScanCancelledException(String message) { super(message); }
    }
    
    /**
     * Constructs a secret detection service with the specified configuration and executor.
     */
    public SecretCheckService(
            SecretDetectorConfig config,
            ExtensionScanConfig scanConfig,
            SecretDetectorFactory scannerFactory,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.config = config;
        this.scanConfig = scanConfig;
        this.taskExecutor = taskExecutor;
        
        this.maxFindings = config.getMaxFindings();

        this.fileContentScanner = scannerFactory.getScanner();
    }

    @Override
    public boolean isEnforced() {
        return config.isEnforced();
    }

    @Override
    public boolean isRequired() {
        return config.isRequired();
    }
    
    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public PublishCheck.Result check(PublishCheck.Context context) {
        var scanResult = scanForSecrets(context.extensionFile());
        if (!scanResult.isSecretsFound()) {
            return PublishCheck.Result.pass();
        }

        var failures = scanResult.getFindings().stream()
            .map(f -> new PublishCheck.Failure(f.getRuleId(), f.toString()))
            .toList();

        return PublishCheck.Result.fail(failures);
    }
    
    /**
     * Scans an extension package for potential secrets.
     * <p>
     * Callers should check {@link #isEnabled()} before invoking this method.
     */
    private SecretDetector.Result scanForSecrets(@NotNull TempFile extensionFile) {
        // Thread-safe collection for parallel processing
        List<SecretDetector.Finding> findings = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger findingsCount = new AtomicInteger(0);
        
        try (ZipFile zipFile = new ZipFile(extensionFile.getPath().toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
            ArchiveUtil.enforceArchiveLimits(entries, scanConfig.getMaxEntryCount(), scanConfig.getMaxArchiveSizeBytes());
            
            // Filter to only scannable entries BEFORE creating tasks (optimization)
            List<? extends ZipEntry> scannableEntries = entries.stream()
                    .filter(entry -> !entry.isDirectory())
                    .toList();
            
            AtomicInteger filesScanned = new AtomicInteger(0);
            AtomicInteger filesSkipped = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            long timeoutMillis = config.getTimeoutSeconds() * 1000L;
            
            // Submit all tasks and collect CompletableFutures
            List<CompletableFuture<Void>> futures = new ArrayList<>(scannableEntries.size());
            
            for (ZipEntry entry : scannableEntries) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // Check timeout at start of each task
                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        throw new SecretScanningTimeoutException("Secret detection timed out");
                    }
                    
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    String filePath = entry.getName();
                    try {
                        boolean scanned = fileContentScanner.scanFile(
                            zipFile,
                            entry,
                            findings,
                            startTime,
                            timeoutMillis,
                            findingsCount,
                            this::recordFinding
                        );
                        if (scanned) {
                            filesScanned.incrementAndGet();
                        } else {
                            filesSkipped.incrementAndGet();
                        }
                    } catch (SecretScanningTimeoutException | ScanCancelledException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Failed to scan file {}: {}", filePath, e.getMessage());
                        filesSkipped.incrementAndGet();
                    }
                }, taskExecutor);
                futures.add(future);
            }
            
            // Wait for all tasks to complete (or fail)
            boolean timedOut = false;
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (java.util.concurrent.CompletionException ce) {
                Throwable cause = ce.getCause();
                if (cause instanceof SecretScanningTimeoutException) {
                    timedOut = true;
                    // Only return partial results if we found something - otherwise throw
                    if (findings.isEmpty()) {
                        logger.error("Secret detection timed out after {} seconds with no findings",
                                config.getTimeoutSeconds());
                        throw new SecretScanningException(
                                "Secret detection timed out after " + config.getTimeoutSeconds() + " seconds. " +
                                "Please reduce the file size or exclude large files.",
                                (SecretScanningTimeoutException) cause);
                    }
                    logger.warn("Secret detection timed out after {} seconds with {} partial findings",
                            config.getTimeoutSeconds(), findings.size());
                } else if (cause instanceof ScanCancelledException) {
                    // Max findings reached - continue with what we have
                    logger.debug("Scan cancelled early: {}", cause.getMessage());
                }
                // Other exceptions: log and continue
            }
            
            logger.debug("Secret scan complete: {} files scanned, {} files skipped, {} findings, timedOut={}",
                        filesScanned.get(), filesSkipped.get(), findings.size(), timedOut);
            
            // Return results (partial if timed out with findings)
            if (timedOut) {
                return SecretDetector.Result.timedOut(findings);
            } else if (findings.isEmpty()) {
                return SecretDetector.Result.noSecretsFound();
            } else {
                return SecretDetector.Result.secretsFound(findings);
            }
            
        } catch (SecretScanningTimeoutException e) {
            // Timeout at top level (before parallel processing started) - no partial results possible
            logger.error("Secret detection timed out after {} seconds", config.getTimeoutSeconds());
            throw new SecretScanningException(
                    "Secret detection timed out after " + config.getTimeoutSeconds() + " seconds. " +
                    "Please reduce the file size or exclude large files.", e);
        } catch (ZipException e) {
            logger.error("Failed to open extension file as zip: {}", e.getMessage());
            throw new SecretScanningException("Failed to scan extension file: invalid zip format", e);
        } catch (SecretScanningException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to scan extension file: {}", e.getMessage());
            throw new SecretScanningException("Failed to scan extension file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Record a finding while respecting the global cap.
     */
    private boolean recordFinding(List<SecretDetector.Finding> findings, AtomicInteger findingsCount,
                                  SecretDetector.Finding finding) {
        int newCount = findingsCount.incrementAndGet();
        if (newCount > maxFindings) {
            throw new ScanCancelledException("Max findings reached");
        }
        findings.add(finding);
        return true;
    }
}

/**
 * Signals that a scan exceeded the configured timeout budget.
 */
class SecretScanningTimeoutException extends RuntimeException {
    SecretScanningTimeoutException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when secret detection fails.
 * Wraps lower-level exceptions (IO, Zip, etc.) with user-facing messages.
 */
class SecretScanningException extends RuntimeException {
    SecretScanningException(String message) {
        super(message);
    }
    
    SecretScanningException(String message, Throwable cause) {
        super(message, cause);
    }
}

