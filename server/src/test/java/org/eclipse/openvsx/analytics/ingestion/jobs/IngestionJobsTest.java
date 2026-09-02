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

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;
import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionRunner;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.analytics.ingestion.aws.AwsDownloadRecordSource;
import org.eclipse.openvsx.analytics.ingestion.azure.AzureDownloadRecordSource;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.storage.AwsStorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionJobsTest {

    private static final String AWS_JOB_ID = "update-aws-download-counts";
    private static final String AZURE_JOB_ID = "update-azure-blob-download-counts";

    private final AwsStorageService awsStorage = Mockito.mock(AwsStorageService.class);
    private final DownloadIngestionRunner ingestionRunner = Mockito.mock(DownloadIngestionRunner.class);
    private final JobRequestScheduler scheduler = Mockito.mock(JobRequestScheduler.class);
    private final DownloadIngestionMetrics metrics = Mockito.mock(DownloadIngestionMetrics.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(AwsStorageService.class, () -> awsStorage)
                .withBean(DownloadIngestionMetrics.class, () -> metrics)
                .withBean(DownloadIngestionRunner.class, () -> ingestionRunner)
                .withBean(JobRequestScheduler.class, () -> scheduler)
                .withUserConfiguration(
                        AwsDownloadRecordSource.class,
                        AzureDownloadRecordSource.class,
                        LogIngestionJob.class);
    }

    @Test
    void testNoSourceBeansWithoutConfiguration() {
        runner().run(context -> assertThat(context).doesNotHaveBean(DownloadRecordSource.class));
    }

    @Test
    void testAwsSourceExistsWhenBucketIsConfigured() {
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            assertThat(context).hasSingleBean(AwsDownloadRecordSource.class);
            assertThat(context).doesNotHaveBean(AzureDownloadRecordSource.class);
        });
    }

    @Test
    void testAzureSourceExistsWhenLogsEndpointIsConfigured() {
        runner().withPropertyValues("ovsx.logs.azure.service-endpoint=https://logs.blob.core.windows.net")
                .run(context -> {
                    assertThat(context).hasSingleBean(AzureDownloadRecordSource.class);
                    assertThat(context).doesNotHaveBean(AwsDownloadRecordSource.class);
                });
    }

    @Test
    void testAwsSourceCoversAwsDownloadsOnly() {
        when(awsStorage.isEnabled()).thenReturn(true);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            var source = context.getBean(DownloadRecordSource.class);
            assertTrue(source.covers(resource(FileResource.STORAGE_AWS)));
            assertFalse(source.covers(resource(FileResource.STORAGE_AZURE)));
            assertFalse(source.covers(resource(FileResource.STORAGE_LOCAL)));
        });
    }

    @Test
    void testAwsSourceDoesNotCoverWhenStorageServiceIsDisabled() {
        when(awsStorage.isEnabled()).thenReturn(false);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            var source = context.getBean(DownloadRecordSource.class);
            assertFalse(source.covers(resource(FileResource.STORAGE_AWS)));
        });
    }

    @Test
    void testSchedulesRecurringJobForEnabledSource() {
        when(awsStorage.isEnabled()).thenReturn(true);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            context.getBean(LogIngestionJob.class).scheduleJobs(null);
            verify(scheduler).scheduleRecurrently(
                    eq(AWS_JOB_ID),
                    eq("0 10 * * * *"),
                    eq(ZoneId.of("UTC")),
                    any(JobRequest.class));
            // the unconfigured azure job is cleaned up in the same pass
            verify(scheduler).deleteRecurringJob(AZURE_JOB_ID);
        });
    }

    @Test
    void testDeletesRecurringJobWhenSourceIsDisabled() {
        when(awsStorage.isEnabled()).thenReturn(false);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            context.getBean(LogIngestionJob.class).scheduleJobs(null);
            verify(scheduler).deleteRecurringJob(AWS_JOB_ID);
        });
    }

    @Test
    void testDeletesAllRecurringJobsWhenNoSourceConfigured() {
        runner().run(context -> {
            context.getBean(LogIngestionJob.class).scheduleJobs(null);
            verify(scheduler).deleteRecurringJob(AWS_JOB_ID);
            verify(scheduler).deleteRecurringJob(AZURE_JOB_ID);
        });
    }

    @Test
    void testHandlerRunsTheResolvedSourceThroughTheRunner() {
        when(awsStorage.isEnabled()).thenReturn(true);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            context.getBean(LogIngestionJob.class)
                    .run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));
            verify(ingestionRunner).run(context.getBean(AwsDownloadRecordSource.class));
        });
    }

    @Test
    void testHandlerSkipsWhenSourceIsDisabled() {
        when(awsStorage.isEnabled()).thenReturn(false);
        runner().withPropertyValues("ovsx.logs.aws.bucket=my-logs").run(context -> {
            context.getBean(LogIngestionJob.class)
                    .run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));
            verify(ingestionRunner, Mockito.never()).run(any());
        });
    }

    private FileResource resource(String storageType) {
        var resource = new FileResource();
        resource.setStorageType(storageType);
        return resource;
    }
}
