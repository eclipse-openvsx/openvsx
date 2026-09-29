/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static LocalDateTime getCurrentUTC() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }

    public static String toUTCString(LocalDateTime dateTime) {
        return dateTime.toString() + 'Z';
    }

    public static LocalDateTime convertToUTC(LocalDateTime dateTime, String zone) {
        var offset = ZoneId.of(zone).getRules().getOffset(LocalDateTime.now());
        return dateTime.minusSeconds(offset.getTotalSeconds());
    }

    public static LocalDateTime fromUTCString(String timestamp) {
        timestamp = timestamp.substring(0, timestamp.length() - 1); // Strip 'Z'
        return LocalDateTime.parse(timestamp);
    }
}
