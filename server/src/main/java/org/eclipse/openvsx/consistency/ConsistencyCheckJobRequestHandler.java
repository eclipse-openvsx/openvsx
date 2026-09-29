/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.consistency;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;

@Component
public class ConsistencyCheckJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final ConsistencyCheckService service;

    public ConsistencyCheckJobRequestHandler(ConsistencyCheckService service) {
        this.service = service;
    }

    @Override
    @Job(name = "Run data consistency checks", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) {
        service.runAllChecks();
    }
}
