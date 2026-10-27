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

import java.util.Optional;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.settings.ReadOnlySetting;

@Component
public class CollectUsageStatsHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(CollectUsageStatsHandler.class);

    private final ReadOnlySetting readOnly;
    private final UsageStatsService usageStatsService;

    public CollectUsageStatsHandler(ReadOnlySetting readOnly, @Nullable UsageStatsService usageStatsService) {
        this.readOnly = readOnly;
        this.usageStatsService = usageStatsService;
    }

    @Override
    @Job(name = "Collect usage stats", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (readOnly.isEnabled()) {
            return;
        }

        if (usageStatsService != null) {
            logger.debug(">> Start collecting usage data");
            usageStatsService.persistUsageStats();
            logger.debug("<< Finished collecting usage data");
        }
    }
}
