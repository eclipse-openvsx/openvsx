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
package org.eclipse.openvsx.storage.log;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record LogRecord(
        @Nonnull String method,
        int status,
        @Nonnull String url,
        @Nullable String clientIp,
        @Nullable String userAgent,
        @Nullable Instant eventTime
) {
    public LogRecord(@Nonnull String method, int status, @Nonnull String url) {
        this(method, status, url, null, null, null);
    }

    public LogRecord(@Nonnull String method, int status, @Nonnull String url,
                     @Nullable String clientIp, @Nullable String userAgent) {
        this(method, status, url, clientIp, userAgent, null);
    }
}
