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
 * Configuration for the display name conflict check during extension publishing.
 * <p>
 * The check rejects a publication that adopts the exact display name (ignoring casing and surrounding
 * whitespace) of an active extension in a namespace the publisher does not belong to. Like every
 * publish check, it only runs when scanning is enabled ({@code ovsx.scanning.enabled}).
 * <p>
 * Configuration example:
 * ovsx:
 *   scanning:
 *     display-name-conflict:
 *       enabled: true     # Run the check (default: true)
 *       enforced: true    # Block publication on a conflict (default: true)
 *       required: true    # Block publication when the check errors (default: true)
 */
@Configuration
public class DisplayNameConflictCheckConfig {

    /**
     * Enables or disables the display name conflict check.
     * <p>
     * Property: {@code ovsx.scanning.display-name-conflict.enabled}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.display-name-conflict.enabled:true}")
    private boolean enabled;

    /**
     * Whether a conflict blocks publication. When false, conflicts are recorded but publication proceeds.
     * <p>
     * Property: {@code ovsx.scanning.display-name-conflict.enforced}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.display-name-conflict.enforced:true}")
    private boolean enforced;

    /**
     * Whether an error during the check blocks publication.
     * <p>
     * Property: {@code ovsx.scanning.display-name-conflict.required}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.display-name-conflict.required:true}")
    private boolean required;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public boolean isRequired() {
        return required;
    }
}
