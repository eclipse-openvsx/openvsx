/**
 * ******************************************************************************
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
 * ******************************************************************************
 */
package org.eclipse.openvsx.metrics.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DownloadCountValidationProperties {

    private static final Logger logger = LoggerFactory.getLogger(DownloadCountValidationProperties.class);

    /**
     * Master toggle for download count validation.
     * When false, all downloads are counted.
     */
    @Value("${ovsx.download-count.validation.enabled:false}")
    private boolean enabled;

    /**
     * Maximum number of downloads counted per IP per extension per hour.
     * Downloads beyond this limit within the same hour window are ignored.
     * Default: 3 — allows a few legitimate re-downloads per hour while
     * preventing bulk inflation from a single IP.
     */
    @Value("${ovsx.download-count.validation.hourly-limit-per-ip:50}")
    private int hourlyLimitPerIp;

    /**
     * Redis key prefix used for deduplication entries.
     */
    @Value("${ovsx.download-count.validation.key-prefix:download:dedup}")
    private String keyPrefix;

    /**
     * Extra hours added to the Redis TTL beyond the dedup window when event-time bucketing is on.
     * Covers late log delivery so out-of-order events still dedup correctly.
     *
     * Default: {@code 24}
     */
    @Value("${ovsx.download-count.validation.late-arrival-hours:2}")
    private int lateArrivalHours;

    /**
     * User-Agent substrings treated as automated clients.
     */
    @Value("${ovsx.download-count.validation.automated-client-keywords:}")
    private String automatedClientKeywordsValue;

    private List<String> automatedClientKeywords = new ArrayList<>();

    /**
     * Validates the configuration at startup.
     * Fails fast with a clear message rather than letting bad config cause
     * subtle runtime bugs (e.g. negative TTLs, ineffective late-arrival buffer).
     */
    @PostConstruct
    public void validate() {
        // hourly-limit-per-ip must be at least 1.
        if (hourlyLimitPerIp < 1) {
            throw new IllegalStateException(
                    "ovsx.download-count.validation.hourly-limit-per-ip must be >= 1, got: " + hourlyLimitPerIp);
        }

        // late-arrival-hours must be non-negative.
        if (lateArrivalHours < 0) {
            throw new IllegalStateException(
                    "ovsx.download-count.validation.late-arrival-hours must be >= 0, got: " + lateArrivalHours);
        }

        // The late-arrival buffer should be at least 1 hour so that log entries
        // arriving after the top of the hour still dedup against the correct bucket.
        if (lateArrivalHours < 1) {
            logger.warn("ovsx.download-count.validation.late-arrival-hours ({}) is less than 1 hour. "
                    + "Late CDN log entries may not deduplicate correctly against the hourly bucket.",
                    lateArrivalHours);
        }

        // key-prefix must not be blank — an empty prefix would produce malformed Redis keys.
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException(
                    "ovsx.download-count.validation.key-prefix must not be blank");
        }
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHourlyLimitPerIp() {
        return hourlyLimitPerIp;
    }

    public void setHourlyLimitPerIp(int hourlyLimitPerIp) {
        this.hourlyLimitPerIp = hourlyLimitPerIp;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getLateArrivalHours() {
        return lateArrivalHours;
    }

    public void setLateArrivalHours(int lateArrivalHours) {
        this.lateArrivalHours = lateArrivalHours;
    }

    public List<String> getAutomatedClientKeywords() {
        if (automatedClientKeywords.isEmpty() && automatedClientKeywordsValue != null) {
            String normalized = automatedClientKeywordsValue
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .replace("'", "");
            automatedClientKeywords = Arrays.stream(normalized.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        return automatedClientKeywords;
    }

    public void setAutomatedClientKeywords(List<String> automatedClientKeywords) {
        this.automatedClientKeywords = automatedClientKeywords;
    }

}
