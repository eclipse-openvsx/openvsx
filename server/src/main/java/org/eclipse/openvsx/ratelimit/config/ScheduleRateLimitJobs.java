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
package org.eclipse.openvsx.ratelimit.config;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.ratelimit.jobs.CalculateDailyUsageStatsHandler;
import org.eclipse.openvsx.ratelimit.jobs.CollectUsageStatsHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ScheduleRateLimitJobs {

    protected final Logger logger = LoggerFactory.getLogger(ScheduleRateLimitJobs.class);

    private RateLimitProperties rateLimitProperties;
    private final JobRequestScheduler scheduler;

    public ScheduleRateLimitJobs(Optional<RateLimitProperties> rateLimitProperties, JobRequestScheduler scheduler) {
        rateLimitProperties.ifPresent(properties -> this.rateLimitProperties = properties);
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        if (rateLimitProperties != null) {
            var schedule = rateLimitProperties.getUsageStats().getJobSchedule();
            logger.info("Scheduling collect usage stats job with schedule '{}'", schedule);
            scheduler.scheduleRecurrently("collect-usage-stats", schedule, new HandlerJobRequest<>(CollectUsageStatsHandler.class));

            var dailyUsageStatsSchedule = rateLimitProperties.getUsageStats().getDailyJobSchedule();
            logger.info("Scheduling calculate daily usage stats job with schedule '{}'", dailyUsageStatsSchedule);
            scheduler.scheduleRecurrently(
                    "calculate-daily-usage-stats",
                    dailyUsageStatsSchedule,
                    new HandlerJobRequest<>(CalculateDailyUsageStatsHandler.class)
            );
        } else {
            scheduler.deleteRecurringJob("collect-usage-stats");
            scheduler.deleteRecurringJob("calculate-daily-usage-stats");
        }
    }
}
