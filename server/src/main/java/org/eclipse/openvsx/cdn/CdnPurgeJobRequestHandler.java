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

import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends one collected purge, retried by JobRunr when the CDN cannot be reached.
 * <p>
 * A failure that exhausts the retries leaves the CDN serving what it has until those responses
 * expire on their own, which is why it is counted rather than only logged; see {@link CdnPurgeMetrics}.
 */
@Component
public class CdnPurgeJobRequestHandler implements JobRequestHandler<CdnPurgeJobRequest> {

    private static final Logger logger = LoggerFactory.getLogger(CdnPurgeJobRequestHandler.class);

    private final CdnPurgeClient client;
    private final CdnPurgeMetrics metrics;

    public CdnPurgeJobRequestHandler(
            @Autowired(required = false)
            @Nullable CdnPurgeClient client,
            CdnPurgeMetrics metrics
    ) {
        this.client = client;
        this.metrics = metrics;
    }

    @Override
    public void run(CdnPurgeJobRequest request) throws Exception {
        var keys = request.getKeys();
        if (client == null || keys == null || keys.isEmpty()) {
            // the provider was unconfigured between enqueueing this and running it
            return;
        }
        try {
            client.purge(keys);
            metrics.purged(keys.size());
            logger.debug("Purged {} surrogate keys from the CDN", keys.size());
        } catch (Exception exc) {
            metrics.failed(keys.size());
            logger.error("Failed to purge {} surrogate keys from the CDN: {}", keys.size(), keys, exc);
            throw exc;
        }
    }
}
