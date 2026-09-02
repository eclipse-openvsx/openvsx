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
package org.eclipse.openvsx.analytics.ingestion.jobs;

import java.time.ZoneId;
import java.util.List;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionRunner;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.entities.FileResource;

/**
 * The recurring download log ingestion job. On startup it registers one recurring job per configured
 * source, and deletes the job of any storage type whose source is absent or disabled, so JobRunr
 * never fires a job it cannot serve. JobRunr then invokes {@link #run} on a single node per tick with
 * the storage type to ingest, which is handed to the {@link DownloadIngestionRunner}. Storage-agnostic,
 * so one job serves every source.
 */
@Component
public class LogIngestionJob implements JobRequestHandler<IngestionJobRequest<?>> {

    private static final List<String> KNOWN_STORAGE_TYPES = List
            .of(FileResource.STORAGE_AWS, FileResource.STORAGE_AZURE);

    protected final Logger logger = LoggerFactory.getLogger(LogIngestionJob.class);

    private final ObjectProvider<DownloadRecordSource> sources;
    private final DownloadIngestionRunner runner;
    private final JobRequestScheduler scheduler;

    public LogIngestionJob(
            ObjectProvider<DownloadRecordSource> sources,
            DownloadIngestionRunner runner,
            JobRequestScheduler scheduler
    ) {
        this.sources = sources;
        this.runner = runner;
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        for (var storageType : KNOWN_STORAGE_TYPES) {
            var jobId = recurringJobId(storageType);
            var source = findEnabledSource(storageType);
            if (source == null) {
                scheduler.deleteRecurringJob(jobId);
            } else {
                logger.info("Scheduling {} log ingestion with cron '{}'", storageType, source.getCronSchedule());
                scheduler.scheduleRecurrently(
                        jobId,
                        source.getCronSchedule(),
                        ZoneId.of("UTC"),
                        new IngestionJobRequest<>(LogIngestionJob.class, storageType));
            }
        }
    }

    @Override
    @Job(name = "Ingest download logs", retries = 0)
    public void run(IngestionJobRequest<?> jobRequest) {
        var source = findEnabledSource(jobRequest.getStorageType());
        if (source == null) {
            logger.warn(
                    "no enabled download record source for storage type {}, skipping",
                    jobRequest.getStorageType());
            return;
        }

        runner.run(source);
    }

    private DownloadRecordSource findEnabledSource(String storageType) {
        return sources.stream()
                .filter(source -> source.getStorageType().equals(storageType) && source.isEnabled())
                .findFirst()
                .orElse(null);
    }

    private String recurringJobId(String storageType) {
        return "update-" + storageType + "-download-counts";
    }
}
