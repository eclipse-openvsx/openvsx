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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import org.eclipse.openvsx.analytics.AnalyticsUnavailableException;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.settings.ReadOnlySetting;

/**
 * Drives the ingestion of one {@link DownloadRecordSource}: skips already-processed and
 * previously failed items (idempotency and dead-lettering via the ingestion entries), reads and
 * processes the remaining items within a time budget, cleans up successful ones and records
 * failures.
 */
@Component
public class DownloadIngestionRunner {

    private static final int TIME_BUDGET_MINUTES = 50;

    protected final Logger logger = LoggerFactory.getLogger(DownloadIngestionRunner.class);

    private final ReadOnlySetting readOnly;
    private final DownloadIngestionProcessor processor;

    public DownloadIngestionRunner(ReadOnlySetting readOnly, DownloadIngestionProcessor processor) {
        this.readOnly = readOnly;
        this.processor = processor;
    }

    public void run(DownloadRecordSource source) {
        var storageType = source.getStorageType();
        if (readOnly.isEnabled()) {
            logger.info("registry is in read-only mode, skipping {} ingestion", storageType);
            return;
        }

        logger.info(">> ingesting downloads from {}", storageType);
        var maxExecutionTime = LocalDateTime.now().plusMinutes(TIME_BUDGET_MINUTES);
        var stopWatch = new StopWatch();
        var allUpdatedExtensions = new HashMap<Long, Extension>();

        try {
            var batches = source.listBatches();
            batches : while (batches.hasNext()) {
                var items = itemsToProcess(source, batches.next());
                if (!replayPendingAnalytics(source, items.pendingAnalytics(), maxExecutionTime)) {
                    break batches;
                }

                for (var name : items.toProcess()) {
                    var processedOn = LocalDateTime.now();

                    if (processedOn.isAfter(maxExecutionTime)) {
                        logger.info(
                                "could not ingest all {} items within the time budget, the rest is picked up by the next run",
                                storageType);
                        break batches;
                    }

                    if (readOnly.isEnabled()) {
                        logger.info("registry is in read-only mode, stopping {} ingestion", storageType);
                        break batches;
                    }

                    var success = false;
                    var analyticsSettled = false;
                    stopWatch.start();
                    List<RawDownloadRecord> records = null;
                    try {
                        records = source.read(name);
                    } catch (Exception e) {
                        logger.error("failed to read item: {}", name, e);
                    } finally {
                        stopWatch.stop();
                    }

                    var executionTime = (int) stopWatch.lastTaskInfo().getTimeMillis();
                    if (records != null) {
                        try {
                            // increments download counters and writes the download ingestion entry
                            // in one transaction
                            var processed = processor
                                    .process(storageType, name, processedOn, executionTime, records);
                            processed.extensions()
                                    .forEach(extension -> allUpdatedExtensions.put(extension.getId(), extension));
                            success = true;
                            // and only then hands the events to the analytics database, which
                            // cannot join that transaction
                            if (processor.saveEvents(processed.events())) {
                                processor.markAnalyticsDone(storageType, name);
                                analyticsSettled = true;
                            }
                        } catch (Exception e) {
                            logger.error("failed to process item: {}", name, e);
                        }
                    }

                    if (success) {
                        if (analyticsSettled) {
                            source.finish(name);
                        } else {
                            // its counters are updated, but its events are not stored: it stays, marked
                            // pending, for a later run to store them once the analytics database is back
                            logger.info("keeping {} until its analytics events are stored", name);
                        }
                    } else {
                        processor.persistIngestion(name, storageType, processedOn, executionTime, false);
                    }
                }
            }
        } finally {
            // no cache eviction: each one is a full Redis keyspace SCAN, and the counts expire with the TTL
            processor.updateSearchEntries(allUpdatedExtensions.values().stream().toList());
        }

        logger.info("<< ingesting downloads from {}", storageType);
    }

    /**
     * The items of a batch to ingest, and those already ingested whose analytics events still have
     * to be stored.
     */
    private record Items(List<String> toProcess, List<String> pendingAnalytics) {}

    /**
     * Removes already-processed items (cleaning them up along the way) and previously failed
     * ones (kept for analysis) from a batch. Items waiting for their analytics events are set aside:
     * their counters are updated already, so they must not be ingested again.
     */
    private Items itemsToProcess(DownloadRecordSource source, List<String> batch) {
        var names = new ArrayList<>(batch);

        var succeeded = processor.succeededIngestions(source.getStorageType(), names);
        succeeded.forEach(source::finish);
        if (!succeeded.isEmpty()) {
            logger.info("cleaning up already ingested items:");
            succeeded.forEach(item -> logger.info("  - {}", item));
        }
        names.removeAll(succeeded);

        var failed = processor.failedIngestions(source.getStorageType(), names);
        if (!failed.isEmpty()) {
            logger.info("skipping previously failed items:");
            failed.forEach(item -> logger.info("  - {}", item));
        }
        names.removeAll(failed);

        var pending = processor.pendingAnalyticsIngestions(source.getStorageType(), names);
        names.removeAll(pending);

        return new Items(names, pending);
    }

    /**
     * Stores the analytics events of items whose counters were updated by an earlier run, then
     * releases them for cleanup. Only the events are written, never the counters.
     *
     * @return false when the time budget is used up or the registry went read-only, so the run stops
     */
    private boolean replayPendingAnalytics(
            DownloadRecordSource source,
            List<String> pending,
            LocalDateTime maxExecutionTime
    ) {
        if (pending.isEmpty()) {
            return true;
        }

        var storageType = source.getStorageType();
        // with analytics switched off since, there is nothing left to store: the files are only released
        var analyticsEnabled = processor.isAnalyticsEnabled();
        if (analyticsEnabled && !processor.isAnalyticsAvailable()) {
            logger.info(
                    "the analytics database is unavailable, keeping {} {} items pending",
                    pending.size(),
                    storageType);
            return true;
        }

        for (var name : pending) {
            if (LocalDateTime.now().isAfter(maxExecutionTime) || readOnly.isEnabled()) {
                return false;
            }

            try {
                if (analyticsEnabled) {
                    processor.backfill(storageType, source.read(name));
                }
                processor.markAnalyticsDone(storageType, name);
                source.finish(name);
                logger.info("released the pending analytics item {}", name);
            } catch (AnalyticsUnavailableException e) {
                logger.info("the analytics database became unavailable, the rest stays pending");
                return true;
            } catch (Exception e) {
                logger.error("failed to store the pending analytics events of item: {}", name, e);
            }
        }
        return true;
    }
}
