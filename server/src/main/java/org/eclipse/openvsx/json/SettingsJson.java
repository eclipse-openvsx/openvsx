/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public class SettingsJson extends ResultJson {

    /**
     * Boxed, so an update can leave a setting alone. A primitive would deserialize an absent field to
     * {@code false}/{@code 0}, which on a PUT means a client that knows nothing of a setting silently
     * resets it - a stale admin tab sending only the fields it knows would turn read-only mode off.
     */
    private @Nullable Boolean readOnly;

    private @Nullable Long maxExtensionSize;

    public @Nullable Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(@Nullable Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public @Nullable Long getMaxExtensionSize() {
        return maxExtensionSize;
    }

    public void setMaxExtensionSize(@Nullable Long maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }
}
