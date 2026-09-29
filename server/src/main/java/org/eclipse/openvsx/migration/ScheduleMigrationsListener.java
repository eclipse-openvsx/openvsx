/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.migration;

import java.time.Instant;
import java.util.UUID;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.util.UUIDService;

@Component
public class ScheduleMigrationsListener {
    protected final Logger logger = LoggerFactory.getLogger(ScheduleMigrationsListener.class);

    @Value("${ovsx.migrations.delay.seconds:0}")
    long delay;

    @Value("${ovsx.migrations.once-per-version:false}")
    boolean runMigrationsOncePerVersion;

    @Value("${ovsx.registry.version:}")
    String registryVersion;

    private final JobRequestScheduler scheduler;
    private final UUIDService uuidService;

    public ScheduleMigrationsListener(JobRequestScheduler scheduler, UUIDService uuidService) {
        this.scheduler = scheduler;
        this.uuidService = uuidService;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        UUID jobId = null;
        if (runMigrationsOncePerVersion) {
            var jobIdText = "MigrationScheduler::" + registryVersion;
            jobId = uuidService.generateFromName(jobIdText);
        }

        var instant = Instant.now().plusSeconds(delay);
        scheduler.schedule(jobId, instant, new HandlerJobRequest<>(MigrationScheduler.class));
    }
}
