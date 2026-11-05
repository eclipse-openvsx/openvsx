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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.nimbusds.jwt.JWTParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.trustedpublishing.github.GitHubTrustedPublishingProvider;
import org.eclipse.openvsx.trustedpublishing.gitlab.GitLabTrustedPublishingProvider;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;

import static java.util.Objects.requireNonNull;

@Service
public class TrustedPublishingService {
    private static final String TOKEN_DESCRIPTION_TEMPLATE = "Trusted publishing (%s)";
    // shortcut: fixed, make it a trusted-publishing setting when a deployment needs another lifetime
    private static final Duration REGISTRATION_CODE_LIFETIME = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TrustedPublishingConfig config;
    private final RepositoryService repositories;
    private final AccessTokenService tokens;
    private final EntityManager entityManager;

    private final Map<String, TrustedPublishingProviderSupport> providers;

    public TrustedPublishingService(
            TrustedPublishingConfig config,
            RepositoryService repositories,
            AccessTokenService tokens,
            EntityManager entityManager
    ) {
        this(config, repositories, tokens, entityManager, config.isEnabled() ? createProviders(config) : Map.of());
        if (config.isEnabled()) {
            warnAboutUnknownActiveProviders(config);
        }
    }

    // for tests, to run the exchange against providers that do not reach out to the network
    TrustedPublishingService(
            TrustedPublishingConfig config,
            RepositoryService repositories,
            AccessTokenService tokens,
            EntityManager entityManager,
            Map<String, TrustedPublishingProviderSupport> providers
    ) {
        this.config = requireNonNull(config);
        this.repositories = requireNonNull(repositories);
        this.tokens = requireNonNull(tokens);
        this.entityManager = requireNonNull(entityManager);
        this.providers = providers;
    }

    /**
     * GitHub is a single, hard-wired provider; every configured GitLab instance becomes one of its own.
     */
    private static Map<String, TrustedPublishingProviderSupport> createProviders(TrustedPublishingConfig config) {
        // insertion-ordered, so the providers are always offered in the same order: GitHub first, then the
        // GitLab instances as configured. An unordered map would reshuffle the list on every restart.
        var providers = new LinkedHashMap<String, TrustedPublishingProviderSupport>();
        providers.put(GitHubTrustedPublishingProvider.PROVIDER_ID, new GitHubTrustedPublishingProvider(config));
        config.getGitlab()
                .forEach(
                        (providerId, instance) -> providers.put(
                                providerId,
                                new GitLabTrustedPublishingProvider(
                                        config,
                                        providerId,
                                        instance.getName(),
                                        instance.getUrl(),
                                        instance.getIssuer())));
        return Collections.unmodifiableMap(providers);
    }

