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
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
class GitHubTrustedPublishingProviderTest {

    @Autowired
    TrustedPublishingConfig config;

    private static Map<String, String> tokenClaims() {
        var claims = new HashMap<String, String>();
        claims.put("sub", "repo:eclipse-openvsx/openvsx:ref:refs/heads/main");
        claims.put("repository", "eclipse-openvsx/openvsx");
        claims.put("repository_id", "226955212");
        claims.put("repository_owner", "eclipse-openvsx");
        claims.put("repository_owner_id", "163524810");
        claims.put("runner_environment", "github-hosted");
        claims.put("workflow_ref", "eclipse-openvsx/openvsx/.github/workflows/release.yml@refs/heads/main");
        return claims;
    }

    private static Map<String, String> registeredClaims() {
        var claims = new HashMap<String, String>();
        claims.put("repository", "eclipse-openvsx/openvsx");
        claims.put("repository_id", "226955212");
        claims.put("repository_owner", "eclipse-openvsx");
        claims.put("repository_owner_id", "163524810");
        claims.put("workflow_ref", "eclipse-openvsx/openvsx/.github/workflows/release.yml");
        return claims;
    }

    private static GitHubTrustedPublishingProvider newProvider(TrustedPublishingConfig config) {
        return new GitHubTrustedPublishingProvider(config) {
            @Override
            protected Map<String, Object> resolve(String owner, String repo) {
                return Map.of(
                        "id",
                        226955212,
                        "owner",
                        Map.of("id", 163524810));
            }
        };
    }

    @Test
    void trustRequestWithoutEnv() throws Exception {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        Map<String, String> data = gh.extractRequest(
                Map.of(
                        "owner",
                        "eclipse-openvsx",
                        "repo",
                        "openvsx",
                        "workflow",
                        "release.yml"));
        assertEquals("eclipse-openvsx", data.get("repository_owner"));
        assertEquals("163524810", data.get("repository_owner_id"));
        assertEquals("eclipse-openvsx/openvsx", data.get("repository"));
        assertEquals("226955212", data.get("repository_id"));
        assertEquals("eclipse-openvsx/openvsx/.github/workflows/release.yml", data.get("workflow_ref"));
        assertEquals(5, data.size());
    }

    @Test
    void trustRequestWithEnv() throws Exception {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        Map<String, String> data = gh.extractRequest(
                Map.of(
                        "owner",
                        "eclipse-openvsx",
                        "repo",
                        "openvsx",
                        "workflow",
                        "release.yml",
                        "environment",
                        "prod"));
        assertEquals("eclipse-openvsx", data.get("repository_owner"));
        assertEquals("163524810", data.get("repository_owner_id"));
        assertEquals("eclipse-openvsx/openvsx", data.get("repository"));
        assertEquals("226955212", data.get("repository_id"));
        assertEquals("eclipse-openvsx/openvsx/.github/workflows/release.yml", data.get("workflow_ref"));
        assertEquals("prod", data.get("environment"));
        assertEquals(6, data.size());
    }

    @Test
    void matchesRegardlessOfRef() {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        var token = tokenClaims();
        assertTrue(gh.matches(registeredClaims(), token));
        token.put("workflow_ref", "eclipse-openvsx/openvsx/.github/workflows/release.yml@refs/tags/v1.0.0");
        assertTrue(gh.matches(registeredClaims(), token));
    }

    @Test
    void mismatchOnDifferentRepositoryId() {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        var token = tokenClaims();
        token.put("repository_id", "1"); // resurrection attack: same name, different repository
        assertFalse(gh.matches(registeredClaims(), token));
    }

    @Test
    void mismatchOnDifferentWorkflow() {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        var token = tokenClaims();
        token.put("workflow_ref", "eclipse-openvsx/openvsx/.github/workflows/other.yml@refs/heads/main");
        assertFalse(gh.matches(registeredClaims(), token));
    }

    @Test
    void pinnedEnvironment() {
        GitHubTrustedPublishingProvider gh = newProvider(config);
        var registered = registeredClaims();
        registered.put("environment", "prod");

        var token = tokenClaims();
        assertFalse(gh.matches(registered, token)); // token not from any environment
        token.put("environment", "staging");
        assertFalse(gh.matches(registered, token));
        token.put("environment", "prod");
        assertTrue(gh.matches(registered, token));
        // and with no pinned environment, any environment is accepted
        assertTrue(gh.matches(registeredClaims(), token));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        TrustedPublishingConfig trustedPublishingConfig() {
            return new TrustedPublishingConfig();
        }
    }
}
