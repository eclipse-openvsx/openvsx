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
package org.eclipse.openvsx.search;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;

@Component
public class ElasticSearchUpdateIndexJobRequestHandler implements JobRequestHandler<HandlerJobRequest> {

    private final ElasticSearchService search;

    public ElasticSearchUpdateIndexJobRequestHandler(ElasticSearchService search) {
        this.search = search;
    }

    @Override
    @Job(name = "Task scheduled once per day to soft-update the search index.", retries = 0)
    public void run(HandlerJobRequest jobRequest) throws Exception {
        search.updateSearchIndex();
    }
}
