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
package org.eclipse.openvsx.consistency;

import java.time.ZoneId;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;

/**
 * Runs every registered {@link ConsistencyCheck} once a day and records the result, so #1622's
 * "on a constant basis" is covered without an admin needing to remember to open the dashboard.
 */
@Component
public class ScheduleConsistencyCheckJobs {

    private static final String JOB_ID = "consistency-check";

    private final JobRequestScheduler scheduler;

    public ScheduleConsistencyCheckJobs(JobRequestScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        scheduler.scheduleRecurrently(
                JOB_ID,
                Cron.daily(3),
                ZoneId.of("UTC"),
                new HandlerJobRequest<>(ConsistencyCheckJobRequestHandler.class));
    }
}
