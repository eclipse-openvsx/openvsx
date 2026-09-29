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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for blocklist checking during extension publishing.
 * <p>
 * The blocklist check compares file SHA256 hashes against the file_decision table.
 * Files marked as BLOCKED will prevent publication.
 * <p>
 * Note: Archive limits (max-archive-size-bytes, max-single-file-bytes, max-entry-count)
 * are configured centrally in ExtensionScanConfig and shared by all scanning checks.
 * <p>
 * Configuration example:
 * ovsx:
 *   scanning:
 *     blocklist-check:
 *       enabled: true     # Enable blocklist checking (default: false)
 *       enforced: true    # Block publication on match (default: true)
 */
@Configuration
public class BlocklistCheckConfig {

    /**
     * Enables or disables blocklist checking for extension publishing.
     * <p>
     * Property: {@code ovsx.scanning.blocklist-check.enabled}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.blocklist-check.enabled:false}")
    private boolean enabled;

    /**
     * Whether blocklist matches are enforced (block publishing) when detected.
     * <p>
     * When false, matches are recorded but publication proceeds (monitor-only mode).
     * <p>
     * Property: {@code ovsx.scanning.blocklist-check.enforced}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.blocklist-check.enforced:true}")
    private boolean enforced;

    /**
     * Whether errors during blocklist checking should block publication.
     * <p>
     * When true (default), exceptions during the check will block publishing.
     * When false, exceptions are logged but publishing continues.
     * <p>
     * Property: {@code ovsx.scanning.blocklist-check.required}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.blocklist-check.required:true}")
    private boolean required;

    /**
     * Message shown to users when their extension is blocked.
     * <p>
     * Property: {@code ovsx.scanning.blocklist-check.user-message}
     * Default: {@code "Extension blocked due to policy violation"}
     */
    @Value("${ovsx.scanning.blocklist-check.user-message:Extension blocked due to policy violation}")
    private String userMessage;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public boolean isRequired() {
        return required;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
