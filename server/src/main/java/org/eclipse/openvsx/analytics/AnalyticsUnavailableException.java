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
package org.eclipse.openvsx.analytics;

import org.jspecify.annotations.Nullable;

/**
 * The time-series database cannot be reached, so the analytics operation was not attempted or did
 * not complete. Callers treat it as an outage of an optional feature, never as a registry error.
 */
public class AnalyticsUnavailableException extends RuntimeException {

    public AnalyticsUnavailableException() {
        super("download analytics is currently unavailable");
    }

    public AnalyticsUnavailableException(@Nullable Throwable cause) {
        super("download analytics is currently unavailable", cause);
    }
}
