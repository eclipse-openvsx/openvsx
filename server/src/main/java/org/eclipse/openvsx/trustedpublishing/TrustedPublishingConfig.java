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

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import org.eclipse.openvsx.trustedpublishing.github.GitHubTrustedPublishingProvider;
import org.eclipse.openvsx.trustedpublishing.gitlab.GitLabTrustedPublishingProvider;

/**
 * The trusted publishing configuration.
 * <p>
 * The scalar settings are read with {@code @Value}; the two that need a typed value - the token lifetime
 * and the GitLab instance map - are bound by {@code @ConfigurationProperties}, whose binder converts them
 * natively. Only fields with a setter are bound, so the two sets do not overlap.
 * <p>
 * Every GitLab instance behaves the same way, differing only in id, name, URL and OIDC issuer, so instances
 * are configured rather than coded. Only the public instance is configured out of the box; any other one -
 * the Eclipse Foundation instance included - is added by configuration, and becomes usable once its id is
 * listed in {@code ovsx.trusted-publishing.active-providers}:
 *
 * <pre>
 * ovsx:
 *   trusted-publishing:
 *     active-providers: github,eclipse-gitlab
 *     gitlab:
 *       eclipse-gitlab:
 *         name: Eclipse GitLab
 *         url: https://gitlab.eclipse.org
 *         issuer: https://gitlab.eclipse.org   # optional, defaults to the URL
 * </pre>
 *
 * The id is persisted with every registration, so renaming it hides the registrations made for it.
 * Configuring the id of the default instance replaces it as a whole rather than patching single fields,
 * so such an entry has to carry the name and the URL itself.
 */
@Configuration
@ConfigurationProperties(prefix = "ovsx.trusted-publishing")
@Validated
public class TrustedPublishingConfig {

    /**
     * Whether trusted publishing is enabled at all.
     */
    @Value("${ovsx.trusted-publishing.enabled:false}")
    private boolean enabled;

    /**
     * The audience to expect in OIDC ID token; by default it is the URL of this instance frontend.
     */
    @Value("${ovsx.trusted-publishing.audience:${ovsx.webui.url:}}")
    private String audience;

    /**
     * Forbidden JWT headers that are enforced.
     * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID Connect Core 1.0 - ID Token</a>
     */
    @Value("${ovsx.trusted-publishing.forbidden-jwt-headers:x5u,x5c,jku,jwk}")
    private List<String> forbiddenJwtHeaders;

    /**
     * The comma separated list of active trusted publishing providers. An id listed here must be
     * {@code github} or one of the configured GitLab instances.
     * Default: {@code github}.
     */
    @Value("${ovsx.trusted-publishing.active-providers:github}")
    private List<String> activeProviders;

    /**
     * How long an issued publishing token is valid. Must be positive: a token that does not expire is a
     * long-lived credential, which is the very thing trusted publishing exists to avoid. The lifetime of
     * ordinary personal access tokens is {@code ovsx.access-token.expiration} instead.
     */
    private Duration tokenExpiration = Duration.ofMinutes(5);

    /**
     * The known GitLab instances, keyed by provider id. Configured instances are added to the public
     * instance, which stays available unless its id is redefined.
     */
    @Valid
    private Map<String, GitLabInstance> gitlab = defaultGitLabInstances();

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    public String getAudience() {
        return audience;
    }

    @NonNull
    public List<String> getForbiddenJwtHeaders() {
        return forbiddenJwtHeaders;
    }

    @NonNull
    public List<String> getActiveProviders() {
        return activeProviders;
    }

    /**
     * The configured GitLab instances, keyed by provider id. Whether an instance can actually be used
     * is decided by {@link #getActiveProviders()}.
     */
    @NonNull
    public Map<String, GitLabInstance> getGitlab() {
        return gitlab;
    }

    public void setGitlab(Map<String, GitLabInstance> gitlab) {
        this.gitlab = gitlab;
    }

    /**
     * How long an issued publishing token is valid, {@code ovsx.trusted-publishing.token-expiration}.
     */
    @NonNull
    public Duration getTokenExpiration() {
        return tokenExpiration;
    }

    public void setTokenExpiration(Duration tokenExpiration) {
        this.tokenExpiration = tokenExpiration;
    }

    private static Map<String, GitLabInstance> defaultGitLabInstances() {
        var instances = new LinkedHashMap<String, GitLabInstance>();
        instances.put(
                GitLabTrustedPublishingProvider.PROVIDER_ID,
                new GitLabInstance("GitLab", GitLabTrustedPublishingProvider.PROVIDER_URL));
        return instances;
    }

    @PostConstruct
    public void validate() {
        // checked whether or not the feature is on, so a typo does not lie in wait until it is turned on
        var tokenExpiration = getTokenExpiration();
        if (tokenExpiration == null || !tokenExpiration.isPositive()) {
            throw new IllegalStateException(
                    "ovsx.trusted-publishing.token-expiration must be a positive duration, got: " + tokenExpiration);
        }
        if (enabled) {
            if (audience == null || audience.isBlank()) {
                throw new IllegalStateException("Trusted publishing is enabled, but audience is not configured");
            }
            if (forbiddenJwtHeaders == null || forbiddenJwtHeaders.isEmpty()) {
                throw new IllegalStateException(
                        "Trusted publishing is enabled, but forbidden JWT headers are not configured");
            }
            if (activeProviders == null || activeProviders.isEmpty()) {
                throw new IllegalStateException(
                        "Trusted publishing is enabled, but there are no active providers configured");
            }
            validateGitLabInstances();
        }
    }

    private void validateGitLabInstances() {
        for (var entry : getGitlab().entrySet()) {
            var id = entry.getKey();
            var instance = entry.getValue();
            if (GitHubTrustedPublishingProvider.PROVIDER_ID.equals(id)) {
                throw new IllegalStateException(
                        "GitLab instance '" + id + "' uses the provider id of the GitHub provider");
            }
            // a configured instance replaces a default one as a whole, so it must carry every field itself
            if (instance.getName() == null || instance.getName().isBlank()
                    || instance.getUrl() == null || instance.getUrl().isBlank()) {
                throw new IllegalStateException("GitLab instance '" + id + "' has no name or no URL configured");
            }
            for (var url : List.of(instance.getUrl(), instance.getIssuer())) {
                if (hostOf(url) == null) {
                    throw new IllegalStateException("GitLab instance '" + id + "' has a malformed URL: " + url);
                }
            }
        }
    }

    @Nullable
    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exc) {
            return null;
        }
    }

    /**
     * A single GitLab instance.
     */
    public static class GitLabInstance {

        @NotBlank
        private String name;

        @NotBlank
        private String url;

        @Nullable
        private String issuer;

        public GitLabInstance() {
            // for configuration property binding
        }

        public GitLabInstance(String name, String url) {
            this.name = name;
            this.url = url;
        }

        /**
         * The instance name, for human consumption.
         */
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * The base URL of the instance; the API and the {@code ci_config_ref_uri} claim are derived from it.
         */
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * The issuer to expect in the {@code iss} claim of issued OIDC ID tokens. GitLab issues tokens
         * under its own base URL, so this defaults to {@link #getUrl()}.
         */
        public String getIssuer() {
            return issuer == null || issuer.isBlank() ? url : issuer;
        }

        public void setIssuer(@Nullable String issuer) {
            this.issuer = issuer;
        }
    }
}
