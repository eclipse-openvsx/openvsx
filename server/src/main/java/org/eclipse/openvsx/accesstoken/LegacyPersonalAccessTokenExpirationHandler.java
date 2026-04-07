/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.accesstoken;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.util.TimeUtil;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LegacyPersonalAccessTokenExpirationHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(LegacyPersonalAccessTokenExpirationHandler.class);

    private final AccessTokenConfig config;
    private final AccessTokenService tokens;

    public LegacyPersonalAccessTokenExpirationHandler(AccessTokenConfig config, AccessTokenService tokens) {
        this.config = config;
        this.tokens = tokens;
    }

    @Override
    public void run(HandlerJobRequest<?> handlerJobRequest) throws Exception {
        if (config.isTokenExpiryEnabled()) {
            var expirationTime = TimeUtil.getCurrentUTC().plus(config.getExpiration());
            var count = tokens.setExpirationTimeForLegacyAccessTokens(expirationTime);
            if (count > 0) {
                logger.info("Set expiration time for {} legacy personal access token(s)", count);
            }
        }
    }
}
