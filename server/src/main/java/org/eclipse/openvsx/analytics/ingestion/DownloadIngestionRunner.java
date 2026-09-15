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

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.settings.SettingsService;

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

    private final SettingsService settings;
    private final DownloadIngestionProcessor processor;

    public DownloadIngestionRunner(SettingsService settings, DownloadIngestionProcessor processor) {
        this.settings = settings;
        this.processor = processor;
    }

    public void run(DownloadRecordSource source) {
        var storageType = source.getStorageType();
        if (settings.isReadOnly()) {
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
                for (var name : itemsToProcess(source, batches.next())) {
                    var processedOn = LocalDateTime.now();

                    if (processedOn.isAfter(maxExecutionTime)) {
                        logger.info(
                                "could not ingest all {} items within the time budget, the rest is picked up by the next run",
                                storageType);
                        break batches;
                    }

                    if (settings.isReadOnly()) {
                        logger.info("registry is in read-only mode, stopping {} ingestion", storageType);
                        break batches;
                    }

                    var success = false;
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
                            processor.saveEvents(processed.events());
                        } catch (Exception e) {
                            logger.error("failed to process item: {}", name, e);
                        }
                    }

                    if (success) {
                        source.finish(name);
                    } else {
                        processor.persistIngestion(name, storageType, processedOn, executionTime, false);
                    }
                }
            }
        } finally {
            // evict caches and update search entries for all updated extensions
            allUpdatedExtensions.values().forEach(processor::evictCaches);
            processor.updateSearchEntries(allUpdatedExtensions.values().stream().toList());
        }

        logger.info("<< ingesting downloads from {}", storageType);
    }

    /**
     * Removes already-processed items (cleaning them up along the way) and previously failed
     * ones (kept for analysis) from a batch.
     */
    private List<String> itemsToProcess(DownloadRecordSource source, List<String> batch) {
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

        return names;
    }
}