    /**
     * An active provider without a matching definition is silently unusable, which is hard to tell apart
     * from a working setup, so say so at startup.
     */
    private void warnAboutUnknownActiveProviders(TrustedPublishingConfig config) {
        var unknown = config.getActiveProviders().stream().filter(id -> !providers.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            logger.warn(
                    "Trusted publishing lists active providers that are not configured and stay unusable: {}",
                    unknown);
        }
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    private void ensureEnabled() {
        if (!config.isEnabled()) {
            throw new ErrorResultException("Trusted publishing is not enabled.", HttpStatus.NOT_FOUND);
        }
    }

    private boolean providerIsActive(String providerId) {
        TrustedPublishingProviderSupport provider = providers.get(providerId);
        return provider != null && provider.isActive();
    }

    /**
     * Client initiated registration of trusted publishing. The user must be an owner of the namespace.
     */
    @Transactional
    public TrustedPublisher registerTrustedPublisher(
            UserData user,
            String namespaceName,
            String extensionName,
            String providerId,
            Map<String, String> registration
    ) {
        requireNonNull(user);
        requireNonNull(namespaceName);
        requireNonNull(extensionName);
        requireNonNull(providerId);
        requireNonNull(registration);
        ensureEnabled();

        Namespace namespace = requireOwnedNamespace(user, namespaceName);
        Extension extension = repositories.findActiveExtension(extensionName, namespaceName);
        if (extension == null) {
            throw new ErrorResultException("Extension must exist to register trusted publisher for it.");
        }

        // a pending registration whose code has expired can never be bound, so registering again replaces it
        var existing = repositories.findTrustedPublishersByExtension(extension).toList();
        if (existing.stream().anyMatch(tp -> !isExpiredPending(tp))) {
            throw new ErrorResultException("An equivalent trusted publisher is already registered.");
        }
        if (!existing.isEmpty()) {
            existing.forEach(repositories::deleteTrustedPublisher);
            entityManager.flush();
        }

        TrustedPublishingProviderSupport provider = providers.get(providerId);
        if (provider == null || !provider.isActive()) {
            throw new ErrorResultException("Unknown trusted publishing provider: " + providerId);
        }

        Map<String, String> claims = provider.extractRequest(registration);

        TrustedPublisher publisher = new TrustedPublisher();
        publisher.setExtension(extension);
        publisher.setProvider(provider.getProviderId());
        publisher.setRegistration(registration);
        publisher.setClaims(claims);
        if (provider.lacksIds(claims)) {
            // the repository is not visible without authentication; the first publish binds it, given the code
            var code = newRegistrationCode();
            publisher.setRegistrationCode(code);
            publisher.setRegistrationCodeHash(hash(code));
            publisher.setRegistrationCodeExpiry(TimeUtil.getCurrentUTC().plus(REGISTRATION_CODE_LIFETIME));
        }
        // find, not merge: this needs a managed UserData to point the new row at, and merging the
        // caller's (detached) user wrote their whole row back as a side effect - see #989.
        // trusted_publisher.created_by is NOT NULL, so a user that has gone away since the request
        // was authenticated has to fail here rather than as a constraint violation on persist.
        var createdBy = entityManager.find(UserData.class, user.getId());
        if (createdBy == null) {
            throw new ErrorResultException("Cannot register a trusted publisher for an unknown user.");
        }
        publisher.setCreatedBy(createdBy);
        publisher.setCreatedTimestamp(TimeUtil.getCurrentUTC());
        entityManager.persist(publisher);
        return publisher;
    }

    /**
     * The trusted publishers of a namespace together with the extensions a further one can be
     * registered for. Registrations of inactive extensions are listed as well, so that they remain
     * visible and can be deleted; such extensions are not registrable.
     *
     * @param publishers the registrations of every extension of the namespace
     * @param registrableExtensions names of the active extensions without a registration, sorted by name
     */
    public record TrustedPublishers(List<TrustedPublisher> publishers, List<String> registrableExtensions) {}

    /**
     * Lists trusted publishers of a namespace. The user must be an owner of the namespace.
     */
    public TrustedPublishers getTrustedPublishers(UserData user, String namespaceName) {
        requireNonNull(user);
        requireNonNull(namespaceName);
        ensureEnabled();
        Namespace namespace = requireOwnedNamespace(user, namespaceName);
        var activeExtensionNames = Set.copyOf(repositories.findActiveExtensionNames(namespace));
        var publishers = new ArrayList<TrustedPublisher>();
        var registrableExtensions = new ArrayList<String>();
        for (String extensionName : repositories.findAllExtensionNames(namespace)) {
            Extension extension = repositories.findExtension(extensionName, namespace);
            if (extension == null) {
                continue;
            }
            var extensionPublishers = repositories.findTrustedPublishersByExtension(extension).toList();
            if (extensionPublishers.isEmpty()) {
                // at most one registration per extension, so only untaken active ones are still registrable
                if (activeExtensionNames.contains(extensionName)) {
                    registrableExtensions.add(extensionName);
                }
            } else {
                // filter by active providers; if registration exists for non-active provider, filter it out
                publishers.addAll(extensionPublishers.stream().filter(p -> providerIsActive(p.getProvider())).toList());
            }
        }
        return new TrustedPublishers(publishers, registrableExtensions);
    }

    /**
     * Deletes a trusted publisher registration. The user must be an owner of the namespace.
     */
    @Transactional
    public ResultJson deleteTrustedPublisher(UserData user, String namespaceName, long id) {
        requireNonNull(user);
        requireNonNull(namespaceName);
        ensureEnabled();
        Namespace namespace = requireOwnedNamespace(user, namespaceName);
        TrustedPublisher publisher = repositories.findTrustedPublisher(id);
        if (publisher == null || !Objects.equals(publisher.getExtension().getNamespace().getId(), namespace.getId())) {
            throw new NotFoundException();
        }
        repositories.deleteTrustedPublisher(publisher);
        return ResultJson.success("Deleted trusted publisher for namespace " + namespace.getName() + ".");
    }

    /**
     * Deletes the pending registrations whose code has expired, as they can no longer be bound.
     */
    @Transactional
    public int deleteExpiredPendingRegistrations() {
        return repositories.deleteExpiredPendingTrustedPublishers(TimeUtil.getCurrentUTC());
    }

    /**
     * Returns the enforced "audience" ({@code aud}) header of the accepted OIDC ID tokens, if service is enabled.
     */
    public String getRequiredAudience() {
        ensureEnabled();
        return config.getAudience();
    }

    /**
     * Lists active trusted publisher providers.
     */
    public Map<String, TrustedPublishingProviderSupport> getTrustedPublisherProviders() {
        ensureEnabled();
        return providers.entrySet().stream()
                .filter(e -> e.getValue().isActive())
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Client signaled publishing intent, by submitting OIDC ID token. If publishing intent is approved,
     * service will issue an access token that is returned to client, and client should publish using received
     * token.
     */
    @Transactional
    public AccessTokenJson requestPublishToken(
            String namespaceName,
            String extensionName,
            String token,
            String registrationCode
    ) {
        requireNonNull(namespaceName);
        requireNonNull(extensionName);
        requireNonNull(token);
        ensureEnabled();

        // just blindly parse token to get "iss" claim from it; to identify provider to use
        String issuer;
        try {
            Object iss = JWTParser.parse(token).getJWTClaimsSet().getClaim(JwtClaimNames.ISS);
            if (iss == null) {
                throw new ErrorResultException("Token does not have issuer set.");
            }
            issuer = iss instanceof String ? (String) iss : String.valueOf(iss);
        } catch (ParseException e) {
            throw new ErrorResultException("Failed to pre-parse token.");
        }

        // select provider based on "iss"
        TrustedPublishingProviderSupport provider = providers.values().stream()
                .filter(TrustedPublishingProviderSupport::isActive)
                .filter(p -> Objects.equals(issuer, p.getOidcIssuer()))
                .findFirst()
                .orElseThrow(() -> new ErrorResultException("Unsupported token issuer."));

        // using provider validate token and extract claims of interest
        Map<String, String> claims = provider.extract(token)
                .orElseThrow(() -> new ErrorResultException("The token could not be validated.", HttpStatus.FORBIDDEN));

        Namespace namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("No trusted publisher matches the presented token.", HttpStatus.FORBIDDEN);
        }
        Extension extension = repositories.findActiveExtension(extensionName, namespaceName);
        if (extension == null) {
            throw new ErrorResultException("No trusted publisher matches the presented token.", HttpStatus.FORBIDDEN);
        }

        var candidates = repositories.findTrustedPublishersByExtension(extension).stream()
                .filter(tp -> tp.getProvider().equals(provider.getProviderId()))
                .toList();
        TrustedPublisher match = candidates.stream()
                .filter(tp -> !tp.isPending() && provider.matches(tp.getClaims(), claims))
                .findFirst()
                .orElse(null);
        if (match == null) {
            // a pending registration has no IDs yet, so its names are compared against the verified token
            match = candidates.stream()
                    .filter(
                            tp -> tp.isPending()
                                    && provider.matches(provider.withIdsOf(tp.getClaims(), claims), claims))
                    .findFirst()
                    .map(tp -> bindPending(tp, provider, claims, registrationCode))
                    .orElseThrow(
                            () -> new ErrorResultException(
                                    "No trusted publisher matches the presented token.",
                                    HttpStatus.FORBIDDEN));
        }

        logger.info(
                "Issuing trusted publishing token for namespace {} to {}",
                namespace.getName(),
                claims.get(JwtClaimNames.SUB));

        // The issued token is TPT personal access token of the registering user scoped for selected namespace
        return tokens.createTrustedPublishingAccessToken(
                match,
                TOKEN_DESCRIPTION_TEMPLATE.formatted(provider.getProviderId()),
                config.getTokenExpiration(),
                claims);
    }

    /**
     * Binds a pending registration to the IDs of the verified token, if the code it was registered with is
     * presented in time. The code works once: the row is locked and re-read, so of two concurrent exchanges
     * only one binds it.
     */
    private TrustedPublisher bindPending(
            TrustedPublisher publisher,
            TrustedPublishingProviderSupport provider,
            Map<String, String> claims,
            String registrationCode
    ) {
        entityManager.refresh(publisher, LockModeType.PESSIMISTIC_WRITE);
        if (!publisher.isPending() || isExpiredPending(publisher)) {
            throw new ErrorResultException(
                    "The registration code of this trusted publisher has expired. Register it again.",
                    HttpStatus.FORBIDDEN);
        }
        if (registrationCode == null
                || !MessageDigest.isEqual(
                        hash(registrationCode.trim()).getBytes(StandardCharsets.US_ASCII),
                        publisher.getRegistrationCodeHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new ErrorResultException(
                    "This trusted publisher registration is pending. Publish with its registration code to complete it.",
                    HttpStatus.FORBIDDEN);
        }
        publisher.setClaims(provider.withIdsOf(publisher.getClaims(), claims));
        publisher.setRegistrationCodeHash(null);
        publisher.setRegistrationCodeExpiry(null);
        return publisher;
    }

    private static boolean isExpiredPending(TrustedPublisher publisher) {
        return publisher.isPending()
                && (publisher.getRegistrationCodeExpiry() == null
                        || TimeUtil.getCurrentUTC().isAfter(publisher.getRegistrationCodeExpiry()));
    }

    private static String newRegistrationCode() {
        var bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String code) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Namespace requireOwnedNamespace(UserData user, String namespaceName) {
        Namespace namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }
        if (!repositories.isNamespaceOwner(user, namespace)) {
            throw new ErrorResultException("You must be an owner of this namespace.", HttpStatus.FORBIDDEN);
        }
        return namespace;
    }
}
