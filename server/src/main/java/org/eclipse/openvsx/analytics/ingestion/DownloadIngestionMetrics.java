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
package org.eclipse.openvsx.analytics.ingestion;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.repositories.RepositoryService;

/**
 * Operational metrics of the download ingestion pipeline: parse skip rate, load volume,
 * extract lag, dead-letter depth and log files pending analytics.
 */
@Component
public class DownloadIngestionMetrics {

    public static final String LINES_METRIC = "openvsx_analytics_log_lines_total";
    public static final String SKIPPED_LINES_METRIC = "openvsx_analytics_log_lines_skipped_total";
    public static final String EVENTS_METRIC = "openvsx_analytics_events_loaded_total";
    public static final String DOWNLOADS_METRIC = "openvsx_analytics_downloads_loaded_total";
    public static final String FAILED_EVENTS_METRIC = "openvsx_analytics_events_failed_total";
    public static final String EXTRACT_LAG_METRIC = "openvsx_analytics_extract_lag";
    public static final String DEAD_LETTER_METRIC = "openvsx_analytics_dead_letter_depth";
    public static final String PENDING_FILES_METRIC = "openvsx_analytics_pending_files";

    private static final Logger logger = LoggerFactory.getLogger(DownloadIngestionMetrics.class);

    private final Counter lines;
    private final Counter skippedLines;
    private final Counter events;
    private final Counter downloads;
    private final Counter failedEvents;
    private final Timer extractLag;

    public DownloadIngestionMetrics(MeterRegistry registry, RepositoryService repositories) {
        this.lines = Counter.builder(LINES_METRIC)
                .description("Access log lines read by the download ingestion pipeline")
                .register(registry);
        this.skippedLines = Counter.builder(SKIPPED_LINES_METRIC)
                .description("Access log lines skipped as malformed")
                .register(registry);
        this.events = Counter.builder(EVENTS_METRIC)
                .description("Aggregated download events loaded into the analytics store")
                .register(registry);
        this.downloads = Counter.builder(DOWNLOADS_METRIC)
                .description("Downloads counted by the ingestion pipeline")
                .register(registry);
        this.failedEvents = Counter.builder(FAILED_EVENTS_METRIC)
                .description("Aggregated download events that could not be written to the analytics store")
                .register(registry);
        this.extractLag = Timer.builder(EXTRACT_LAG_METRIC)
                .description("Delay between a download and its ingestion from access logs")
                .register(registry);

        // scraped frequently, so the underlying count query is memoized for a minute
        LoadingCache<Boolean, Long> deadLetterDepth = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(_ -> countFailedItems(repositories));
        Gauge.builder(DEAD_LETTER_METRIC, () -> deadLetterDepth.get(Boolean.TRUE))
                .description("Number of log files that failed processing and await manual attention")
                .register(registry);

        LoadingCache<Boolean, Long> pendingFiles = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(_ -> countPendingFiles(repositories));
        Gauge.builder(PENDING_FILES_METRIC, () -> pendingFiles.get(Boolean.TRUE))
                .description(
                        "Number of ingested log files kept until their events can be stored in the analytics store")
                .register(registry);
    }

    private long countPendingFiles(RepositoryService repositories) {
        try {
            return repositories.countPendingAnalyticsDownloadIngestions();
        } catch (Exception e) {
            logger.warn("could not determine the number of log files pending analytics", e);
            return -1;
        }
    }

    private long countFailedItems(RepositoryService repositories) {
        try {
            return repositories.countFailedDownloadIngestions();
        } catch (Exception e) {
            logger.warn("could not determine dead-letter depth", e);
            return -1;
        }
    }

    public void recordParsedLines(int total, int skipped) {
        lines.increment(total);
        skippedLines.increment(skipped);
    }

    public void recordLoaded(int eventCount, int downloadCount) {
        events.increment(eventCount);
        downloads.increment(downloadCount);
    }

    public void recordFailedEvents(int eventCount) {
        failedEvents.increment(eventCount);
    }

    public void recordExtractLag(Duration lag) {
        if (!lag.isNegative()) {
            extractLag.record(lag);
        }
    }
}
