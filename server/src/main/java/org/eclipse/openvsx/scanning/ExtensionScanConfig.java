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

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for extension scanning during publishing.
 * <p>
 * This controls:
 * - Overall scan lifecycle and behavior
 * - Shared archive limits used by all scanning checks (secret detection, blocklist, etc.)
 * <p>
 * Individual checks can have additional check-specific configuration, but archive
 * limits are centralized here to ensure consistency.
 * <p>
 * Configuration example:
 * ovsx:
 *   scanning:
 *     enabled: true                     # Enable extension scanning (default: false)
 *     max-archive-size-bytes: 536870912 # Total archive size limit (512 MB)
 *     max-single-file-bytes: 268435456  # Per-file size limit (256 MB)
 *     max-entry-count: 50000            # Max ZIP entries to process
 */
@Configuration
public class ExtensionScanConfig {

    /**
     * When disabled, no scanning during publishing is performed.
     * Defaults to false - must be explicitly enabled in production.
     */
    @Value("${ovsx.scanning.enabled:false}")
    private boolean enabled;

    /**
     * Maximum total size of the uncompressed archive in bytes.
     * This limit is shared by all scanning checks (secret detection, blocklist, etc.).
     *
     * Property: {@code ovsx.scanning.max-archive-size-bytes}
     * Default: {@code 536870912} (512 MB)
     */
    @Value("${ovsx.scanning.max-archive-size-bytes:536870912}")
    private long maxArchiveSizeBytes;

    /**
     * Maximum size of a single file to process in bytes.
     * Files larger than this are skipped by scanning checks.
     * This limit is shared by all scanning checks.
     *
     * Property: {@code ovsx.scanning.max-single-file-bytes}
     * Default: {@code 268435456} (256 MB)
     */
    @Value("${ovsx.scanning.max-single-file-bytes:268435456}")
    private long maxSingleFileBytes;

    /**
     * Maximum number of zip entries to inspect in an archive.
     * This limit is shared by all scanning checks.
     *
     * Property: {@code ovsx.scanning.max-entry-count}
     * Default: {@code 50000}
     */
    @Value("${ovsx.scanning.max-entry-count:50000}")
    private int maxEntryCount;

    /**
     * Check if extension scanning is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the maximum archive size in bytes.
     * Shared by all scanning checks.
     */
    public long getMaxArchiveSizeBytes() {
        return maxArchiveSizeBytes;
    }

    /**
     * Get the maximum single file size in bytes.
     * Shared by all scanning checks.
     */
    public long getMaxSingleFileBytes() {
        return maxSingleFileBytes;
    }

    /**
     * Get the maximum entry count.
     * Shared by all scanning checks.
     */
    public int getMaxEntryCount() {
        return maxEntryCount;
    }

    @PostConstruct
    public void validate() {
        if (maxArchiveSizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.scanning.max-archive-size-bytes must be positive, got: " + maxArchiveSizeBytes);
        }

        if (maxSingleFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.scanning.max-single-file-bytes must be positive, got: " + maxSingleFileBytes);
        }

        if (maxEntryCount <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.scanning.max-entry-count must be positive, got: " + maxEntryCount);
        }
    }
}
