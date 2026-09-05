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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;

import static jakarta.transaction.Transactional.TxType;
import static java.util.Objects.requireNonNull;
import static org.eclipse.openvsx.util.UrlUtil.createApiUrl;

@Service
public class AccessTokenService {
    /**
     * Arbitrary, fixed key for the Postgres advisory lock guarding the token upgrade below. Must stay
     * distinct from every other advisory lock key this application uses; see
     * {@code ExtensionScanJobRecoveryService.RECOVERY_LOCK_KEY} for the other one.
     */
    private static final long UPGRADE_LOCK_KEY = 891_234_567_890_124L;

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenService.class);

    /** 256 bits; far beyond guessing, and the encoded form is still shorter than a UUID. */
    private static final int TOKEN_BYTES = 32;

    /**
     * Base62 rather than base64url, so that the body carries no {@code _} or {@code -} and the underscore
     * ending the marker stays the last separator in the value. The deployment prefix in front of the
     * marker may well contain one of its own - the development configuration uses {@code dev_ovsx} - but
     * that part is fixed and recognisable, where base64url put a further {@code -} or {@code _} at a
     * random place in roughly three of every four bodies, making the marker look longer than it is and a
     * token awkward to select as a single word. Base62 costs nothing in length: 62^43 still exceeds
     * 2^256, so the body stays 43 characters.
     */
    private static final char[] TOKEN_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            .toCharArray();

    /** Characters needed for 256 bits in base62: ceil(256 / log2(62)). */
    private static final int TOKEN_LENGTH = 43;

    private static final BigInteger TOKEN_RADIX = BigInteger.valueOf(TOKEN_ALPHABET.length);

    /** The token types that are retired by deleting the row rather than deactivating it. */
    private static final List<PersonalAccessTokenType> EPHEMERAL_TOKEN_TYPES = Arrays
            .stream(PersonalAccessTokenType.values())
            .filter(PersonalAccessTokenType::isEphemeral)
            .toList();

    private static final int TOKEN_VERSION_0 = 0;
    private static final int TOKEN_VERSION_1 = 1;
    private static final int[] ALL_TOKEN_VERSIONS = { TOKEN_VERSION_0, TOKEN_VERSION_1 };
    private static final int TOKEN_CURRENT_VERSION = TOKEN_VERSION_1;

    private final AccessTokenConfig config;
    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final MailService mail;
    private final DSLContext dsl;
    private final SecureRandom random = new SecureRandom();

    public AccessTokenService(
            AccessTokenConfig config,
            EntityManager entityManager,
            RepositoryService repositories,
            MailService mail,
            DSLContext dsl
    ) {
        this.config = config;
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.mail = mail;
        this.dsl = dsl;
    }

    /**
     * Creates a long-lived token for user. Depending on configuration, the token expiration may be set as well.
     */
    @Transactional
    public AccessTokenJson createLongLivedAccessToken(UserData user, String description) {
        requireNonNull(user);
        final LocalDateTime expiresTimestamp = config.isTokenExpiryEnabled()
                ? TimeUtil.getCurrentUTC().plus(config.getExpiration())
                : null;
        return createAccessToken(
                user,
                description,
                expiresTimestamp,
                null,
                null,
                null,
                null,
                PersonalAccessTokenType.LLT);
    }

    /**
     * Creates a trusted publishing token for a trusted publisher. The token is scoped to given trusted publisher
     * associated extension only. How long it lives is the trusted publishing configuration's to decide, so the
     * caller passes it in rather than this service reading it.
     */
    @Transactional
    public AccessTokenJson createTrustedPublishingAccessToken(
            TrustedPublisher trustedPublisher,
            String description,
            Duration expiration,
            Map<String, String> claims
    ) {
        requireNonNull(trustedPublisher);
        requireNonNull(expiration);
        requireNonNull(claims);
        final LocalDateTime expiresTimestamp = TimeUtil.getCurrentUTC().plus(expiration);
        return createAccessToken(
                trustedPublisher.getCreatedBy(),
                description,
                expiresTimestamp,
                trustedPublisher,
                claims,
                null,
                null,
                PersonalAccessTokenType.TPT);
    }

    private AccessTokenJson createAccessToken(
            UserData user,
            String description,
            @Nullable LocalDateTime expiresTimestamp,
            @Nullable TrustedPublisher trustedPublisher,
            @Nullable Map<String, String> claims,
            @Nullable Extension scopeExtension,
            @Nullable Namespace scopeNamespace,
            PersonalAccessTokenType type
    ) {
        var rawValue = generateTokenValue(type);
        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setValue(hashTokenValue(rawValue));
        token.setActive(true);
        token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
        token.setDescription(description);
        token.setExpiresTimestamp(expiresTimestamp);
        token.setVersion(TOKEN_CURRENT_VERSION);
        token.setType(type);
        if (trustedPublisher != null) {
            // fool-proofing; only TPT token may be created with TP
            if (type != PersonalAccessTokenType.TPT) {
                throw new IllegalArgumentException("Only TPT token may be created with TP");
            }
            // link TP and scope to TP.ext, and carry the exchange's claims to the publish that uses this
            token.setTrustedPublisher(trustedPublisher);
            token.setClaims(claims);
            token.setScopeExtension(trustedPublisher.getExtension());
        } else if (scopeExtension != null) {
            // scope to ext
            token.setScopeExtension(scopeExtension);
        } else if (scopeNamespace != null) {
            // scope to ns
            token.setScopeNamespace(scopeNamespace);
        }

        entityManager.persist(token);
        var json = token.toAccessTokenJson();
        // Include the token raw value after creation so the user can copy it
        json.setValue(rawValue);
        if (!type.isEphemeral()) {
            json.setDeleteTokenUrl(
                    createApiUrl(UrlUtil.getBaseUrl(), "user", "token", "delete", Long.toString(token.getId())));
        }

        return json;
    }

    /**
     * Generates a token value: the deployment's prefix, the marker saying which kind of token this is, and
     * 32 bytes from a CSPRNG. A UUID would be the wrong shape here - it is an identifier type, and the
     * time-ordered v7 this application generates elsewhere spends 48 of its bits on a readable timestamp,
     * leaving 74 random ones where raw bytes give 256.
     * <p>
     * Uniqueness is the {@code UNIQUE (value)} constraint's to enforce, not this method's. It is the only
     * check that can work: it applies to the hash that actually gets stored, and it holds across every pod
     * writing to the database, which a check-then-insert here could not.
     */
    // public to be accessible from tests
    public String generateTokenValue(PersonalAccessTokenType type) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return config.getPrefix() + type.getTokenMarker() + encodeTokenBody(bytes);
    }

    /**
     * Renders the random bytes as a fixed-width base62 string, most significant digit first.
     * <p>
     * The bytes are read as one unsigned number, so no entropy is lost and none is added: 62^43 is larger
     * than 2^256, which makes every distinct byte sequence a distinct string and leaves no room for the
     * modulo bias that mapping each character separately onto a 62-letter alphabet would introduce. Values
     * that need fewer digits keep the same width by carrying leading zero characters.
     */
    private static String encodeTokenBody(byte[] bytes) {
        var remaining = new BigInteger(1, bytes);
        var body = new char[TOKEN_LENGTH];
        for (var i = TOKEN_LENGTH - 1; i >= 0; i--) {
            var divideAndRemainder = remaining.divideAndRemainder(TOKEN_RADIX);
            remaining = divideAndRemainder[0];
            body[i] = TOKEN_ALPHABET[divideAndRemainder[1].intValue()];
        }
        return new String(body);
    }

    @Transactional
    public ResultJson deactivateAccessToken(UserData user, long id) {
        var token = repositories.findPersonalAccessToken(id);
        if (token == null || !token.isActive()) {
            throw new NotFoundException();
        }

        // Compare ids, not entities: UserData#equals compares every field - tokens and memberships
        // included - so comparing entities rejects a caller whose user differs from the stored row in
        // any way. Rejecting id 0 stops an entity that was never persisted from failing the check open.
        var tokenUser = token.getUser();
        if (tokenUser == null || tokenUser.getId() == 0 || tokenUser.getId() != user.getId()) {
            throw new NotFoundException();
        }

        token.setActive(false);
        return ResultJson.success("Deactivated access token for user " + tokenUser.getLoginName() + ".");
    }

    // REQUIRES_NEW: callers such as LocalRegistryService#createNamespace(NamespaceJson, String) wrap
    // the whole request in their own @Transactional(rollbackOn = ErrorResultException.class). Without
    // its own transaction, the setActive(false)/setAccessedTimestamp mutations below would join that
    // caller's transaction and be rolled back together with the very ErrorResultException the caller
    // throws once this method returns null - silently discarding the fact that the token was touched
    // or found expired.
    @Transactional(TxType.REQUIRES_NEW)
    public AccessTokenAuthentication useAccessToken(String tokenValue, AccessTokenAction accessTokenAction) {
        var token = repositories.findPersonalAccessToken(hashTokenValue(tokenValue));
        if (token == null) {
            // the pepper may have changed since this token was issued; the row is rewritten if so
            token = findTokenHashedWithPreviousPepper(tokenValue);
        }
        if (token == null) {
            // assume DB contains token v0; fetch and upgrade if found active token
            token = repositories.findPersonalAccessToken(tokenValue);
            if (token != null && token.getVersion() != TOKEN_CURRENT_VERSION) {
                // upgrade token
                upgradeToken(token);
            }
        }
        // existence + active
        if (token == null || !token.isActive()) {
            return null;
        }
        // personal_access_token.user_data has no NOT NULL constraint at the schema level, so a
        // legacy/corrupt row could in principle have no user attached. Callers treat a non-null
        // AccessTokenAuthentication as a fully authenticated request and dereference userData()
        // unguarded, so surface that here as "no valid authentication" rather than handing out a
        // token authentication for nobody and letting it NPE further down.
        if (token.getUser() == null) {
            return null;
        }
        // expiration - <=, not <, to match expireAccessTokens' "expires_timestamp <= ?1" and
        // findByExpiresTimestampLessThanEqual...: a token expiring at exactly `now` is expired.
        LocalDateTime now = TimeUtil.getCurrentUTC();
        if (token.getExpiresTimestamp() != null && !token.getExpiresTimestamp().isAfter(now)) {
            if (token.getType().isEphemeral()) {
                entityManager.remove(token);
            } else {
                token.setActive(false);
            }
            return null;
        }
        // Deleting a registration takes its tokens with it, so this should not be reachable; kept as a
        // guard, because a TPT that lost its registration may only ever publish an extension it can no
        // longer be checked against. Removed rather than deactivated: it can never become valid again,
        // and nothing reads the row afterwards - the same reasoning as for any ephemeral token.
        if (token.getType() == PersonalAccessTokenType.TPT && token.getTrustedPublisher() == null) {
            entityManager.remove(token);
            return null;
        }
        // scope
        AccessTokenScope scope = getScope(token);
        if (!scope.allowsAction(accessTokenAction)) {
            return null;
        }
        // bookkeeping; if "using"
        if (accessTokenAction.isUsing()) {
            token.setAccessedTimestamp(now);
            if (token.getType().isOneTime()) {
                // Deleted outright rather than deactivated: nothing reads the row again afterwards.
                // A trusted publishing token is deliberately not one of these - it stays usable until it
                // expires, so that the target platforms of one release can share the token they were
                // issued rather than exchanging the CI identity again for each of them.
                entityManager.remove(token);
            }
        }
        return new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), token.getClaims());
    }

    /**
     * Looks a token up under each pepper this instance used before the current one, and rewrites the hash
     * of the row it finds so that the next lookup matches on the first try.
     * <p>
     * This is the whole of pepper rotation. The raw value is never stored, so a row can only be moved to
     * a new pepper while its holder is presenting the token - there is no set of rows a background job
     * could rehash, the way {@link #upgradeTokens()} can rehash the v0 rows that still carry their raw
     * value. A token that is never used again therefore keeps its old hash until it expires, which is
     * what obliges an operator to keep a retired pepper configured; see
     * {@code ovsx.access-token.token-hash-previous-peppers}.
     * <p>
     * The rewrite happens before the caller has decided whether the token is usable at all, matching what
     * the v0 upgrade does: which pepper hashed a row says nothing about whether that token is active,
     * expired or in scope, so there is no reason to make the migration wait on those checks.
     * <p>
     * Costs one query per configured previous pepper, and only for a token that has not been used since
     * the rotation. An instance that is not mid-rotation has an empty keyring and does no extra work.
     */
    private @Nullable PersonalAccessToken findTokenHashedWithPreviousPepper(String tokenValue) {
        for (var previousPepper : config.getTokenHashPepperKeyring()) {
            var token = repositories.findPersonalAccessToken(hashTokenValue(tokenValue, previousPepper));
            if (token != null) {
                token.setValue(hashTokenValue(tokenValue));
                logger.debug("Rehashed access token {} with the current pepper", token.getId());
                return token;
            }
        }
        return null;
    }

    private AccessTokenScope getScope(PersonalAccessToken token) {
        AccessTokenScope scope;
        if (token.getScopeExtension() != null) {
            scope = new AccessTokenScope.ExtensionScoped(token.getScopeExtension());
        } else if (token.getScopeNamespace() != null) {
            scope = new AccessTokenScope.NamespaceScoped(token.getScopeNamespace());
        } else {
            scope = new AccessTokenScope.Unrestricted();
        }
        if (token.getType() == PersonalAccessTokenType.TPT) {
            scope = scope.and(new AccessTokenScope.ActionScoped(AccessTokenAction.PublishVersion.class));
        }
        return scope;
    }

    /**
     * Retires every token that has expired.
     * <p>
     * An ephemeral token is deleted rather than deactivated, the same as when a one-time token is used, when
     * a trusted publisher registration goes, or when an extension is purged: it can never be used again,
     * nothing reads the row afterwards, and one is minted per exchange. Keeping the expired ones while
     * deleting the used ones would retain exactly the rows nobody has a question about.
     * <p>
     * Long-lived tokens stay as deactivated rows: a user is shown their own expired tokens, and the
     * expiry notification mails read them.
     */
    @Transactional
    public int expireAccessTokens() {
        var now = TimeUtil.getCurrentUTC();
        var deletedAccessTokens = repositories.deleteExpiredPersonalAccessTokens(now, EPHEMERAL_TOKEN_TYPES);
        var expiredAccessTokens = repositories.expirePersonalAccessTokens(now);
        if (config.isSendExpiredMailEnabled()) {
            for (var token : expiredAccessTokens) {
                if (token.getType().isNotify()) {
                    mail.scheduleAccessTokenExpiredMail(token);
                }
            }
        }
        return deletedAccessTokens.size() + expiredAccessTokens.size();
    }

    @Transactional
    public void scheduleTokenExpirationNotification(PersonalAccessToken token) {
        // find, not merge: only `notified` is this method's to change, and merging the whole
        // detached token reverted any column that moved since it was loaded - see #989.
        var managedToken = entityManager.find(PersonalAccessToken.class, token.getId());
        if (managedToken == null) {
            return;
        }
        if (managedToken.getType().isNotify() && !managedToken.isNotified()) {
            try {
                mail.scheduleAccessTokenExpiryNotification(managedToken);
            } finally {
                managedToken.setNotified(true);
            }
        }
    }

    @Transactional
    public int setExpirationTimeForLegacyAccessTokens(LocalDateTime expirationTime) {
        return repositories.updateExpiresTimeForLegacyPersonalAccessTokens(expirationTime, PersonalAccessTokenType.LLT);
    }

    /**
     * Upgrades every token still stored in a legacy format.
     * <p>
     * The job that calls this is enqueued from {@code ApplicationStartedEvent}, which fires in every
     * instance's own JVM: during a rolling update several pods run this against the same rows. The work
     * itself is idempotent - each row is hashed from the raw value it still holds, and the row stops
     * matching once upgraded - but there is no point in every pod scanning and rewriting the whole set,
     * so a transaction-scoped advisory lock lets one of them do it. {@code pg_try_advisory_xact_lock}
     * returns immediately rather than blocking, and Postgres drops the lock when this method's
     * transaction ends, so there is no unlock to forget and none can leak onto a pooled connection.
     */
    @Transactional
    public int upgradeTokens() {
        if (!tryAcquireUpgradeLock()) {
            logger.debug("Another instance already holds the token upgrade lock, skipping");
            return 0;
        }

        int upgradedCount = 0;
        for (int version : ALL_TOKEN_VERSIONS) {
            if (version == TOKEN_CURRENT_VERSION) {
                continue;
            }
            var legacyTokens = repositories.findAllPersonalAccessTokensByVersion(version);
            for (var token : legacyTokens) {
                if (upgradeToken(token)) {
                    upgradedCount++;
                }
            }
        }
        return upgradedCount;
    }

    // Package-private so a test can stub it via a spy without needing two real database connections.
    boolean tryAcquireUpgradeLock() {
        return Boolean.TRUE.equals(
                dsl.fetchValue(
                        DSL.select(
                                DSL.function("pg_try_advisory_xact_lock", Boolean.class, DSL.val(UPGRADE_LOCK_KEY)))));
    }

    private boolean upgradeToken(PersonalAccessToken token) {
        int version = token.getVersion();
        if (version == TOKEN_VERSION_0) {
            token.setValue(hashTokenValue(token.getValue()));
            token.setVersion(TOKEN_VERSION_1);
            return true;
        }
        if (version == TOKEN_VERSION_1) {
            return false;
        }
        throw new IllegalArgumentException("Unsupported token version: " + version);
    }

    private String hashTokenValue(String tokenValue) {
        return hashTokenValue(tokenValue, config.getTokenHashPepper());
    }

    private String hashTokenValue(String tokenValue, String pepper) {
        try {
            // the pepper is instance wide and lives in the configuration only; it must never reach the DB
            String payload = tokenValue + pepper;
            return Hex.encodeHexString(
                    DigestUtils.digest(
                            MessageDigest.getInstance(config.getTokenHashAlgorithm()),
                            payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // we verify this on boot; unlikely
            throw new IllegalStateException("Hash algorithm not found: " + config.getTokenHashAlgorithm(), e);
        }
    }
}
