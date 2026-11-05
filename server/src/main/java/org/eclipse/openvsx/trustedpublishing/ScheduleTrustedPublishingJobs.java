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
package org.eclipse.openvsx.trustedpublishing;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;

@Component
public class ScheduleTrustedPublishingJobs {

    private static final String EXPIRE_PENDING_JOB = "trusted-publishing-expire-pending";
    // shortcut: fixed, make it a trusted-publishing setting when a deployment needs another schedule
    private static final String EXPIRE_PENDING_SCHEDULE = "0 0 3 * * *";

    private final TrustedPublishingConfig config;
    private final JobRequestScheduler scheduler;

    public ScheduleTrustedPublishingJobs(TrustedPublishingConfig config, JobRequestScheduler scheduler) {
        this.config = config;
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        if (config.isEnabled()) {
            scheduler.scheduleRecurrently(
                    EXPIRE_PENDING_JOB,
                    EXPIRE_PENDING_SCHEDULE,
                    new HandlerJobRequest<>(ExpirePendingRegistrationsHandler.class));
        } else {
            scheduler.deleteRecurringJob(EXPIRE_PENDING_JOB);
        }
    }
}
