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
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.ratelimit.edge.EdgeUsageService;

@Component
public class UnblockEdgeCustomersHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final @Nullable EdgeUsageService edgeUsageService;

    public UnblockEdgeCustomersHandler(@Nullable EdgeUsageService edgeUsageService) {
        this.edgeUsageService = edgeUsageService;
    }

    @Override
    @Job(name = "Unblock edge customers", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (edgeUsageService != null) {
            edgeUsageService.unblockRefilled();
        }
    }
}