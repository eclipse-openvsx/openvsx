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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

import org.eclipse.openvsx.json.TrustedPublisherInputJson;
import org.eclipse.openvsx.util.ErrorResultException;

import static java.util.Objects.requireNonNull;

/**
 * Support for trusted publisher providers.
 *
 * @see <a href="https://repos.openssf.org/trusted-publishers-for-all-package-repositories.html">Trusted Publishers for Package Repositories</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID Connect Core 1.0 - ID Token</a>
 */
public abstract class TrustedPublishingProviderSupport {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final TrustedPublishingConfig config;
    protected final String providerId;
    protected final String providerName;
    protected final String providerUrl;
    protected final String oidcIssuer;
    protected final List<TrustedPublisherInputJson> registrationInputs;
    protected final RestClient restClient;

    private volatile JwtDecoder decoder;

    protected TrustedPublishingProviderSupport(
            TrustedPublishingConfig config,
            String providerId,
            String providerName,
            String providerUrl,
            String oidcIssuer,
            List<TrustedPublisherInputJson> registrationInputs
    ) {
        this.config = requireNonNull(config);
        this.providerId = requireNonNull(providerId);
        this.providerName = requireNonNull(providerName);
        this.providerUrl = requireNonNull(providerUrl);
        this.oidcIssuer = requireNonNull(oidcIssuer);
        this.registrationInputs = requireNonNull(registrationInputs);
        this.restClient = RestClient.create();
    }

