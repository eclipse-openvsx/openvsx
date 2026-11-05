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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.trustedpublishing.github.GitHubTrustedPublishingProvider;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A private repository cannot be resolved without credentials, so it is registered pending and bound to the IDs
 * of the first verified token that presents the registration code.
 */
@ExtendWith(MockitoExtension.class)
class TrustedPublishingPendingRegistrationTest {

    private static final String NAMESPACE = "foo";
    private static final String EXTENSION = "bar";
    private static final Map<String, String> REGISTRATION = Map
            .of("owner", "acme", "repo", "private", "workflow", "release.yml");
    // what GitHub puts into the token of that repository's workflow
    private static final Map<String, String> TOKEN_CLAIMS = Map.of(
            "sub",
            "repo:acme/private:ref:refs/heads/main",
            "repository",
            "acme/private",
            "repository_id",
            "42",
            "repository_owner",
            "acme",
            "repository_owner_id",
            "7",
            "workflow_ref",
            "acme/private/.github/workflows/release.yml@refs/heads/main");

    @Mock
    TrustedPublishingConfig config;

    @Mock
    RepositoryService repositories;

    @Mock
    AccessTokenService tokens;

    @Mock
    EntityManager entityManager;

    private final UserData user = new UserData();
    private final Namespace namespace = new Namespace();
    private final Extension extension = new Extension();
    private GitHubTrustedPublishingProvider provider;
    private TrustedPublishingService service;
    private String idToken;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        namespace.setId(2L);
        namespace.setName(NAMESPACE);
        extension.setId(3L);
        extension.setName(EXTENSION);
        extension.setNamespace(namespace);
        lenient().when(config.isEnabled()).thenReturn(true);
        lenient().when(config.getActiveProviders()).thenReturn(List.of("github"));
        lenient().when(config.getTokenExpiration()).thenReturn(Duration.ofMinutes(5));
        lenient().when(repositories.findNamespace(NAMESPACE)).thenReturn(namespace);
        lenient().when(repositories.isNamespaceOwner(user, namespace)).thenReturn(true);
        lenient().when(repositories.findActiveExtension(EXTENSION, NAMESPACE)).thenReturn(extension);
        lenient().when(entityManager.find(UserData.class, 1L)).thenReturn(user);

