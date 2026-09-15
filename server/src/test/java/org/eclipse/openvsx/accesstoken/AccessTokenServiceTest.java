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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.apache.commons.codec.digest.DigestUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenServiceTest {

    @Mock
    AccessTokenConfig config;

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    @Mock
    MailService mail;

    @Mock
    DSLContext dsl;

    @InjectMocks
    AccessTokenService accessTokenService;

    @BeforeEach
    void setUp() {
        // lenient: generateTokenValue does not hash anything, so it needs neither of these
        lenient().when(config.getTokenHashAlgorithm()).thenReturn("SHA-256");
        lenient().when(config.getTokenHashPepper()).thenReturn("pepper");
        // no rotation in progress unless a test says otherwise
        lenient().when(config.getTokenHashPepperKeyring()).thenReturn(List.of());
    }

    /** The hash the service stores for a token, so a test can set up the row it expects to be found. */
    private static String hashed(String tokenValue, String pepper) {
        return DigestUtils.sha256Hex((tokenValue + pepper).getBytes(StandardCharsets.UTF_8));
    }

    private PersonalAccessToken activeUnrestrictedToken() {
        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setType(PersonalAccessTokenType.LLT);
        token.setVersion(1);
        return token;
    }

    // #989: deactivateAccessToken used to call entityManager.merge(user) so that `user` became the
    // same managed instance as token.getUser() and UserData#equals - which compares every field,
    // tokens and memberships included - could short-circuit on ==. The merge wrote the caller's
    // whole user row back as a side effect. These pin down that the ownership check now goes by id,
    // so it neither writes through the EntityManager nor depends on the caller's copy of the user
    // matching the stored row field for field.
    @Test
    void deactivatesATokenForItsOwnerWithoutWritingTheUserBack() {
        var owner = new UserData();
        owner.setId(1L);
        owner.setLoginName("owner");
        var token = activeUnrestrictedToken();
        token.setUser(owner);
        when(repositories.findPersonalAccessToken(7L)).thenReturn(token);

        // The caller's copy carries stale fields, exactly what merge would have written back.
        var stale = new UserData();
        stale.setId(1L);
        stale.setLoginName("owner");
        stale.setFullName("a stale full name");

        var result = accessTokenService.deactivateAccessToken(stale, 7L);

        assertThat(result.getError()).isNull();
        assertThat(token.isActive()).isFalse();
        verifyNoInteractions(entityManager);
    }

    @Test
    void refusesToDeactivateATokenBelongingToSomebodyElse() {
        var owner = new UserData();
        owner.setId(1L);
        var token = activeUnrestrictedToken();
        token.setUser(owner);
        when(repositories.findPersonalAccessToken(7L)).thenReturn(token);

        var other = new UserData();
        other.setId(2L);

        assertThatThrownBy(() -> accessTokenService.deactivateAccessToken(other, 7L))
                .isInstanceOf(NotFoundException.class);
        assertThat(token.isActive()).isTrue();
    }

    // personal_access_token.user_data is nullable, so an ownerless row must not be deactivatable by
    // whoever asks - and must not NPE on the way to refusing.
    @Test
    void refusesToDeactivateATokenWithNoUser() {
        var token = activeUnrestrictedToken();
        token.setUser(null);
        when(repositories.findPersonalAccessToken(7L)).thenReturn(token);

        var user = new UserData();
        user.setId(1L);

        assertThatThrownBy(() -> accessTokenService.deactivateAccessToken(user, 7L))
                .isInstanceOf(NotFoundException.class);
        assertThat(token.isActive()).isTrue();
    }

    @Test
    void authenticatesWithTheTokensUser() {
        var user = new UserData();
        var token = activeUnrestrictedToken();
        token.setUser(user);
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify());

        assertThat(tau).isNotNull();
        assertThat(tau.userData()).isSameAs(user);
        assertThat(tau.type()).isEqualTo(PersonalAccessTokenType.LLT);
    }

    // Regression: personal_access_token.user_data has no NOT NULL constraint at the schema level.
    // A row with no user attached used to come back as a "successfully authenticated" (non-null)
    // AccessTokenAuthentication wrapping a null userData(), which every caller dereferences
    // unguarded (e.g. LocalRegistryService#createNamespace/deleteExtension/verifyToken), turning a
    // legacy/corrupt token row into an unhandled NPE instead of the intended auth failure.
    @Test
    void rejectsATokenWithNoUser() {
        var token = activeUnrestrictedToken();
        token.setUser(null);
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify());

        assertThat(tau).isNull();
    }

    // The upgrade job is enqueued from every pod's own ApplicationStartedEvent, so the advisory lock is
    // what keeps one rolling update from having each of them scan and rewrite the same rows.
    @Test
    void skipsTheTokenUpgradeWhenAnotherInstanceHoldsTheLock() {
        var service = Mockito.spy(accessTokenService);
        Mockito.doReturn(false).when(service).tryAcquireUpgradeLock();

        assertThat(service.upgradeTokens()).isZero();

        verifyNoInteractions(repositories);
    }

    @Test
    void upgradesTokensWhenItWinsTheLock() {
        var service = Mockito.spy(accessTokenService);
        Mockito.doReturn(true).when(service).tryAcquireUpgradeLock();
        var legacy = new PersonalAccessToken();
        legacy.setVersion(0);
        legacy.setValue("raw");
        when(repositories.findAllPersonalAccessTokensByVersion(0)).thenReturn(Streamable.of(legacy));

        assertThat(service.upgradeTokens()).isEqualTo(1);

        assertThat(legacy.getVersion()).isEqualTo(1);
        assertThat(legacy.getValue()).isNotEqualTo("raw");
    }

    // The old implementation regenerated until repositories.hasPersonalAccessToken(value) came back
    // false, comparing a raw value against a column that stores peppered hashes - it could never match
    // for a current token, so it only cost a query per token created. Uniqueness is UNIQUE (value)'s job,
    // and only it can work across pods anyway.
    @Test
    void generatesATokenValueWithoutAskingTheDatabase() {
        when(config.getPrefix()).thenReturn("ovsx");

        var value = accessTokenService.generateTokenValue(PersonalAccessTokenType.LLT);

        // prefix, then the marker saying what kind of token this is, then 256 bits base62 encoded
        assertThat(value).startsWith("ovsxat_");
        assertThat(value.substring("ovsxat_".length())).matches("[0-9A-Za-z]{43}");
        verifyNoInteractions(repositories);
    }

    // Nothing in the body may look like the marker's own delimiter, so that what follows the marker
    // reads as one word. Base64url put a further - or _ at a random place in about three of every four
    // bodies, which made the marker look longer than it is. The configured prefix ahead of the marker
    // may contain an underscore of its own; that one is fixed and recognisable, so it does not blur
    // where the marker ends.
    @Test
    void keepsTheBodyFreeOfTheCharactersThatMarkTheTokenType() {
        when(config.getPrefix()).thenReturn("ovsx");

        var values = java.util.stream.Stream.generate(
                () -> accessTokenService.generateTokenValue(PersonalAccessTokenType.LLT)).limit(500).toList();

        assertThat(values).allSatisfy(
                value -> assertThat(value.substring("ovsxat_".length()))
                        .doesNotContain("_", "-", "=", "+", "/"));
    }

    @Test
    void marksEachKindOfTokenDistinctly() {
        when(config.getPrefix()).thenReturn("ovsx");

        assertThat(accessTokenService.generateTokenValue(PersonalAccessTokenType.TPT)).startsWith("ovsxtp_");
        assertThat(accessTokenService.generateTokenValue(PersonalAccessTokenType.LLT)).startsWith("ovsxat_");
    }

    @Test
    void generatesADifferentValueEveryTime() {
        when(config.getPrefix()).thenReturn("");

        var values = java.util.stream.Stream.generate(
                () -> accessTokenService.generateTokenValue(PersonalAccessTokenType.LLT)).limit(100).toList();

        assertThat(values).doesNotHaveDuplicates();
    }

    // How long a publishing token lives is the trusted publishing configuration's to decide, so this
    // service applies whatever it is handed instead of reading a setting of its own.
    @Test
    void appliesTheExpirationItIsGivenToATrustedPublishingToken() {
        var user = new UserData();
        var namespace = new Namespace();
        namespace.setName("foo");
        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);
        var trustedPublisher = new TrustedPublisher();
        trustedPublisher.setExtension(extension);
        trustedPublisher.setCreatedBy(user);
        trustedPublisher.setRegistration(Map.of());
        when(config.getPrefix()).thenReturn("ovsx");

        var before = LocalDateTime.now(ZoneId.of("UTC"));
        var json = accessTokenService
                .createTrustedPublishingAccessToken(
                        trustedPublisher,
                        "Trusted publishing (github)",
                        Duration.ofMinutes(7),
                        Map.of("repository_id", "74"));
        var after = LocalDateTime.now(ZoneId.of("UTC"));

        var persisted = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(entityManager).persist(persisted.capture());
        assertThat(persisted.getValue().getType()).isEqualTo(PersonalAccessTokenType.TPT);
        assertThat(persisted.getValue().getExpiresTimestamp())
                .isBetween(before.plusMinutes(7), after.plusMinutes(7));
        // the token is scoped to the registration's extension, whatever its lifetime
        assertThat(persisted.getValue().getScopeExtension()).isSameAs(extension);
        // carried to the publish that uses this token, which copies them onto the version
        assertThat(persisted.getValue().getClaims()).containsEntry("repository_id", "74");
        assertThat(json.getValue()).isNotNull();
    }

    private PersonalAccessToken trustedPublishingToken(UserData user) {
        var namespace = new Namespace();
        namespace.setName("foo");
        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);
        var trustedPublisher = new TrustedPublisher();
        trustedPublisher.setExtension(extension);
        trustedPublisher.setCreatedBy(user);
        trustedPublisher.setRegistration(Map.of());

        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setType(PersonalAccessTokenType.TPT);
        token.setVersion(1);
        token.setUser(user);
        token.setTrustedPublisher(trustedPublisher);
        token.setScopeExtension(extension);
        return token;
    }

    @Test
    void refusesTrustedPublishingTokenForNonPublishing() {
        var user = new UserData();
        var token = trustedPublishingToken(user);
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        var tau = accessTokenService
                .useAccessToken("tok", new AccessTokenAction.DeleteVersion("foo", "bar"));

        // is null; while token is valid, is not allowed for delete action
        assertThat(tau).isNull();
        // was not "used"; TPT token was not deleted
        verify(entityManager, never()).remove(any());
    }

    // A release commonly publishes one version per target platform, and each is its own publish request.
    // The CLI exchanges the CI identity once and shares the token across them (cli/src/trusted-publishing.ts
    // caches it per extension), so consuming it on first use failed every target but one - and since the
    // CLI publishes them concurrently, which ones failed was down to timing.
    @Test
    void keepsATrustedPublishingTokenUsableForEveryTargetPlatformOfARelease() {
        var user = new UserData();
        var token = trustedPublishingToken(user);
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        for (var i = 0; i < 3; i++) {
            var tau = accessTokenService
                    .useAccessToken("tok", new AccessTokenAction.PublishVersion("foo", "bar"));

            assertThat(tau).isNotNull();
            assertThat(tau.type()).isEqualTo(PersonalAccessTokenType.TPT);
        }

        verify(entityManager, never()).remove(any());
    }

    // The deprecated one-time type keeps its meaning: the split of one-time from ephemeral was to let a
    // trusted publishing token outlive a single request, not to make every machine token reusable.
    @Test
    void stillConsumesAOneTimeTokenOnFirstUse() {
        var token = activeUnrestrictedToken();
        token.setType(PersonalAccessTokenType.OTT);
        token.setUser(new UserData());
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.PublishVersion("foo", "bar"));

        assertThat(tau).isNotNull();
        verify(entityManager).remove(token);
    }

    // Reusable until it expires, but not past it - and the row goes rather than lingering deactivated,
    // because it carries the OIDC claims of the workflow that obtained it.
    @Test
    void deletesAnExpiredTrustedPublishingTokenRatherThanDeactivatingIt() {
        var token = trustedPublishingToken(new UserData());
        token.setExpiresTimestamp(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(1));
        when(repositories.findPersonalAccessToken(anyString())).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.PublishVersion("foo", "bar"));

        assertThat(tau).isNull();
        verify(entityManager).remove(token);
        assertThat(token.isActive()).isTrue();
    }

    // Nothing ever shows a trusted publishing token to its owner, so there is no page for a revoke link
    // to sit on. Being reusable does not make it user-managed.
    @Test
    void offersNoRevocationLinkForATrustedPublishingToken() {
        var user = new UserData();
        var trustedPublisher = trustedPublishingToken(user).getTrustedPublisher();
        when(config.getPrefix()).thenReturn("ovsx");

        var json = accessTokenService.createTrustedPublishingAccessToken(
                trustedPublisher,
                "Trusted publishing (github)",
                Duration.ofMinutes(5),
                Map.of());

        assertThat(json.getDeleteTokenUrl()).isNull();
    }

    // Pepper rotation: the raw value is never stored, so a row can only move to the new pepper while its
    // holder is presenting the token. Listing the old pepper keeps such a token valid, and the hit
    // rewrites the row so the next lookup matches on the first try.
    @Test
    void authenticatesATokenStillHashedWithAPreviousPepper() {
        when(config.getTokenHashPepperKeyring()).thenReturn(List.of("old-pepper"));
        var user = new UserData();
        var token = activeUnrestrictedToken();
        token.setUser(user);
        token.setValue(hashed("tok", "old-pepper"));
        when(repositories.findPersonalAccessToken(hashed("tok", "pepper"))).thenReturn(null);
        when(repositories.findPersonalAccessToken(hashed("tok", "old-pepper"))).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify());

        assertThat(tau).isNotNull();
        assertThat(tau.userData()).isSameAs(user);
        assertThat(token.getValue()).isEqualTo(hashed("tok", "pepper"));
    }

    // Adopting a pepper on an instance that ran without one: the previous pepper is the empty string,
    // which is why it gets its own flag rather than an unwritable empty entry in the list.
    @Test
    void authenticatesAnUnpepperedTokenWhenAPepperIsAdopted() {
        when(config.getTokenHashPepperKeyring()).thenReturn(List.of(""));
        var user = new UserData();
        var token = activeUnrestrictedToken();
        token.setUser(user);
        token.setValue(hashed("tok", ""));
        when(repositories.findPersonalAccessToken(hashed("tok", "pepper"))).thenReturn(null);
        when(repositories.findPersonalAccessToken(hashed("tok", ""))).thenReturn(token);

        var tau = accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify());

        assertThat(tau).isNotNull();
        assertThat(token.getValue()).isEqualTo(hashed("tok", "pepper"));
    }

    // The keyring is tried in the configured order, and only until something matches.
    @Test
    void stopsAtTheFirstPreviousPepperThatMatches() {
        when(config.getTokenHashPepperKeyring()).thenReturn(List.of("older", "old"));
        var token = activeUnrestrictedToken();
        token.setUser(new UserData());
        token.setValue(hashed("tok", "older"));
        when(repositories.findPersonalAccessToken(hashed("tok", "pepper"))).thenReturn(null);
        when(repositories.findPersonalAccessToken(hashed("tok", "older"))).thenReturn(token);

        assertThat(accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify())).isNotNull();

        verify(repositories).findPersonalAccessToken(hashed("tok", "pepper"));
        verify(repositories).findPersonalAccessToken(hashed("tok", "older"));
        verify(repositories, never()).findPersonalAccessToken(hashed("tok", "old"));
    }

    // A token already on the current pepper must not pay for the rotation: it matches on the first
    // lookup, and neither the keyring nor the v0 fallback is consulted.
    @Test
    void doesNotRetryPreviousPeppersWhenTheCurrentOneMatches() {
        // lenient: this stub going unread is half the assertion - the keyring is never even consulted
        lenient().when(config.getTokenHashPepperKeyring()).thenReturn(List.of("old-pepper"));
        var token = activeUnrestrictedToken();
        token.setUser(new UserData());
        when(repositories.findPersonalAccessToken(hashed("tok", "pepper"))).thenReturn(token);

        assertThat(accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify())).isNotNull();

        verify(repositories).findPersonalAccessToken(hashed("tok", "pepper"));
        verifyNoMoreInteractions(repositories);
    }

    // An unknown token still ends at the v0 raw-value fallback, having tried every configured pepper.
    @Test
    void fallsThroughToTheLegacyLookupWhenNoPepperMatches() {
        when(config.getTokenHashPepperKeyring()).thenReturn(List.of("old-pepper"));

        assertThat(accessTokenService.useAccessToken("tok", new AccessTokenAction.Verify())).isNull();

        verify(repositories).findPersonalAccessToken(hashed("tok", "pepper"));
        verify(repositories).findPersonalAccessToken(hashed("tok", "old-pepper"));
        verify(repositories).findPersonalAccessToken("tok");
    }
}
