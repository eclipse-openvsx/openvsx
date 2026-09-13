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

import java.time.Instant;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A time-bucketed, strictly additive download fact: {@code SUM(count)} over any grouping is a
 * valid total. Every event correlates with the concrete extension version it was downloaded
 * from: {@code extensionId} and {@code extensionVersionId} are the keys (no foreign keys into
 * the registry tables), while namespace, extension name, version and target platform are
 * write-time snapshots.
 * <p>
 * The client IP and raw user agent are persisted as found in the source (first iteration);
 * deriving client classifications from the user agent is left to future consumers.
 */
public record DownloadEvent(
        Instant time,
        long extensionId,
        long extensionVersionId,
        String namespace,
        String extensionName,
        String version,
        String targetPlatform,
        @Nullable String country,
        @Nullable String ip,
        @Nullable String userAgent,
        int count
) {
    public DownloadEvent {
        Objects.requireNonNull(time, "time must not be null");
        requireNonBlank(namespace, "namespace");
        requireNonBlank(extensionName, "extensionName");
        requireNonBlank(version, "version");
        requireNonBlank(targetPlatform, "targetPlatform");
        if (count < 1) {
            throw new IllegalArgumentException("count must be a positive increment, got " + count);
        }

        country = normalizeCountry(country);
        ip = StringUtils.trimToNull(ip);
        userAgent = StringUtils.trimToNull(userAgent);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static @Nullable String normalizeCountry(@Nullable String country) {
        if (country == null) {
            return null;
        }
        if (country.length() != 2 || !country.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException("country must be a two-letter ISO code, got '" + country + "'");
        }

        return country.toUpperCase();
    }
}
