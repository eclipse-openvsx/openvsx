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
        module.addDeserializer(LogRecord.class, new LogRecordDeserializer());
        this.mapper = JsonMapper.builder().addModule(module).build();
    }

    @Override
    public @Nullable LogRecord parse(String line) {
        try {
            var jsonStartIndex = line.indexOf("{");
            if (jsonStartIndex != -1) {
                return mapper.readValue(line.substring(jsonStartIndex), LogRecord.class);
            } else {
                return null;
            }
        } catch (JacksonException ex) {
            logger.error("could not parse log line '{}'", line, ex);
            return null;
        }
    }
}

class LogRecordDeserializer extends StdDeserializer<LogRecord> {

    public LogRecordDeserializer() {
        super(LogRecord.class);
    }

    @Override
    public LogRecord deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(jp);
        String operation = node.get("request_method").asString();
        int status = (Integer) node.get("response_status").numberValue();
        String url = node.get("url").asString();
        return new LogRecord(operation, status, url);
    }
}
