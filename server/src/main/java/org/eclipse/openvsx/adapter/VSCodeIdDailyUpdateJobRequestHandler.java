/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.adapter;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.settings.ReadOnlySetting;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "false", matchIfMissing = true)
public class VSCodeIdDailyUpdateJobRequestHandler implements JobRequestHandler<JobRequest> {

    private final ReadOnlySetting readOnly;
    private final VSCodeIdUpdateService service;

    public VSCodeIdDailyUpdateJobRequestHandler(ReadOnlySetting readOnly, VSCodeIdUpdateService service) {
        this.readOnly = readOnly;
        this.service = service;
    }

    @Override
    @Job(name = "VS Code ID daily update", retries = 0)
    public void run(JobRequest request) throws Exception {
        if (readOnly.isEnabled()) {
            return;
        }

        service.updateAll();
    }
}
