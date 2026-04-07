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
package org.eclipse.openvsx.accesstoken;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class AccessTokenConfig {
    /**
     * The token prefix to use when generating a new access token.
     * <p>
     * Property: {@code ovsx.access-token.prefix}
     * Old Property: {@code ovsx.token-prefix}
     * Default: {@code ''}
     */
    @Value("#{'${ovsx.access-token.prefix:${ovsx.token-prefix:}}'}")
    private String prefix;

    /**
     * The expiration period for personal access tokens.
     * <p>
     * If {@code 0} is provided, the access tokens do not expire.
     * <p>
     * Property: {@code ovsx.access-token.expiration}
     * Default: {@code P90D}, expires in 90 days
     */
    @Value("${ovsx.access-token.expiration:P90D}")
    private Duration expiration;

    /**
     * The duration before the expiration of an access token
     * to send out a notification email to users.
     * <p>
     * Property: {@code ovsx.access-token.notification}
     * Default: {@code P7D}, 7 days prior to expiration
     */
    @Value("${ovsx.access-token.notification:P7D}")
    private Duration notification;

    /**
     * Whether an email shall be sent when a token has expired.
     * <p>
     * Property: {@code ovsx.access-token.send-expired-mail}
     * Default: {@code true}
     */
    @Value("${ovsx.access-token.send-expired-mail:false}")
    private boolean sendExpiredMail;

    /**
     * The maximum number of expiring token notifications to handle
     * within one job execution.
     * <p>
     * Property: {@code ovsx.access-token.max-token-notifications}
     * Default: {@code 100}
     */
    @Value("${ovsx.access-token.max-token-notifications:100}")
    private int maxTokenNotifications;

    /**
     * The cron schedule for the job to disable expired
     * access tokens.
     * <p>
     * Property: {@code ovsx.access-token.expiration-schedule}
     * Default: every 15 min
     */
    @Value("${ovsx.access-token.expiration-schedule:0 */15 * * * *}")
    private String expirationSchedule;

    /**
     * The cron schedule for the job to send out notifications
     * for soon to be expired access tokens.
     * <p>
     * Property: {@code ovsx.access-token.notification-schedule}
     * Default: every 15 min
     */
    @Value("${ovsx.access-token.notification-schedule:30 */15 * * * *}")
    private String notificationSchedule;

    @Value("${ovsx.data.mirror.enabled:false}")
    private boolean mirrorEnabled;

    public @Nonnull String getPrefix() {
        return this.prefix;
    }

    public boolean isTokenExpiryEnabled() {
        return this.expiration.isPositive();
    }

    public @Nonnull Duration getExpiration() {
        return this.expiration;
    }

    public boolean isTokenExpiryNotificationEnabled() {
        return this.notification.isPositive();
    }

    public @Nonnull Duration getNotification() {
        return this.notification;
    }

    public boolean isSendExpiredMailEnabled() {
        return this.sendExpiredMail;
    }

    public int getMaxTokenNotifications() {
        return this.maxTokenNotifications;
    }

    public boolean hasExpirationSchedule() {
        return StringUtils.hasText(this.expirationSchedule);
    }

    public @Nonnull String getExpirationSchedule() {
        return this.expirationSchedule;
    }

    public boolean hasNotificationSchedule() {
        return StringUtils.hasText(this.notificationSchedule);
    }

    public @Nonnull String getNotificationSchedule() {
        return this.notificationSchedule;
    }

    @PostConstruct
    public void validate() {
        if (isTokenExpiryEnabled() && mirrorEnabled) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.expiration can not be enabled when mirror mode is active, got: " + expiration);
        }

        if (expiration.isNegative()) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.expiration must be a non-negative duration, got: " + expiration);
        }

        if (notification.isNegative()) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.notification must be a non-negative duration, got: " + notification);
        }

        if (maxTokenNotifications < 0) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.max-token-notifications must be a non-negative number, got: " + maxTokenNotifications);
        }
    }
}