        provider = new GitHubTrustedPublishingProvider(config) {
            @Override
            protected Map<String, Object> resolve(String owner, String repo) {
                return null; // private: GitHub answers 404 without credentials
            }

            @Override
            public Optional<Map<String, String>> extract(String oidcId) {
                return Optional.of(TOKEN_CLAIMS);
            }
        };
        service = new TrustedPublishingService(config, repositories, tokens, entityManager, Map.of("github", provider));
        idToken = new PlainJWT(new JWTClaimsSet.Builder().issuer(provider.getOidcIssuer()).build()).serialize();
    }

    private TrustedPublisher register() {
        when(repositories.findTrustedPublishersByExtension(extension)).thenReturn(Streamable.empty());
        var publisher = service.registerTrustedPublisher(user, NAMESPACE, EXTENSION, "github", REGISTRATION);
        var persisted = ArgumentCaptor.forClass(TrustedPublisher.class);
        verify(entityManager).persist(persisted.capture());
        assertThat(persisted.getValue()).isSameAs(publisher);
        lenient().when(repositories.findTrustedPublishersByExtension(extension)).thenReturn(Streamable.of(publisher));
        return publisher;
    }

    @Test
    void registersAPrivateRepositoryPendingWithAOneTimeCode() {
        var publisher = register();

        assertThat(publisher.isPending()).isTrue();
        assertThat(publisher.getClaims()).doesNotContainKeys("repository_id", "repository_owner_id");
        assertThat(publisher.getRegistrationCode()).hasSizeGreaterThanOrEqualTo(32);
        // only a hash is stored, and the code is expired after a week
        assertThat(publisher.getRegistrationCodeHash()).isNotEqualTo(publisher.getRegistrationCode()).hasSize(64);
        assertThat(publisher.getRegistrationCodeExpiry())
                .isBetween(
                        TimeUtil.getCurrentUTC().plusDays(7).minusMinutes(1),
                        TimeUtil.getCurrentUTC().plusDays(7).plusMinutes(1));
        var json = publisher.toJson();
        assertThat(json.getPending()).isTrue();
        assertThat(json.getRegistrationCode()).isEqualTo(publisher.getRegistrationCode());
    }

    @Test
    void bindsToTheIdsOfTheFirstVerifiedTokenPresentingTheCode() {
        var publisher = register();
        var code = publisher.getRegistrationCode();

        service.requestPublishToken(NAMESPACE, EXTENSION, idToken, code);

        assertThat(publisher.isPending()).isFalse();
        assertThat(publisher.getRegistrationCodeExpiry()).isNull();
        assertThat(publisher.getClaims()).containsEntry("repository_id", "42")
                .containsEntry("repository_owner_id", "7");
        verify(tokens).createTrustedPublishingAccessToken(any(), anyString(), any(), any());
        // listings never show the code again
        assertThat(publisher.toJson().getRegistrationCode()).isNull();
    }

    @Test
    void acceptsAMatchingExchangeThatLostTheBindingRace() {
        var publisher = register();
        bindOnRefresh(publisher, "42");

        service.requestPublishToken(NAMESPACE, EXTENSION, idToken, publisher.getRegistrationCode());

        verify(tokens).createTrustedPublishingAccessToken(any(), anyString(), any(), any());
    }

    @Test
    void rejectsAConflictingExchangeThatLostTheBindingRace() {
        var publisher = register();
        var code = publisher.getRegistrationCode();
        bindOnRefresh(publisher, "99");

        assertThatThrownBy(() -> service.requestPublishToken(NAMESPACE, EXTENSION, idToken, code))
                .isInstanceOfSatisfying(
                        ErrorResultException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(publisher.getClaims()).containsEntry("repository_id", "99");
        verify(tokens, never()).createTrustedPublishingAccessToken(any(), anyString(), any(), any());
    }

    private void bindOnRefresh(TrustedPublisher publisher, String repositoryId) {
        doAnswer(invocation -> {
            var claims = new java.util.HashMap<>(publisher.getClaims());
            claims.put("repository_id", repositoryId);
            claims.put("repository_owner_id", "7");
            publisher.setClaims(claims);
            publisher.setRegistrationCodeHash(null);
            publisher.setRegistrationCodeExpiry(null);
            return null;
        })
                .when(entityManager)
                .refresh(eq(publisher), any(LockModeType.class));
    }

    @Test
    void doesNotIssueATokenWithoutTheCodeOrWithAWrongOne() {
        var publisher = register();

        for (var code : new String[] { null, "", "not-the-code" }) {
            assertThatThrownBy(() -> service.requestPublishToken(NAMESPACE, EXTENSION, idToken, code))
                    .isInstanceOfSatisfying(
                            ErrorResultException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        assertThat(publisher.isPending()).isTrue();
        assertThat(publisher.getClaims()).doesNotContainKey("repository_id");
        verify(tokens, never()).createTrustedPublishingAccessToken(any(), anyString(), any(), any());
    }

    @Test
    void doesNotBindWhenTheNamesDoNotMatchTheToken() {
        var publisher = register();
        var other = new java.util.HashMap<>(TOKEN_CLAIMS);
        other.put("workflow_ref", "acme/private/.github/workflows/other.yml@refs/heads/main");
        var otherProvider = new GitHubTrustedPublishingProvider(config) {
            @Override
            public Optional<Map<String, String>> extract(String oidcId) {
                return Optional.of(other);
            }
        };
        var otherService = new TrustedPublishingService(
                config,
                repositories,
                tokens,
                entityManager,
                Map.of("github", otherProvider));

        assertThatThrownBy(
                () -> otherService.requestPublishToken(NAMESPACE, EXTENSION, idToken, publisher.getRegistrationCode()))
                .isInstanceOf(ErrorResultException.class);

        assertThat(publisher.isPending()).isTrue();
    }

    @Test
    void doesNotBindAfterTheCodeExpired() {
        var publisher = register();
        publisher.setRegistrationCodeExpiry(TimeUtil.getCurrentUTC().minusMinutes(1));

        assertThatThrownBy(
                () -> service.requestPublishToken(NAMESPACE, EXTENSION, idToken, publisher.getRegistrationCode()))
                .isInstanceOfSatisfying(
                        ErrorResultException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(publisher.getClaims()).doesNotContainKey("repository_id");
    }

    @Test
    void registeringAgainReplacesAnExpiredPendingRegistrationOnly() {
        var expired = new TrustedPublisher();
        expired.setExtension(extension);
        expired.setRegistrationCodeHash("hash");
        expired.setRegistrationCodeExpiry(TimeUtil.getCurrentUTC().minusDays(1));
        when(repositories.findTrustedPublishersByExtension(extension)).thenReturn(Streamable.of(expired));

        var replacement = service.registerTrustedPublisher(user, NAMESPACE, EXTENSION, "github", REGISTRATION);

        verify(repositories).deleteTrustedPublisher(expired);
        assertThat(replacement.isPending()).isTrue();

        // while a live pending registration stays in the way, like any other
        var live = new TrustedPublisher();
        live.setExtension(extension);
        live.setRegistrationCodeHash("hash");
        live.setRegistrationCodeExpiry(TimeUtil.getCurrentUTC().plusDays(1));
        when(repositories.findTrustedPublishersByExtension(extension)).thenReturn(Streamable.of(live));
        assertThatThrownBy(() -> service.registerTrustedPublisher(user, NAMESPACE, EXTENSION, "github", REGISTRATION))
                .isInstanceOf(ErrorResultException.class);
    }

    @Test
    void deletesTheExpiredPendingRegistrations() {
        when(repositories.deleteExpiredPendingTrustedPublishers(any())).thenReturn(2);

        assertThat(service.deleteExpiredPendingRegistrations()).isEqualTo(2);

        verify(repositories).deleteExpiredPendingTrustedPublishers(any());
    }
}
