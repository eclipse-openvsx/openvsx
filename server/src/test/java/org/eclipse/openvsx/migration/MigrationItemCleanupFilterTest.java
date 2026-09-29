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
package org.eclipse.openvsx.migration;

import java.util.List;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MigrationItemCleanupFilterTest {

    @Mock
    MigrationService migrations;

    @Test
    void onProcessingSucceeded_deletesMigrationItemBackedRequest() {
        var filter = new MigrationItemCleanupFilter(migrations);
        var request = new MigrationJobRequest<>(FixMissingFilesJobRequestHandler.class, 42L, 7L);
        var job = jobFor(request);

        filter.onProcessingSucceeded(job);

        verify(migrations).deleteMigrationItem(7L);
    }

    @Test
    void onProcessingSucceeded_leavesNonMigrationItemBackedRequestAlone() {
        var filter = new MigrationItemCleanupFilter(migrations);
        // GenerateKeyPairJobRequestHandler-style: no migration_item row backs this job.
        var request = new MigrationJobRequest<>(FixMissingFilesJobRequestHandler.class, 42L);
        var job = jobFor(request);

        filter.onProcessingSucceeded(job);

        verify(migrations, never()).deleteMigrationItem(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void onFailedAfterRetries_doesNotDeleteMigrationItem() {
        var filter = new MigrationItemCleanupFilter(migrations);
        var request = new MigrationJobRequest<>(FixMissingFilesJobRequestHandler.class, 42L, 7L);
        var job = jobFor(request);

        filter.onFailedAfterRetries(job);

        verify(migrations, never()).deleteMigrationItem(org.mockito.ArgumentMatchers.anyLong());
    }

    private Job jobFor(MigrationJobRequest<?> request) {
        var jobDetails = new JobDetails(
                "org.eclipse.openvsx.migration.MigrationItemJobRequestHandler",
                null,
                "run",
                List.of(new JobParameter(request)));

        return new Job(jobDetails);
    }
}