    /**
     * Builds the decoder lazily: {@link JwtDecoders#fromIssuerLocation(String)} performs OIDC discovery over the
     * network, which must not happen at application startup.
     */
    private JwtDecoder decoder() {
        JwtDecoder localDecoder = decoder;
        if (localDecoder == null) {
            synchronized (this) {
                localDecoder = decoder;
                if (localDecoder == null) {
                    OAuth2TokenValidator<Jwt> forbiddenHeadersValidator = jwt -> {
                        if (config.getForbiddenJwtHeaders().stream().anyMatch(jwt.getHeaders()::containsKey)) {
                            return OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error("invalid_headers", "The token contains forbidden headers.", null));
                        }
                        return OAuth2TokenValidatorResult.success();
                    };
                    OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(oidcIssuer);
                    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
                        List<String> audience = jwt.getAudience();
                        if (audience != null && audience.contains(config.getAudience())) {
                            return OAuth2TokenValidatorResult.success();
                        }
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(
                                        "invalid_audience",
                                        "The token does not contain the expected audience.",
                                        null));
                    };
                    // Discovery is a network call, and this is the first exchange after every restart. A
                    // failure here says nothing about the token, so it must not be reported as a refusal.
                    NimbusJwtDecoder nimbusDecoder;
                    try {
                        nimbusDecoder = JwtDecoders.fromIssuerLocation(oidcIssuer);
                    } catch (RuntimeException e) {
                        throw unverifiable(e);
                    }
                    nimbusDecoder.setJwtValidator(
                            JwtValidators.createDefaultWithValidators(
                                    List.of(issuerValidator, audienceValidator, forbiddenHeadersValidator)));
                    decoder = localDecoder = nimbusDecoder;
                }
            }
        }
        return localDecoder;
    }

    /**
     * The unique identifier of the provider.
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * Is provider active.
     */
    public boolean isActive() {
        return config.getActiveProviders().contains(getProviderId());
    }

    /**
     * The provider name, for human consumption.
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * The "well known" URL of the provider.
     */
    public String getProviderUrl() {
        return providerUrl;
    }

    /**
     * The issuer, to be found in "iss" claim of issued OIDC tokens.
     */
    public String getOidcIssuer() {
        return oidcIssuer;
    }

    /**
     * The provider registration inputs, it requires.
     */
    public List<TrustedPublisherInputJson> getRegistrationInputs() {
        return registrationInputs;
    }

    /**
     * Parses the raw OIDC ID token in form of JWT. If validation and parsing passed, and token is found valid,
     * the contained "claims of interest" are returned as {@link Map}. Otherwise, the optional is empty.
     */
    public Optional<Map<String, String>> extract(String oidcId) {
        requireNonNull(oidcId);
        if (!config.isEnabled() || !isActive()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder().decode(oidcId);
            Map<String, String> claims = requireNonNull(extractClaims(jwt));
            if (claims.isEmpty()) {
                logger.warn("Trusted Publishing OIDC ID token does not contain any claims of interest");
                return Optional.empty();
            } else {
                return Optional.of(claims);
            }
        } catch (JwtEncodingException e) {
            // token has encoding issues
            logger.warn("Error decoding Trusted Publishing OIDC ID token", e);
        } catch (JwtValidationException e) {
            // token has validation issues
            logger.warn("Error validating Trusted Publishing OIDC ID token: {}", e.getErrors(), e);
        } catch (MissingRequiredClaimException e) {
            // missing claims we expect; lack of information
            logger.warn("Trusted Publishing OIDC ID token lack of information: {}", e.getClaim(), e);
        } catch (JwtException e) {
            // Everything else: fetching the JWK set, connectivity, the provider being down. We could not
            // check the token, which is not the same as deciding against it.
            throw unverifiable(e);
        }
        return Optional.empty();
    }

    /**
     * Reports that the token could not be checked at all, as distinct from being checked and rejected.
     * A refusal tells a publishing workflow never to come back; this asks it to try again, which is what
     * a provider outage or a JWK set that could not be fetched actually warrants.
     */
    private ErrorResultException unverifiable(RuntimeException cause) {
        logger.warn("Could not verify Trusted Publishing OIDC ID token with {}", providerName, cause);
        return new ErrorResultException(
                "Could not verify the token with " + providerName + ", please retry.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    protected static String mustRegister(Map<String, String> registration, String key) throws ErrorResultException {
        String value = registration.get(key);
        if (value == null || value.isBlank()) {
            throw new ErrorResultException("Malformed registration request");
        }
        return value;
    }

    /**
     * Helper to require a claim from the JWT, throwing {@link MissingRequiredClaimException} if not present or blank.
     */
    protected static void mustClaim(Jwt jwt, String claim, Map<String, String> claims)
            throws MissingRequiredClaimException {
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            throw new MissingRequiredClaimException(claim);
        }
        claims.put(claim, value);
    }

    /**
     * Helper to optionally require a claim from the JWT, adding it to the claims map if present and not blank.
     */
    protected static void mayClaim(Jwt jwt, String claim, Map<String, String> claims) {
        String value = jwt.getClaimAsString(claim);
        if (value != null && !value.isBlank()) {
            claims.put(claim, value);
        }
    }

    /**
     * Helper to compare two claim values that must both be present and equal.
     */
    protected static boolean claimEquals(String claim, Map<String, String> registered, Map<String, String> token) {
        String registeredValue = registered.get(claim);
        return registeredValue != null && registeredValue.equals(token.get(claim));
    }

    /**
     * Helper that strips the trailing {@code @<ref>} part from a claim value like
     * {@code owner/repo/.github/workflows/ci.yml@refs/heads/main}.
     */
    protected static String stripRef(String value) {
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        return at < 0 ? value : value.substring(0, at);
    }

    /**
     * Helper to compare an optionally registered claim: if the registration pins the claim, the token
     * must carry the equal value; if not pinned, anything (or nothing) is accepted.
     */
    protected static boolean pinnedClaimMatches(
            String claim,
            Map<String, String> registered,
            Map<String, String> token
    ) {
        String registeredValue = registered.get(claim);
        return registeredValue == null || registeredValue.equals(token.get(claim));
    }

    /**
     * Extracts issuer specific claims from the passed in JWT token, never returns {@code null}.
     *
     * @throws JwtException if extraction (including validation) fails in some way.
     */
    @NonNull
    protected abstract Map<String, String> extractClaims(Jwt jwt) throws JwtException;

    /**
     * Creates issuer specific claims from the passed in {@code registration}. This is provider specific, and
     * involves remote access, to resolve usernames and repository names to more stable, provider specific IDs.
     *
     * @throws ErrorResultException if the request processing fails in some way.
     */
    @NonNull
    protected abstract Map<String, String> extractRequest(Map<String, String> registration) throws ErrorResultException;

    /**
     * Decides whether the claims extracted from a presented OIDC ID token ({@code token}, produced by
     * {@link #extractClaims(Jwt)}) satisfy a registered trust ({@code registered}, produced by
     * {@link #extractRequest(Map)}).
     */
    public abstract boolean matches(@NonNull Map<String, String> registered, @NonNull Map<String, String> token);
}
