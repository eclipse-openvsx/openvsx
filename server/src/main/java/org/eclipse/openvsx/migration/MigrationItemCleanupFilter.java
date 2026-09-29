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

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.JobServerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes the {@link org.eclipse.openvsx.entities.MigrationItem} row backing a migration job once
 * that job has actually finished successfully, instead of leaving completed rows in the table
 * forever (see <a href="https://github.com/eclipse-openvsx/openvsx/issues/1588">issue #1588</a>).
 * Deletion doesn't live at the end of every {@code *JobRequestHandler} because there would be a
 * lot of them to touch and to keep touching for every future migration -- a single JobRunr filter
 * that inspects the {@link MigrationJobRequest} on state transitions covers all of them at once,
 * present and future.
 * <p>
 * On failure (even after retries are exhausted) the row is deliberately left in place: the next
 * {@link MigrationScheduler} run will pick it up again via
 * {@code MigrationItemRepository#findByMigrationScheduledFalseOrderById}... except
 * {@code migrationScheduled} was already flipped to {@code true} in
 * {@link MigrationService#scheduleMigration}, so a permanently-failing item currently just sits
 * there for an operator to notice and investigate, the same as before this change. Fixing that
 * retry gap is out of scope for this sketch.
 */
@Component
public class MigrationItemCleanupFilter implements JobServerFilter {

    protected final Logger logger = LoggerFactory.getLogger(MigrationItemCleanupFilter.class);

    private final MigrationService migrations;

    public MigrationItemCleanupFilter(MigrationService migrations) {
        this.migrations = migrations;
    }

    @Override
    public void onProcessingSucceeded(Job job) {
        var migrationItemId = getMigrationItemId(job);
        if (migrationItemId != null) {
            migrations.deleteMigrationItem(migrationItemId);
        }
    }

    @Override
    public void onFailedAfterRetries(Job job) {
        var migrationItemId = getMigrationItemId(job);
        if (migrationItemId != null) {
            logger.warn(
                    "Migration item {} failed after all retries, leaving it in the database for investigation",
                    migrationItemId);
        }
    }

    private Long getMigrationItemId(Job job) {
        for (var value : job.getJobDetails().getJobParameterValues()) {
            if (value instanceof MigrationJobRequest<?> request && request.getMigrationItemId() > 0) {
                return request.getMigrationItemId();
            }
        }

        return null;
    }
}
