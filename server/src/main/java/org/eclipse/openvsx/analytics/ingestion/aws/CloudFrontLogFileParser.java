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
package org.eclipse.openvsx.analytics.ingestion.aws;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;

class CloudFrontLogFileParser implements LogFileParser {
    private final Logger logger = LoggerFactory.getLogger(CloudFrontLogFileParser.class);

    private static final String EMPTY_FIELD = "-";

    @Override
    public @Nullable AccessLogRecord parse(String line) {
        if (line.startsWith("#")) {
            return null;
        }

        // Format:
        // date	time x-edge-location sc-bytes c-ip cs-method cs(Host) cs-uri-stem sc-status	cs(Referer)	cs(User-Agent) cs-uri-query cs(Cookie) x-edge-result-type	x-edge-request-id	x-host-header	cs-protocol	cs-bytes	time-taken	x-forwarded-for	ssl-protocol	ssl-cipher	x-edge-response-result-type	cs-protocol-version	fle-status	fle-encrypted-fields	c-port	time-to-first-byte	x-edge-detailed-result-type	sc-content-type	sc-content-len	sc-range-start	sc-range-end
        var components = line.split("[ \t]+");
        if (components.length < 11) {
            logger.warn("skipping malformed log line '{}'", line);
            return null;
        }

        try {
            // CloudFront standard logs carry no country information
            return new AccessLogRecord(
                    components[5],
                    Integer.parseInt(components[8]),
                    components[7],
                    parseTimestamp(components[0], components[1]),
                    null,
                    parseField(components[4]),
                    parseUserAgent(components[10]));
        } catch (RuntimeException e) {
            logger.warn("skipping malformed log line '{}'", line, e);
            return null;
        }
    }

    private @Nullable String parseField(String value) {
        return EMPTY_FIELD.equals(value) ? null : value;
    }

    private @Nullable Instant parseTimestamp(String date, String time) {
        if (EMPTY_FIELD.equals(date) || EMPTY_FIELD.equals(time)) {
            return null;
        }

        return LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }

    private @Nullable String parseUserAgent(String userAgent) {
        if (EMPTY_FIELD.equals(userAgent)) {
            return null;
        }

        return UriUtils.decode(userAgent, StandardCharsets.UTF_8);
    }
}
