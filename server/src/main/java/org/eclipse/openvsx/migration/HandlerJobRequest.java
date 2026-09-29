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
package org.eclipse.openvsx.migration;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

public class HandlerJobRequest<T extends JobRequestHandler<?>> implements JobRequest {

    private Class<T> handler;

    // needed for serialization by jobrunr
    public HandlerJobRequest() {
    }

    public HandlerJobRequest(Class<T> handler) {
        this.handler = handler;
    }

    @Override
    public Class<? extends JobRequestHandler<?>> getJobRequestHandler() {
        return handler;
    }
}
