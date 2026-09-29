/******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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

import java.time.Instant;

import jakarta.annotation.PostConstruct;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.SettingsService;

@Component
public class MigrationItemJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    // How far apart consecutive items in a batch are spread out, so the batch doesn't all become
    // due at the exact same instant -- see MigrationService.enqueueMigration for why that matters.
    // Not configurable: it only controls how gently a single batch trickles in, whereas batchSize
    // and the recurring schedule (see MigrationScheduler) are what actually determine throughput.
    private static final long STAGGER_MILLIS = 200;

    protected final Logger logger = LoggerFactory.getLogger(MigrationItemJobRequestHandler.class);

    private final SettingsService settings;
    private final RepositoryService repositories;
    private final MigrationService migrations;
    private final MigrationScheduler scheduler;

    // Default of 200 keeps a batch's worst-case spread (batchSize * STAGGER_MILLIS = 40s) comfortably
    // inside the default 15-minute recurring interval, while still bounding how many migration jobs
    // can ever be due ahead of a real, user-triggered job at once -- down from the previous 25000.
    @Value("${ovsx.migrations.batch-size:200}")
    int batchSize;

    public MigrationItemJobRequestHandler(
            SettingsService settings,
            RepositoryService repositories,
            MigrationService migrations,
            MigrationScheduler scheduler
    ) {
        this.settings = settings;
        this.repositories = repositories;
        this.migrations = migrations;
        this.scheduler = scheduler;
    }

    // PageRequest.ofSize(...) throws IllegalArgumentException for a size below 1, which would make
    // the recurring job fail on every single run for a misconfigured ovsx.migrations.batch-size --
    // clamp instead, so a bad value degrades to "very slow" rather than "never runs at all".
    @PostConstruct
    void validateBatchSize() {
        if (batchSize < 1) {
            logger.warn("ovsx.migrations.batch-size must be at least 1, but was {} -- using 1 instead", batchSize);
            batchSize = 1;
        }
    }

    @Override
    @Job(name = "Migration item processing", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (settings.isReadOnly()) {
            return;
        }

        var items = repositories.findNotMigratedItems(PageRequest.ofSize(batchSize));
        var now = Instant.now();
        var index = 0;
        for (var item : items) {
            migrations.scheduleMigration(item, now.plusMillis(index++ * STAGGER_MILLIS));
        }

        if (items.getNumberOfElements() > 0) {
            // With a small batchSize, draining a large backlog now takes many recurring runs instead
            // of one -- log how much is left after this batch so that's visible across all of them,
            // not just a repeated "scheduled N items" with no sense of overall progress until the
            // last run. Skipped entirely when nothing was found: an empty slice means hasNext() is
            // already false too (see below), so there's nothing to report and no remaining count
            // worth an extra query for -- it can only be zero.
            var remaining = repositories.countNotMigratedItems();
            logger.info("Scheduled {} migration items ({} remaining)", items.getNumberOfElements(), remaining);
        }

        if (!items.hasNext()) {
            logger.info("Migration completed, deleting recurring job");
            scheduler.deleteScheduleMigrationItemsJob();
        }
    }
}
