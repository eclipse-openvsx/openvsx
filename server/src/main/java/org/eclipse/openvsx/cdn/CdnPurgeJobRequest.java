/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.cdn;

import java.util.List;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * The surrogate keys one committed change made stale.
 */
public class CdnPurgeJobRequest implements JobRequest {

    private List<String> keys;

    public CdnPurgeJobRequest() {
    }

    public CdnPurgeJobRequest(List<String> keys) {
        this.keys = keys;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    @Override
    public Class<? extends JobRequestHandler<CdnPurgeJobRequest>> getJobRequestHandler() {
        return CdnPurgeJobRequestHandler.class;
    }
}
