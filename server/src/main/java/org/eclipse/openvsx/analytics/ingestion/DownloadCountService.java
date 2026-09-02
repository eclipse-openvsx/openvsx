/********************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion;

import java.time.ZoneId;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.analytics.ingestion.aws.AwsDownloadCountHandler;
import org.eclipse.openvsx.analytics.ingestion.azure.AzureDownloadCountHandler;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.migration.HandlerJobRequest;

/**
 * A utility service to determine whether an optimized download count service
 * is available for a specific {@link FileResource}.
 */
@Service
public class DownloadCountService {

    protected final Logger logger = LoggerFactory.getLogger(DownloadCountService.class);

    private final JobRequestScheduler scheduler;
    private final AwsDownloadCountHandler awsDownloadCountHandler;
    private final AzureDownloadCountHandler azureDownloadCountHandler;

    public DownloadCountService(
            JobRequestScheduler scheduler,
            AwsDownloadCountHandler awsDownloadCountHandler,
            AzureDownloadCountHandler azureDownloadCountHandler
    ) {
        this.scheduler = scheduler;
        this.awsDownloadCountHandler = awsDownloadCountHandler;
        this.azureDownloadCountHandler = azureDownloadCountHandler;
    }

    /**
     * Returns whether an optimized download count service is enabled for the given {@link FileResource}.
     *
     * @param resource the {@link FileResource} to check
     * @return {@code true} if an optimized download count service is available, {@code false} otherwise
     */
    public boolean isEnabled(FileResource resource) {
        return switch (resource.getStorageType()) {
            case FileResource.STORAGE_AWS -> awsDownloadCountHandler.isEnabled();
            case FileResource.STORAGE_AZURE -> azureDownloadCountHandler.isEnabled();
            default -> false;
        };
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        if (awsDownloadCountHandler.isEnabled()) {
            logger.info(
                    "Scheduling AWS download count handler with cron '{}'",
                    awsDownloadCountHandler.getCronSchedule());
            scheduler.scheduleRecurrently(
                    awsDownloadCountHandler.getRecurringJobId(),
                    awsDownloadCountHandler.getCronSchedule(),
                    ZoneId.of("UTC"),
                    new HandlerJobRequest<>(AwsDownloadCountHandler.class));
        } else {
            scheduler.deleteRecurringJob(awsDownloadCountHandler.getRecurringJobId());
        }

        if (azureDownloadCountHandler.isEnabled()) {
            logger.info(
                    "Scheduling Azure download count handler with cron '{}'",
                    azureDownloadCountHandler.getCronSchedule());
            scheduler.scheduleRecurrently(
                    azureDownloadCountHandler.getRecurringJobId(),
                    azureDownloadCountHandler.getCronSchedule(),
                    ZoneId.of("UTC"),
                    new HandlerJobRequest<>(AzureDownloadCountHandler.class));
        } else {
            scheduler.deleteRecurringJob(azureDownloadCountHandler.getRecurringJobId());
        }
    }
}
