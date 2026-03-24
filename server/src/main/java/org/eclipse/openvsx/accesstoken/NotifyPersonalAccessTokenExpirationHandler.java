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
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TimeUtil;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class NotifyPersonalAccessTokenExpirationHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    private final Logger logger = LoggerFactory.getLogger(NotifyPersonalAccessTokenExpirationHandler.class);

    private final AccessTokenConfig config;
    private final AccessTokenService tokens;
    private final RepositoryService repositories;

    public NotifyPersonalAccessTokenExpirationHandler(
            AccessTokenConfig config,
            AccessTokenService tokens,
            RepositoryService repositories
    ) {
        this.config = config;
        this.tokens = tokens;
        this.repositories = repositories;
    }

    @Override
    public void run(HandlerJobRequest<?> handlerJobRequest) throws Exception {
        if (config.isTokenExpiryNotificationEnabled()) {
            var expireBefore = TimeUtil.getCurrentUTC().plus(config.getNotification());
            var page = PageRequest.of(0, config.getMaxTokenNotifications());
            var expiringAccessTokens = repositories.findExpiringAccessTokensWithoutNotification(expireBefore, page);
            for (var token : expiringAccessTokens) {
                tokens.scheduleTokenExpirationNotification(token);
            }

            if (!expiringAccessTokens.isEmpty()) {
                logger.info("Scheduled {} notification(s) for expiring personal access tokens", expiringAccessTokens.size());
            }
        }
    }
}
