/*******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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
package org.eclipse.openvsx.accesstoken;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.settings.SettingsService;

@Component
public class UpgradePersonalAccessTokenHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(UpgradePersonalAccessTokenHandler.class);

    private final SettingsService settings;
    private final AccessTokenService tokens;

    public UpgradePersonalAccessTokenHandler(
            SettingsService settings,
            AccessTokenService tokens
    ) {
        this.settings = settings;
        this.tokens = tokens;
    }

    @Override
    @Job(name = "Upgrade tokens", retries = 0)
    public void run(HandlerJobRequest<?> handlerJobRequest) throws Exception {
        if (settings.isReadOnly()) {
            return;
        }

        int upgraded = tokens.upgradeTokens();
        if (upgraded > 0) {
            logger.info("Upgraded {} token(s)", upgraded);
        }
    }
}
