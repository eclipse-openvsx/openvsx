/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.mirror.MirrorConfig;

@Component
public class MigrationScheduler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final static String SCHEDULE_MIGRATION_ITEMS_JOB = "schedule-migration-items";

    private final Logger logger = LoggerFactory.getLogger(MigrationScheduler.class);

    private final OrphanNamespaceMigration orphanNamespaceMigration;
    private final JobRequestScheduler scheduler;
    private final MirrorConfig mirrorConfig;

    // Default matches JobRunr's Cron.every15minutes(). Configurable so a deployment that's
    // bothered by migration-item bursts crowding out real-time jobs (see
    // MigrationItemJobRequestHandler) can spread them out further without a code change.
    @Value("${ovsx.migrations.cron:0 */15 * * * *}")
    String migrationItemsCron;

    public MigrationScheduler(
            OrphanNamespaceMigration orphanNamespaceMigration,
            JobRequestScheduler scheduler,
            MirrorConfig mirrorConfig
    ) {
        this.orphanNamespaceMigration = orphanNamespaceMigration;
        this.scheduler = scheduler;
        this.mirrorConfig = mirrorConfig;
    }

    @Override
    @Job(name = "Schedule migrations", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        orphanNamespaceMigration.fixOrphanNamespaces();
        if (!mirrorConfig.isEnabled()) {
            scheduler.enqueue(new HandlerJobRequest<>(GenerateKeyPairJobRequestHandler.class));
        }

        logger.info("Scheduling migration item job with schedule '{}'", this.migrationItemsCron);

        scheduler.scheduleRecurrently(
                SCHEDULE_MIGRATION_ITEMS_JOB,
                migrationItemsCron,
                new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));
    }

    public void deleteScheduleMigrationItemsJob() {
        scheduler.deleteRecurringJob(SCHEDULE_MIGRATION_ITEMS_JOB);
    }
}
