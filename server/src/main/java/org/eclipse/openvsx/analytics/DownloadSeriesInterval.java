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

/**
 * Bucket size of a download series. Buckets start at UTC midnight (day), UTC Monday (week)
 * or the first of the month (month).
 */
public enum DownloadSeriesInterval {
    DAY("day"), WEEK("week"), MONTH("month");

    private final String value;

    DownloadSeriesInterval(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DownloadSeriesInterval fromValue(String value) {
        for (var interval : values()) {
            if (interval.value.equals(value)) {
                return interval;
            }
        }

        throw new IllegalArgumentException("unknown interval '" + value + "', expected day, week or month");
    }
}
