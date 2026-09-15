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
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AccessTokenConfig {

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenConfig.class);

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

    /**
     * The peppers this instance used before the current one, so that tokens hashed with one of them keep
     * working across a pepper change.
     * <p>
     * Rotation has to work this way round because the raw token value is never stored: a row holds only
     * its hash, so nothing can rehash it under a new pepper except the holder presenting the token again.
     * Each token listed here is therefore migrated to the current pepper the next time it is used, and a
     * token nobody uses again keeps its old hash until it expires. That has two consequences worth
     * planning for:
     * <ul>
     * <li>A retired pepper must stay listed until every row that might still use it is gone - bounded by
     * {@code ovsx.access-token.expiration} after the change, plus one expiry sweep. Where expiry is
     * disabled ({@code expiration: 0}) an unused token lives forever, and so must its pepper.</li>
     * <li>No background job can finish the migration early, so removing a pepper from this list is the
     * operator's decision, not something the application can signal.</li>
     * </ul>
     * Comma separated, and so a pepper itself must not contain a comma; {@link #validate()} rejects a
     * current pepper that does, rather than letting it split into nonsense once it is rotated out.
     * Generating peppers with {@code openssl rand -base64 32} keeps clear of the problem entirely. Blank
     * entries - what a trailing or doubled comma leaves behind - are ignored rather than read as the
     * unpeppered hash, which has its own flag in {@link #acceptUnpepperedTokenHashes}.
     * <p>
     * Property: {@code ovsx.access-token.token-hash-previous-peppers}
     * Default: {@code ''}, no previous peppers
     */
    @Value("${ovsx.access-token.token-hash-previous-peppers:}")
    private List<String> previousTokenHashPeppers;

    /**
     * Whether to accept tokens whose hash carries no pepper at all.
     * <p>
     * This is the switch for adopting a pepper on a deployment that ran without one - the default is the
     * empty string, so this is the shape most instances start in. Set it alongside a new
     * {@code token-hash-pepper} and every existing token stays valid, migrating to the peppered hash as
     * it gets used; without it, setting a pepper for the first time invalidates all of them at once.
     * <p>
     * It is the unpeppered member of {@code token-hash-previous-peppers}, kept as its own flag because an
     * empty entry in a comma separated list cannot be written unambiguously. Everything the list's
     * documentation says about retiring a pepper applies here too.
     * <p>
     * Property: {@code ovsx.access-token.token-hash-accept-unpeppered}
     * Default: {@code false}
     */
    @Value("${ovsx.access-token.token-hash-accept-unpeppered:false}")
    private boolean acceptUnpepperedTokenHashes;

    /**
     * The peppers to fall back to, in order, when a token does not match under the current one. Derived
     * in {@link #validate()} from the two properties above: deduplicated, and without the current pepper,
     * which is always tried first anyway.
     */
    private List<String> tokenHashPepperKeyring = List.of();

    @Value("${ovsx.data.mirror.enabled:false}")
    private boolean mirrorEnabled;

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

    /**
     * The peppers a token that does not match under the current pepper is retried with, in order. Empty
     * unless the instance is mid-rotation, in which case a hit means the row still carries an old hash
     * and wants rewriting.
     */
    public @NonNull List<String> getTokenHashPepperKeyring() {
        return tokenHashPepperKeyring;
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
        if (tokenHashPepper.indexOf(',') >= 0) {
            throw new IllegalArgumentException(
                    "ovsx.access-token.token-hash-pepper must not contain a comma, because "
                            + "ovsx.access-token.token-hash-previous-peppers is a comma separated list and "
                            + "could not carry this value once it is rotated out");
        }
        // hasText, not isEmpty: a pepper of blanks is not a secret either, and how whitespace survives
        // a property source depends on how it was quoted - neither is worth staying quiet about.
        if (!StringUtils.hasText(tokenHashPepper)) {
            // Not an error: this is the default, and refusing to start would lock out every existing
            // deployment. It is worth one line at boot, though, because the alternative is that the
            // recommendation lives only in a javadoc nobody deploying the server reads.
            logger.warn(
                    "No ovsx.access-token.token-hash-pepper is configured, so personal access tokens "
                            + "are stored as unkeyed hashes. Whoever obtains a copy of the database can "
                            + "then confirm a token found elsewhere without touching this registry, and "
                            + "match hashes across dumps and instances. Generate one with "
                            + "'openssl rand -base64 32'; to keep the tokens that already exist working, "
                            + "set ovsx.access-token.token-hash-accept-unpeppered=true at the same time "
                            + "and drop it once they have expired.");
        }

        // LinkedHashSet: the order operators wrote decides which pepper is tried first, and a pepper
        // listed twice - or listed while still current - costs a query per authentication, not a bug.
        var keyring = new LinkedHashSet<String>();
        if (previousTokenHashPeppers != null) {
            // hasText, and not merely non-null: a trailing or doubled comma leaves a blank element behind
            // (Spring trims each one, so an all-whitespace entry arrives blank too), and a blank pepper is
            // the unpeppered hash - the thing acceptUnpepperedTokenHashes exists to gate, kept as its own
            // flag precisely because this list cannot express it unambiguously. Taking one at face value
            // would let a stray comma switch on acceptance of unpeppered tokens with nobody asking for it,
            // so a blank here means a typo and nothing else.
            previousTokenHashPeppers.stream().filter(StringUtils::hasText).forEach(keyring::add);
        }
        if (acceptUnpepperedTokenHashes) {
            keyring.add("");
        }
        keyring.remove(tokenHashPepper);
        tokenHashPepperKeyring = List.copyOf(keyring);
    }
}
