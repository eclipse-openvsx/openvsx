/********************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.admin;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.settings.SettingsService;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UUIDService;

@Component
public class MonthlyAdminStatisticsJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final SettingsService settings;
    private final JobRequestScheduler scheduler;
    private final UUIDService uuidService;

    public MonthlyAdminStatisticsJobRequestHandler(
            SettingsService settings,
            JobRequestScheduler scheduler,
            UUIDService uuidService
    ) {
        this.settings = settings;
        this.scheduler = scheduler;
        this.uuidService = uuidService;
    }

    @Override
    @Job(name = "Monthly admin statistics update", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (settings.isReadOnly()) {
            return;
        }

        var lastMonth = TimeUtil.getCurrentUTC().minusMonths(1);
        var year = lastMonth.getYear();
        var month = lastMonth.getMonthValue();

        var jobIdText = "AdminStatistics::year=" + year + ",month=" + month;
        var jobId = uuidService.generateFromName(jobIdText);
        scheduler.enqueue(jobId, new AdminStatisticsJobRequest(year, month));
    }
}
