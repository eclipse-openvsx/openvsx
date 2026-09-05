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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import org.eclipse.openvsx.mirror.MirrorConfig;

@Configuration
public class AccessTokenConfig {

    private final MirrorConfig mirrorConfig;

    public AccessTokenConfig(MirrorConfig mirrorConfig) {
        this.mirrorConfig = mirrorConfig;
    }

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
     * The expiration period for long-lived personal access tokens.
     * <p>
     * If {@code 0} is provided, the access tokens do not expire.
     * <p>
     * Property: {@code ovsx.access-token.expiration}
     * Default: {@code P90D}, expires in 90 days
     */
    @Value("${ovsx.access-token.expiration:P90D}")
    private Duration expiration;

    /**
     * The duration before the expiration of a long-lived personal access token
     * to send out a notification email to users.
     * <p>
     * Property: {@code ovsx.access-token.notification}
     * Default: {@code P7D}, 7 days prior to expiration
     */
    @Value("${ovsx.access-token.notification:P7D}")
    private Duration notification;

    /**
     * Whether an email shall be sent when a long-lived personal access token has expired.
     * <p>
     * Property: {@code ovsx.access-token.send-expired-mail}
     * Default: {@code true}
     */
    @Value("${ovsx.access-token.send-expired-mail:false}")
    private boolean sendExpiredMail;

    /**
     * The maximum number of expiring personal long-lived access token notifications to handle
     * within one job execution.
     * <p>
     * Property: {@code ovsx.access-token.max-token-notifications}
     * Default: {@code 100}
     */
    @Value("${ovsx.access-token.max-token-notifications:100}")
    private int maxTokenNotifications;

    /**
     * The cron schedule for the job to disable expired
     * personal long-lived access tokens.
     * <p>
     * Property: {@code ovsx.access-token.expiration-schedule}
     * Default: every 15 min
     */
    @Value("${ovsx.access-token.expiration-schedule:0 */15 * * * *}")
    private String expirationSchedule;

    /**
     * The cron schedule for the job to send out notifications
     * for soon to be expired personal long-lived access tokens.
     * <p>
     * Property: {@code ovsx.access-token.notification-schedule}
     * Default: every 15 min
     */
    @Value("${ovsx.access-token.notification-schedule:30 */15 * * * *}")
    private String notificationSchedule;

    /**
     * The hash algorithm used for personal access tokens.
     * <p>
     * Property: {@code ovsx.access-token.token-hash-algorithm}
     * Default: {@code SHA-256}
     */
    @Value("${ovsx.access-token.token-hash-algorithm:SHA-256}")
    private String tokenHashAlgorithm;

    /**
     * The (instance wide) secret mixed into the hash of every personal access token.
     * <p>
     * A pepper rather than a salt: one value for the whole instance, not one per token, and it lives
     * only in the configuration so that it is never stored next to the hashes it protects. That is what
     * it is for - the token values are 256 random bits each, so there is nothing to precompute against
     * and no chance of two of them colliding, and the only thing this secret can buy is making a leaked
     * {@code personal_access_token.value} column useless to whoever holds it. Keep it out of anywhere a
     * non-secret would go.
     * <p>
     * Note: because the same pepper covers every token, changing it invalidates all existing/active
     * personal access tokens at once. Default is the empty string, which mixes in nothing and is not
     * recommended for production!
     * <p>
     * Property: {@code ovsx.access-token.token-hash-pepper}
     * Default: {@code ''}
     */
    @Value("${ovsx.access-token.token-hash-pepper:}")
    private String tokenHashPepper;

    public @NonNull String getPrefix() {
        return this.prefix;
    }

    public boolean isTokenExpiryEnabled() {
        return this.expiration.isPositive();
    }

    public @NonNull Duration getExpiration() {
        return this.expiration;
    }

    public boolean isTokenExpiryNotificationEnabled() {
        return this.notification.isPositive();
    }

    public @NonNull Duration getNotification() {
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

    public @NonNull String getExpirationSchedule() {
        return this.expirationSchedule;
    }

    public boolean hasNotificationSchedule() {
        return StringUtils.hasText(this.notificationSchedule);
    }

    public @NonNull String getNotificationSchedule() {
        return this.notificationSchedule;
    }

    public @NonNull String getTokenHashAlgorithm() {
        return tokenHashAlgorithm;
    }

    public @NonNull String getTokenHashPepper() {
        return tokenHashPepper;
    }

    @PostConstruct
    public void validate() {
        if (isTokenExpiryEnabled() && mirrorConfig.isEnabled()) {
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
                    "ovsx.access-token.max-token-notifications must be a non-negative number, got: "
                            + maxTokenNotifications);
        }
        try {
            MessageDigest.getInstance(tokenHashAlgorithm);
        } catch (NullPointerException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.token-hash-algorithm must be non-null and a valid digest algorithm name, got: "
                            + tokenHashAlgorithm,
                    e);
        }
        if (tokenHashPepper == null) {
            throw new IllegalArgumentException("ovsx.access-token.token-hash-pepper must not be null");
        }
    }
}
