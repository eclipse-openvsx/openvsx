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
package org.eclipse.openvsx.trustedpublishing.gitlab;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.web.client.RestClientException;

import org.eclipse.openvsx.json.TrustedPublisherInputJson;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingProviderSupport;
import org.eclipse.openvsx.util.ErrorResultException;

import static java.util.Objects.requireNonNull;

/**
 * A GitLab instance as a trusted publishing provider.
 * <p>
 * Every instance - the public one, the Eclipse Foundation one, any self-hosted one - is served by this
 * class: they only differ in id, name, URL and OIDC issuer, which are configured through
 * {@link org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig.GitLabInstance}.
 *
 * @see <a href="https://docs.gitlab.com/ci/secrets/id_token_authentication/">GitLab OpenID Connect</a>
 */
public class GitLabTrustedPublishingProvider extends TrustedPublishingProviderSupport {
    /**
     * The provider id of the public GitLab instance, configured out of the box.
     */
    public static final String PROVIDER_ID = "gitlab";

    /**
     * The URL of the public GitLab instance.
     */
    public static final String PROVIDER_URL = "https://gitlab.com";

    private static final String CLAIM_NAMESPACE_ID = "namespace_id"; // "72"
    private static final String CLAIM_NAMESPACE_PATH = "namespace_path"; // "my-group"
    private static final String CLAIM_PROJECT_ID = "project_id"; // "20"
    private static final String CLAIM_PROJECT_PATH = "project_path"; // "my-group/my-project"
    private static final String CLAIM_ENVIRONMENT = "environment"; // "prod"; optional
    private static final String CLAIM_RUNNER_ENVIRONMENT = "runner_environment"; // "gitlab-hosted"
    private static final String CLAIM_CI_CONFIG_REF_URI = "ci_config_ref_uri"; // "gitlab.example.com/my-group/my-project//.gitlab-ci.yml@refs/heads/main"

    private static final String API_RESOLVE_REQUEST = "/api/v4/projects/{path}";

    private static final String REG_NAMESPACE = "namespace";
    private static final String REG_PROJECT = "project";
    private static final String REG_WORKFLOW = "workflow";
    private static final String REG_ENVIRONMENT = "environment";
    private static final List<TrustedPublisherInputJson> REGISTRATION_INPUTS = List.of(
            TrustedPublisherInputJson.create(REG_NAMESPACE, "Namespace", false),
            TrustedPublisherInputJson.create(REG_PROJECT, "Project name", false),
            TrustedPublisherInputJson.create(REG_WORKFLOW, "Top-level CI filename", false),
            TrustedPublisherInputJson.create(REG_ENVIRONMENT, "Environment name (optional)", true));

    /**
     * The instance part of the {@code ci_config_ref_uri} claim: the host of the instance, plus the path
     * when the instance is served under a relative URL root.
     */
    private final String instanceLocation;

    public GitLabTrustedPublishingProvider(
            TrustedPublishingConfig config,
            String providerId,
            String providerName,
            String providerUrl,
            String oidcIssuer
    ) {
        super(config, providerId, providerName, providerUrl, oidcIssuer, REGISTRATION_INPUTS);
        this.instanceLocation = instanceLocation(providerUrl);
    }

    private static String instanceLocation(String providerUrl) {
        URI uri = URI.create(providerUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Not a valid GitLab instance URL: " + providerUrl);
        }
        String path = uri.getPath();
        while (path != null && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path == null || path.isEmpty() ? host : host + path;
    }

    @NonNull
    @Override
    protected Map<String, String> extractClaims(Jwt jwt) {
        requireNonNull(jwt);
        HashMap<String, String> result = new HashMap<>(7);
        mustClaim(jwt, JwtClaimNames.SUB, result);
        mustClaim(jwt, CLAIM_NAMESPACE_ID, result);
        mustClaim(jwt, CLAIM_NAMESPACE_PATH, result);
        mustClaim(jwt, CLAIM_PROJECT_ID, result);
        mustClaim(jwt, CLAIM_PROJECT_PATH, result);
        mayClaim(jwt, CLAIM_ENVIRONMENT, result);
        mustClaim(jwt, CLAIM_RUNNER_ENVIRONMENT, result);
        mustClaim(jwt, CLAIM_CI_CONFIG_REF_URI, result);
        return result;
    }

    @NonNull
    @Override
    protected Map<String, String> extractRequest(Map<String, String> registration) throws ErrorResultException {
        requireNonNull(registration);

        final String namespace = mustRegister(registration, REG_NAMESPACE);
        final String project = mustRegister(registration, REG_PROJECT);
        final String workflow = mustRegister(registration, REG_WORKFLOW);
        final String environment = registration.get(REG_ENVIRONMENT);
        final String projectPath = namespace + "/" + project;

        Map<String, Object> response = resolve(projectPath);
        if (response == null || !(response.get("id") instanceof Number projectId)
                || !(response.get("namespace") instanceof Map<?, ?> namespaceMap)
                || !(namespaceMap.get("id") instanceof Number namespaceId)) {
            throw new ErrorResultException("Unexpected GitLab response for project " + projectPath);
        }

        HashMap<String, String> result = new HashMap<>();
        result.put(CLAIM_NAMESPACE_ID, String.valueOf(namespaceId.longValue()));
        result.put(CLAIM_NAMESPACE_PATH, namespace);
        result.put(CLAIM_PROJECT_ID, String.valueOf(projectId.longValue()));
        result.put(CLAIM_PROJECT_PATH, projectPath);
        // registered without the "@<ref>" part: publishing is trusted regardless of branch or tag
        result.put(CLAIM_CI_CONFIG_REF_URI, instanceLocation + "/" + projectPath + "//" + workflow);
        if (environment != null) {
            result.put(CLAIM_ENVIRONMENT, environment);
        }
        return result;
    }

    /**
     * Pulled out for testability; is mocked in UT to prevent real remote access.
     */
    protected Map<String, Object> resolve(String projectPath) throws ErrorResultException {
        try {
            // the {path} template variable is URL-encoded by RestClient, turning "/" into "%2F" as GitLab expects
            return restClient.get()
                    .uri(providerUrl + API_RESOLVE_REQUEST, projectPath)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException e) {
            throw new ErrorResultException("Could not resolve GitLab project " + projectPath, e);
        }
    }

    @Override
    public boolean matches(@NonNull Map<String, String> registered, @NonNull Map<String, String> token) {
        requireNonNull(registered);
        requireNonNull(token);
        return claimEquals(CLAIM_PROJECT_ID, registered, token)
                && claimEquals(CLAIM_NAMESPACE_ID, registered, token)
                && registered.get(CLAIM_CI_CONFIG_REF_URI) != null
                && registered.get(CLAIM_CI_CONFIG_REF_URI).equals(stripRef(token.get(CLAIM_CI_CONFIG_REF_URI)))
                && pinnedClaimMatches(CLAIM_ENVIRONMENT, registered, token);
    }
}
