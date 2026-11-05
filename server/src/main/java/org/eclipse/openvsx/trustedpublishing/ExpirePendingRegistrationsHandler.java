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
package org.eclipse.openvsx.trustedpublishing;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.settings.ReadOnlySetting;

@Component
public class ExpirePendingRegistrationsHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(ExpirePendingRegistrationsHandler.class);

    private final ReadOnlySetting readOnly;
    private final TrustedPublishingService trustedPublishing;

    public ExpirePendingRegistrationsHandler(ReadOnlySetting readOnly, TrustedPublishingService trustedPublishing) {
        this.readOnly = readOnly;
        this.trustedPublishing = trustedPublishing;
    }

    @Override
    @Job(name = "Delete expired pending trusted publishers", retries = 0)
    public void run(HandlerJobRequest<?> handlerJobRequest) throws Exception {
        if (readOnly.isEnabled()) {
            return;
        }

        var count = trustedPublishing.deleteExpiredPendingRegistrations();
        if (count > 0) {
            logger.info("Deleted {} expired pending trusted publisher(s)", count);
        }
    }
}
