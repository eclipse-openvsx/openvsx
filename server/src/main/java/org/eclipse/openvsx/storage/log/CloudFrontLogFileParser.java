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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

class CloudFrontLogFileParser implements LogFileParser {

    private static final Logger logger = LoggerFactory.getLogger(CloudFrontLogFileParser.class);

    @Override
    public LogRecord parse(String line) {
        if (line.startsWith("#")) {
            return null;
        }

        // Format:
        // date	time x-edge-location sc-bytes c-ip cs-method cs(Host) cs-uri-stem sc-status	cs(Referer)	cs(User-Agent) cs-uri-query cs(Cookie) x-edge-result-type	x-edge-request-id	x-host-header	cs-protocol	cs-bytes	time-taken	x-forwarded-for	ssl-protocol	ssl-cipher	x-edge-response-result-type	cs-protocol-version	fle-status	fle-encrypted-fields	c-port	time-to-first-byte	x-edge-detailed-result-type	sc-content-type	sc-content-len	sc-range-start	sc-range-end
        var components = line.split("[ \t]+");
        return new LogRecord(
                components[5],
                Integer.parseInt(components[8]),
                components[7],
                components[4],
                components[10],
                parseEventTime(components[0], components[1])
        );
    }

    /**
     * Parses CloudFront date and time columns into a UTC Instant.
     * CloudFront logs always use UTC for these fields.
     * Returns null if parsing fails, so callers can fall back gracefully.
     */
    private Instant parseEventTime(String date, String time) {
        try {
            return LocalDate.parse(date)
                    .atTime(LocalTime.parse(time))
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.debug("Failed to parse CloudFront event time: date={}, time={}", date, time);
            return null;
        }
    }
}
