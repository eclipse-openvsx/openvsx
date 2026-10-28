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
package org.eclipse.openvsx.trustedpublishing.github;

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
 * GitHub specific support.
 *
 * @see <a href="https://docs.github.com/en/actions/concepts/security/openid-connect">GitHub OpenID Connect</a>
 */
public abstract class GitHubTrustedPublishingProviderSupport extends TrustedPublishingProviderSupport {
    private static final String CLAIM_REPOSITORY = "repository"; // "octo-org/octo-repo"
    private static final String CLAIM_REPOSITORY_ID = "repository_id"; // "74"
    private static final String CLAIM_REPOSITORY_OWNER = "repository_owner"; // "octo-org"
    private static final String CLAIM_REPOSITORY_OWNER_ID = "repository_owner_id"; // "65"
    private static final String CLAIM_ENVIRONMENT = "environment"; // "prod"; optional
    private static final String CLAIM_RUNNER_ENVIRONMENT = "runner_environment"; // "github-hosted"; for self-hosted GH runners this claim may not be included
    private static final String CLAIM_WORKFLOW_REF = "workflow_ref"; // "octo-org/octo-automation/.github/workflows/oidc.yml@refs/heads/main"
    private static final String CLAIM_JOB_WORKFLOW_REF = "job_workflow_ref"; // the executing (possibly reusable) workflow; recorded only
    private static final String CLAIM_RUN_ID = "run_id"; // "1658821493"; recorded only
    private static final String CLAIM_SHA = "sha"; // commit the run was for; recorded only

    private static final String REG_OWNER = "owner";
    private static final String REG_REPO = "repo";
    private static final String REG_WORKFLOW = "workflow";
    private static final String REG_ENVIRONMENT = "environment";
    private static final String REG_REPOSITORY_ID = "repository_id";
    private static final String REG_OWNER_ID = "owner_id";
    private static final List<TrustedPublisherInputJson> REGISTRATION_INPUTS = List.of(
            TrustedPublisherInputJson.create(REG_OWNER, "Organization or User name", false),
            TrustedPublisherInputJson.create(REG_REPO, "Repository name", false),
            TrustedPublisherInputJson.create(REG_WORKFLOW, "Workflow filename", false),
            TrustedPublisherInputJson.create(REG_ENVIRONMENT, "Environment name (optional)", true),
            TrustedPublisherInputJson
                    .create(REG_REPOSITORY_ID, "Repository ID (optional, for private repositories)", true),
            TrustedPublisherInputJson.create(REG_OWNER_ID, "Owner ID (optional, for private repositories)", true));

    private final String apiResolveRequest;

    protected GitHubTrustedPublishingProviderSupport(
            TrustedPublishingConfig config,
            String providerId,
            String providerName,
            String providerUrl,
            String oidcIssuer,
            String apiResolveRequest
    ) {
        super(config, providerId, providerName, providerUrl, oidcIssuer, REGISTRATION_INPUTS);
        this.apiResolveRequest = requireNonNull(apiResolveRequest);
    }

    @NonNull
    @Override
    protected Map<String, String> extractClaims(Jwt jwt) {
        requireNonNull(jwt);
        HashMap<String, String> result = new HashMap<>(10);
        mustClaim(jwt, JwtClaimNames.SUB, result);
        mustClaim(jwt, CLAIM_REPOSITORY, result);
        mustClaim(jwt, CLAIM_REPOSITORY_ID, result);
        mustClaim(jwt, CLAIM_REPOSITORY_OWNER, result);
        mustClaim(jwt, CLAIM_REPOSITORY_OWNER_ID, result);
        mayClaim(jwt, CLAIM_ENVIRONMENT, result);
        mayClaim(jwt, CLAIM_RUNNER_ENVIRONMENT, result);
        mustClaim(jwt, CLAIM_WORKFLOW_REF, result);
        mayClaim(jwt, CLAIM_JOB_WORKFLOW_REF, result);
        mayClaim(jwt, CLAIM_RUN_ID, result);
        mayClaim(jwt, CLAIM_SHA, result);
        return result;
    }

    @NonNull
    @Override
    protected Map<String, String> extractRequest(Map<String, String> registration) throws ErrorResultException {
        requireNonNull(registration);

        final String owner = mustRegister(registration, REG_OWNER);
        final String repo = mustRegister(registration, REG_REPO);
        final String workflow = mustRegister(registration, REG_WORKFLOW);
        final String environment = registration.get(REG_ENVIRONMENT);

        final String repositoryId;
        final String ownerId;
        final String givenRepositoryId = blankToNull(registration.get(REG_REPOSITORY_ID));
        final String givenOwnerId = blankToNull(registration.get(REG_OWNER_ID));
        if (givenRepositoryId != null || givenOwnerId != null) {
            // Explicit IDs skip the unauthenticated lookup, which cannot see private repositories.
            // A wrong ID never matches an OIDC token, so it fails closed.
            repositoryId = normalizeId(givenRepositoryId);
            ownerId = normalizeId(givenOwnerId);
            if (repositoryId == null || ownerId == null) {
                throw new ErrorResultException("Malformed registration request");
            }
        } else {
            Map<String, Object> response = resolve(owner, repo);
            if (response == null || !(response.get("id") instanceof Number resolvedRepositoryId)
                    || !(response.get("owner") instanceof Map<?, ?> ownerMap)
                    || !(ownerMap.get("id") instanceof Number resolvedOwnerId)) {
                throw new ErrorResultException(
                        "Unexpected GitHub response for repository "
                                + owner + "/" + repo);
            }
            repositoryId = String.valueOf(resolvedRepositoryId.longValue());
            ownerId = String.valueOf(resolvedOwnerId.longValue());
        }

        HashMap<String, String> result = new HashMap<>();
        result.put(CLAIM_REPOSITORY, owner + "/" + repo);
        result.put(CLAIM_REPOSITORY_ID, repositoryId);
        result.put(CLAIM_REPOSITORY_OWNER, owner);
        result.put(CLAIM_REPOSITORY_OWNER_ID, ownerId);
        // registered without the "@<ref>" part: publishing is trusted regardless of branch or tag
        result.put(
                CLAIM_WORKFLOW_REF,
                owner + "/" + repo
                        + "/.github/workflows/" + workflow);
        if (environment != null) {
            result.put(CLAIM_ENVIRONMENT, environment);
        }
        return result;
    }

    /**
     * Pulled out for testability; is mocked in UT to prevent real remote access.
     */
    protected Map<String, Object> resolve(String owner, String repo) throws ErrorResultException {
        try {
            return restClient.get()
                    .uri(apiResolveRequest, owner, repo)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException e) {
            throw new ErrorResultException(
                    "Could not resolve GitHub repository "
                            + owner + "/" + repo,
                    e);
        }
    }

    @Override
    public boolean matches(@NonNull Map<String, String> registered, @NonNull Map<String, String> token) {
        requireNonNull(registered);
        requireNonNull(token);
        return claimEquals(CLAIM_REPOSITORY_ID, registered, token)
                && claimEquals(CLAIM_REPOSITORY_OWNER_ID, registered, token)
                && registered.get(CLAIM_WORKFLOW_REF) != null
                && registered.get(CLAIM_WORKFLOW_REF).equals(stripRef(token.get(CLAIM_WORKFLOW_REF)))
                && pinnedClaimMatches(CLAIM_ENVIRONMENT, registered, token);
    }
}
