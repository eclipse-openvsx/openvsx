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
package org.eclipse.openvsx.admin;

import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminStatisticsJobRequestHandler implements JobRequestHandler<AdminStatisticsJobRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminStatisticsJobRequestHandler.class);

    private final AdminStatisticsService service;

    public AdminStatisticsJobRequestHandler(AdminStatisticsService service) {
        this.service = service;
    }

    @Override
    public void run(AdminStatisticsJobRequest jobRequest) throws Exception {
        var statistics = service.computeAdminStatistics(jobRequest.getYear(), jobRequest.getMonth());
        service.saveAdminStatistics(statistics);
    }
}
