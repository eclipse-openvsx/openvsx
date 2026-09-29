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
package org.eclipse.openvsx.ratelimit.jobs;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.settings.SettingsService;

@Component
public class CalculateDailyUsageStatsHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(CalculateDailyUsageStatsHandler.class);

    private final SettingsService settings;
    private final @Nullable UsageStatsService usageStatsService;

    public CalculateDailyUsageStatsHandler(
            SettingsService settings,
            @Nullable UsageStatsService usageStatsService
    ) {
        this.settings = settings;
        this.usageStatsService = usageStatsService;
    }

    @Override
    @Job(name = "Calculate daily usage stats", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (settings.isReadOnly()) {
            return;
        }

        if (usageStatsService != null) {
            logger.info(">> Start calculating daily usage data");
            usageStatsService.calculateDailyUsageStats();
            logger.info("<< Finished calculating daily usage data");
        }
    }
}
