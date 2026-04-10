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

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CollectUsageStatsHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(CollectUsageStatsHandler.class);

    private UsageStatsService usageStatsService;

    public CollectUsageStatsHandler(Optional<UsageStatsService> usageStatsService) {
        usageStatsService.ifPresent(service -> this.usageStatsService = service);
    }

    @Override
    @Job(name = "Collect usage stats", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (usageStatsService != null) {
            logger.debug(">> Start collecting usage data");
            usageStatsService.persistUsageStats();
            logger.debug("<< Finished collecting usage data");
        }
    }
}
