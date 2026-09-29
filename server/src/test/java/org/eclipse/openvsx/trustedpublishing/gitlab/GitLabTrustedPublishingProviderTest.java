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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
class GitLabTrustedPublishingProviderTest {

    @Autowired
    TrustedPublishingConfig config;

    private static Map<String, String> tokenClaims() {
        var claims = new HashMap<String, String>();
        claims.put("sub", "project_path:gitlab-org/gitlab:ref_type:branch:ref:master");
        claims.put("namespace_id", "9970");
        claims.put("namespace_path", "gitlab-org");
        claims.put("project_id", "278964");
        claims.put("project_path", "gitlab-org/gitlab");
        claims.put("runner_environment", "gitlab-hosted");
        claims.put("ci_config_ref_uri", "gitlab.com/gitlab-org/gitlab//.gitlab-ci.yml@refs/heads/master");
        return claims;
    }

    private static Map<String, String> registeredClaims() {
        var claims = new HashMap<String, String>();
        claims.put("namespace_id", "9970");
        claims.put("namespace_path", "gitlab-org");
        claims.put("project_id", "278964");
        claims.put("project_path", "gitlab-org/gitlab");
        claims.put("ci_config_ref_uri", "gitlab.com/gitlab-org/gitlab//.gitlab-ci.yml");
        return claims;
    }

    private static GitLabTrustedPublishingProvider newProvider(TrustedPublishingConfig config) {
        return newProvider(
                config,
                GitLabTrustedPublishingProvider.PROVIDER_ID,
                GitLabTrustedPublishingProvider.PROVIDER_URL);
    }

    private static GitLabTrustedPublishingProvider newProvider(
            TrustedPublishingConfig config,
            String providerId,
            String providerUrl
    ) {
        return new GitLabTrustedPublishingProvider(config, providerId, "GitLab", providerUrl, providerUrl) {
            @Override
            protected Map<String, Object> resolve(String projectPath) {
                return Map.of(
                        "id",
                        278964,
                        "namespace",
                        Map.of("id", 9970));
            }
        };
    }

    @Test
    void trustRequestWithoutEnv() throws Exception {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        Map<String, String> data = gl.extractRequest(
                Map.of(
                        "namespace",
                        "gitlab-org",
                        "project",
                        "gitlab",
                        "workflow",
                        ".gitlab-ci.yml"));
        assertEquals("9970", data.get("namespace_id"));
        assertEquals("gitlab-org", data.get("namespace_path"));
        assertEquals("278964", data.get("project_id"));
        assertEquals("gitlab-org/gitlab", data.get("project_path"));
        assertEquals("gitlab.com/gitlab-org/gitlab//.gitlab-ci.yml", data.get("ci_config_ref_uri"));
        assertEquals(5, data.size());
    }

    @Test
    void trustRequestWithEnv() throws Exception {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        Map<String, String> data = gl.extractRequest(
                Map.of(
                        "namespace",
                        "gitlab-org",
                        "project",
                        "gitlab",
                        "workflow",
                        ".gitlab-ci.yml",
                        "environment",
                        "prod"));
        assertEquals("9970", data.get("namespace_id"));
        assertEquals("gitlab-org", data.get("namespace_path"));
        assertEquals("278964", data.get("project_id"));
        assertEquals("gitlab-org/gitlab", data.get("project_path"));
        assertEquals("gitlab.com/gitlab-org/gitlab//.gitlab-ci.yml", data.get("ci_config_ref_uri"));
        assertEquals("prod", data.get("environment"));
        assertEquals(6, data.size());
    }

    @Test
    void matchesRegardlessOfRef() {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        var token = tokenClaims();
        assertTrue(gl.matches(registeredClaims(), token));
        token.put("ci_config_ref_uri", "gitlab.com/gitlab-org/gitlab//.gitlab-ci.yml@refs/tags/v1.0.0");
        assertTrue(gl.matches(registeredClaims(), token));
    }

    @Test
    void mismatchOnDifferentProjectId() {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        var token = tokenClaims();
        token.put("project_id", "1"); // resurrection attack: same path, different project
        assertFalse(gl.matches(registeredClaims(), token));
    }

    @Test
    void mismatchOnDifferentCiConfig() {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        var token = tokenClaims();
        token.put("ci_config_ref_uri", "gitlab.com/gitlab-org/gitlab//other-ci.yml@refs/heads/master");
        assertFalse(gl.matches(registeredClaims(), token));
    }

    @Test
    void pinnedEnvironment() {
        GitLabTrustedPublishingProvider gl = newProvider(config);
        var registered = registeredClaims();
        registered.put("environment", "prod");

        var token = tokenClaims();
        assertFalse(gl.matches(registered, token)); // token not from any environment
        token.put("environment", "staging");
        assertFalse(gl.matches(registered, token));
        token.put("environment", "prod");
        assertTrue(gl.matches(registered, token));
        // and with no pinned environment, any environment is accepted
        assertTrue(gl.matches(registeredClaims(), token));
    }

    @Test
    void selfHostedInstanceIsAddressedByItsOwnUrl() throws Exception {
        GitLabTrustedPublishingProvider gl = newProvider(config, "acme-gitlab", "https://gitlab.acme.example");
        Map<String, String> data = gl.extractRequest(
                Map.of(
                        "namespace",
                        "gitlab-org",
                        "project",
                        "gitlab",
                        "workflow",
                        ".gitlab-ci.yml"));
        assertEquals("gitlab.acme.example/gitlab-org/gitlab//.gitlab-ci.yml", data.get("ci_config_ref_uri"));
        assertEquals("acme-gitlab", gl.getProviderId());
    }

    @Test
    void instanceServedUnderARelativeUrlRootKeepsItsPath() throws Exception {
        GitLabTrustedPublishingProvider gl = newProvider(config, "acme-gitlab", "https://acme.example/gitlab/");
        Map<String, String> data = gl.extractRequest(
                Map.of(
                        "namespace",
                        "gitlab-org",
                        "project",
                        "gitlab",
                        "workflow",
                        ".gitlab-ci.yml"));
        assertEquals("acme.example/gitlab/gitlab-org/gitlab//.gitlab-ci.yml", data.get("ci_config_ref_uri"));
    }

    @Test
    void malformedInstanceUrlIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> newProvider(config, "acme-gitlab", "not-a-url"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        TrustedPublishingConfig trustedPublishingConfig() {
            return new TrustedPublishingConfig();
        }
    }
}
