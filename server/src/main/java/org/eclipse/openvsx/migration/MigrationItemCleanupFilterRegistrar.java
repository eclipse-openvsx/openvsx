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
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import org.jobrunr.server.BackgroundJobServer;
import org.springframework.stereotype.Component;

/**
 * Registers {@link MigrationItemCleanupFilter} with JobRunr's {@link BackgroundJobServer}.
 * <p>
 * JobRunr's Spring Boot starter ({@code JobRunrAutoConfiguration}) does not pick up
 * {@link org.jobrunr.jobs.filters.JobFilter} beans automatically -- confirmed by inspecting its
 * bean definitions, none of which take a {@code List<JobFilter>} or similar. Registration has to
 * be done explicitly against the server's {@code JobDefaultFilters}, and via
 * {@code addAll(...)} rather than {@code setJobFilters(...)} so JobRunr's own built-in filters
 * (e.g. {@code RetryFilter}) are appended to, not replaced.
 * <p>
 * Registration happens in {@link PostConstruct}, i.e. as soon as this bean (and therefore its
 * {@code Optional<BackgroundJobServer>} dependency) is constructed, rather than waiting for an
 * application-lifecycle event: {@code BackgroundJobServer} only starts actually processing jobs
 * once JobRunr's own {@code JobRunrStarter} calls {@code start()} on {@code ApplicationReadyEvent}
 * -- confirmed via javap that this is strictly later than bean construction -- but there's no
 * reason to rely on that ordering when registering earlier is just as easy and removes any doubt.
 * <p>
 * {@link BackgroundJobServer} is injected as {@link Optional} because it's only present when
 * {@code jobrunr.background-job-server.enabled=true} (e.g. it's absent on nodes that only
 * schedule jobs, not process them); there's nothing to register the filter on in that case.
 */
@Component
public class MigrationItemCleanupFilterRegistrar {

    private final Optional<BackgroundJobServer> backgroundJobServer;
    private final MigrationItemCleanupFilter migrationItemCleanupFilter;

    public MigrationItemCleanupFilterRegistrar(
            Optional<BackgroundJobServer> backgroundJobServer,
            MigrationItemCleanupFilter migrationItemCleanupFilter
    ) {
        this.backgroundJobServer = backgroundJobServer;
        this.migrationItemCleanupFilter = migrationItemCleanupFilter;
    }

    @PostConstruct
    public void registerFilter() {
        backgroundJobServer.ifPresent(server -> server.getJobFilters().addAll(List.of(migrationItemCleanupFilter)));
    }
}
