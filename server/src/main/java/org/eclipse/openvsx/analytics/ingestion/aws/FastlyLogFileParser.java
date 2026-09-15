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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class FastlyLogFileParser implements LogFileParser {
    private final Logger logger = LoggerFactory.getLogger(FastlyLogFileParser.class);

    private final JsonMapper mapper;

    public FastlyLogFileParser() {
        var module = new SimpleModule();
        module.addDeserializer(AccessLogRecord.class, new AccessLogRecordDeserializer());
        this.mapper = JsonMapper.builder().addModule(module).build();
    }

    @Override
    public @Nullable AccessLogRecord parse(String line) {
        try {
            var jsonStartIndex = line.indexOf("{");
            if (jsonStartIndex != -1) {
                return mapper.readValue(line.substring(jsonStartIndex), AccessLogRecord.class);
            } else {
                return null;
            }
        } catch (JacksonException | IllegalArgumentException | NullPointerException ex) {
            logger.error("could not parse log line '{}'", line, ex);
            return null;
        }
    }
}

class AccessLogRecordDeserializer extends StdDeserializer<AccessLogRecord> {

    // Fastly emits RFC 822 zone offsets, e.g. 2026-02-09T04:20:50+0000
    private static final DateTimeFormatter FASTLY_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT);

    public AccessLogRecordDeserializer() {
        super(AccessLogRecord.class);
    }

    @Override
    public AccessLogRecord deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(jp);
        String operation = node.get("request_method").asString();
        int status = (Integer) node.get("response_status").numberValue();
        String url = node.get("url").asString();
        return new AccessLogRecord(
                operation,
                status,
                url,
                parseTimestamp(optionalString(node, "timestamp")),
                optionalString(node, "geo_country"),
                optionalString(node, "client_ip"),
                optionalString(node, "request_user_agent"));
    }

    private @Nullable String optionalString(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() ? StringUtils.trimToNull(value.asString()) : null;
    }

    private @Nullable Instant parseTimestamp(@Nullable String timestamp) {
        if (timestamp == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(timestamp, FASTLY_TIMESTAMP).toInstant();
        } catch (DateTimeParseException e) {
            // fall through to the ISO format, e.g. 2026-02-09T04:20:50+00:00
        }
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
