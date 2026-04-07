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
import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.repositories.FileDecisionRepository;
import org.eclipse.openvsx.util.ArchiveUtil;
import org.eclipse.openvsx.util.TempFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Service for checking extension files against the blocklist during publishing.
 * <p>
 * This service computes SHA256 hashes of all files in the extension package
 * and checks them against the file_decision table. Any file with a BLOCKED
 * decision will cause the extension to be rejected.
 * <p>
 * Implements PublishCheck to be auto-discovered by PublishCheckRunner.
 * Only loaded when blocklist checking is enabled via configuration.
 */
@Service
@Order(2)  // Run second: blocklist check
@ConditionalOnProperty(name = "ovsx.scanning.blocklist-check.enabled", havingValue = "true")
public class BlocklistCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "BLOCKLIST";

    private static final Logger logger = LoggerFactory.getLogger(BlocklistCheckService.class);

    private final BlocklistCheckConfig config;
    private final ExtensionScanConfig scanConfig;
    private final FileDecisionRepository fileDecisionRepository;
    private final AsyncTaskExecutor taskExecutor;

    public BlocklistCheckService(
            BlocklistCheckConfig config,
            ExtensionScanConfig scanConfig,
            FileDecisionRepository fileDecisionRepository,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        this.config = config;
        this.scanConfig = scanConfig;
        this.fileDecisionRepository = fileDecisionRepository;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
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
    public String getUserFacingMessage(List<Failure> failures) {
        return config.getUserMessage();
    }

    @Override
    public Result check(Context context) {
        var blockedFiles = checkForBlockedFiles(context.extensionFile());
        if (blockedFiles.isEmpty()) {
            return Result.pass();
        }

        logger.warn("Blocklist check found {} blocked file(s) in extension {}.{} v{}",
                blockedFiles.size(),
                context.scan().getNamespaceName(),
                context.scan().getExtensionName(),
                context.scan().getExtensionVersion());

        // Create detailed failures for each blocked file.
        // These are stored in the database for admin review.
        var failures = blockedFiles.stream()
                .map(bf -> {
                    logger.warn("  - Blocked file: '{}' (hash: {})", bf.fileName(), bf.fileHash());
                    return new Failure(
                            "BLOCKED_FILE",
                            String.format("File '%s' (hash: %s)",
                                    bf.fileName(),
                                    bf.fileHash())
                    );
                })
                .toList();

        return Result.fail(failures);
    }

    /**
     * Check extension files against the blocklist.
     */
    private List<BlockedFileInfo> checkForBlockedFiles(TempFile extensionFile) {
        // Thread-safe map for parallel hashing: hash -> filePath
        Map<String, String> fileHashes = new ConcurrentHashMap<>();

        try (ZipFile zipFile = new ZipFile(extensionFile.getPath().toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
            
            ArchiveUtil.enforceArchiveLimits(
                    entries,
                    scanConfig.getMaxEntryCount(),
                    scanConfig.getMaxArchiveSizeBytes()
            );

            var hashableEntries = entries.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> ArchiveUtil.isSafePath(entry.getName()))
                    .toList();

            List<CompletableFuture<Void>> futures = new ArrayList<>(hashableEntries.size());
            
            for (ZipEntry entry : hashableEntries) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String hash = computeHash(zipFile, entry);
                        fileHashes.put(hash, entry.getName());
                    } catch (IOException e) {
                        logger.warn("Failed to hash file {}: {}", entry.getName(), e.getMessage());
                    }
                }, taskExecutor);
                futures.add(future);
            }

            // Wait for all hashing to complete (non-blocking wait)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            logger.debug("Blocklist check: hashed {} files, skipped {}", 
                    fileHashes.size(), entries.size() - hashableEntries.size());

        } catch (ZipException e) {
            logger.error("Failed to open extension file as zip: {}", e.getMessage());
            throw new RuntimeException("Failed to check extension file: invalid zip format", e);
        } catch (IOException e) {
            logger.error("Failed to check extension file: {}", e.getMessage());
            throw new RuntimeException("Failed to check extension file: " + e.getMessage(), e);
        }

        if (fileHashes.isEmpty()) {
            return List.of();
        }

        List<FileDecision> blockedDecisions = fileDecisionRepository.findBlockedByFileHashIn(
                fileHashes.keySet()
        );

        if (blockedDecisions.isEmpty()) {
            logger.debug("No blocked files found in extension");
            return List.of();
        }

        List<BlockedFileInfo> result = new ArrayList<>();
        for (FileDecision decision : blockedDecisions) {
            String hash = decision.getFileHash();
            String fileName = fileHashes.get(hash);
            result.add(new BlockedFileInfo(fileName, hash, decision));
            
            logger.info("Blocked file detected: {} (hash: {})", fileName, hash);
        }

        return result;
    }

    /**
     * Compute SHA256 hash of a zip entry.
     */
    private String computeHash(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream is = zipFile.getInputStream(entry)) {
            return DigestUtils.sha256Hex(is);
        }
    }

    /**
     * Information about a blocked file found in an extension.
     */
    private record BlockedFileInfo(
            String fileName,
            String fileHash,
            FileDecision decision
    ) {}
}
