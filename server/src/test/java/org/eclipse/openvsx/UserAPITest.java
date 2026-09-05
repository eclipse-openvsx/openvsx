/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.accesstoken.AccessTokenConfig;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.LatestExtensionVersionCacheKeyGenerator;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.eclipse.EclipseTokenService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.migration.MigrationsProperties;
import org.eclipse.openvsx.mirror.MirrorConfig;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishExtensionVersionHandler;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.scanning.NamespaceOwnershipCheckScanner;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.search.SimilarityConfig;
import org.eclipse.openvsx.search.SimilarityService;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.security.OAuth2UserServices;
import org.eclipse.openvsx.security.SecurityConfig;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.UUIDService;
import org.eclipse.openvsx.util.VersionService;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAPI.class)
@MockitoBean(
    types = {
        DSLContext.class,
        EclipseService.class,
        ClientRegistrationRepository.class,
        StorageUtilService.class,
        CacheService.class,
        ExtensionValidator.class,
        SimpleMeterRegistry.class,
        SearchUtilService.class,
        PublishExtensionVersionHandler.class,
        JobRequestScheduler.class,
        VersionService.class,
        ExtensionVersionIntegrityService.class,
        ExtensionScanService.class,
        ExtensionScanPersistenceService.class,
        LogService.class,
        MailService.class
    }
)
class UserAPITest {

    @BeforeEach
    void noTrustedPublishersByDefault() {
        // the real repository hands back an empty Streamable rather than null; only the trusted
        // publishing tests care what it actually holds
        Mockito.when(repositories.findTrustedPublishersByNamespaceAndCreatedBy(any(), any()))
                .thenReturn(Streamable.empty());
    }

    @MockitoSpyBean
    UserService users;

    @MockitoSpyBean
    AccessTokenService tokens;

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    UUIDService uuidService;

    @MockitoBean
    RepositoryService repositories;

    @Autowired
    StorageUtilService storageUtil;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    private AccessTokenService accessTokenService;

    @Test
    void testLoggedIn() throws Exception {
        mockUserData();
        mockMvc.perform(get("/user"))
                .andExpect(status().isOk())
                .andExpect(content().json(userJson(u -> {
                    u.setLoginName("test_user");
                    u.setFullName("Test User");
                    u.setHomepage("http://example.com/test");
                })));
    }

    @Test
    void testNotLoggedIn() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isOk())
                .andExpect(content().json(userJson(u -> {
                    u.setError("Not logged in.");
                })));
    }

    @Test
    void testAccessTokens() throws Exception {
        mockAccessTokens();
        mockMvc.perform(
                get("/user/tokens")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json(accessTokensJson(a -> {
                    var t1 = new AccessTokenJson();
                    t1.setDescription("This is token 1");
                    t1.setCreatedTimestamp("2000-01-01T10:00Z");
                    a.add(t1);
                    var t3 = new AccessTokenJson();
                    t3.setDescription("This is token 3");
                    t3.setCreatedTimestamp("2000-01-01T10:00Z");
                    a.add(t3);
                })));
    }

    @Test
    void testAccessTokensNotLoggedIn() throws Exception {
        mockAccessTokens();
        mockMvc.perform(get("/user/tokens"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateAccessToken() throws Exception {
        mockUserData();
        Mockito.doReturn("foobar").when(accessTokenService).generateTokenValue(any());
        mockMvc.perform(
                post("/user/token/create?description={description}", "This is my token")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(content().json(accessTokenJson(t -> {
                    t.setValue("foobar");
                    t.setDescription("This is my token");
                })));
    }

    @Test
    void testCreateAccessTokenNotLoggedIn() throws Exception {
        mockMvc.perform(
                post("/user/token/create?description={description}", "This is my token")
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteAccessToken() throws Exception {
        var userData = mockUserData();
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUser(userData);
        token.setActive(true);
        token.setType(PersonalAccessTokenType.LLT);
        Mockito.when(repositories.findPersonalAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(
                post("/user/token/delete/{id}", 100)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deactivated access token for user test_user.")));
    }

    @Test
    void testDeleteAccessTokenNotLoggedIn() throws Exception {
        mockMvc.perform(
                post("/user/token/delete/{id}", 100)
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteAccessTokenInactive() throws Exception {
        var userData = mockUserData();
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUser(userData);
        token.setActive(false);
        token.setType(PersonalAccessTokenType.LLT);
        Mockito.when(repositories.findPersonalAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(
                post("/user/token/delete/{id}", 100)
                        .with(user("test_user"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Token does not exist.")));
    }

    @Test
    void testDeleteAccessTokenWrongUser() throws Exception {
        mockUserData();
        var userData = new UserData();
        userData.setId(2);
        userData.setLoginName("wrong_user");
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUser(userData);
        token.setActive(true);
        token.setType(PersonalAccessTokenType.LLT);
        Mockito.when(repositories.findPersonalAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(
                post("/user/token/delete/{id}", 100)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Token does not exist.")));
    }

    @Test
    void testOwnNamespaces() throws Exception {
        mockOwnMemberships();
        mockMvc.perform(
                get("/user/namespaces")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json(namespacesJson(a -> {
                    var ns1 = new NamespaceJson();
                    ns1.setName("foo");
                    a.add(ns1);
                    var ns2 = new NamespaceJson();
                    ns2.setName("bar");
                    a.add(ns2);
                })));
    }

    @Test
    void testOwnNamespacesNotLoggedIn() throws Exception {
        mockOwnMemberships();
        mockMvc.perform(get("/user/namespaces"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOwnExtension() throws Exception {
        var userData = mockUserData();
        var versions = mockExtension(userData, 2, 0, 0);
        var namespace = versions.getLast().getExtension().getNamespace();
        // The user is still a member of the namespace, so their published extension is listed.
        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(userData);
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMemberships(userData)).thenReturn(Streamable.of(membership));
        mockMvc.perform(
                get("/user/extensions")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(a -> {
                    var json = new ExtensionJson();
                    json.setName("baz");
                    json.setNamespace("foobar");
                    a.add(json);
                })));
    }

    @Test
    void testOwnExtensionExcludesNonMemberNamespace() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 0);
        // The user published the extension but is no longer a member of its namespace: it must not
        // be listed. findMemberships returns nothing, so the published extension is filtered out.
        Mockito.when(repositories.findMemberships(userData)).thenReturn(Streamable.empty());
        mockMvc.perform(
                get("/user/extensions")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void testOwnExtensionNotLoggedIn() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 1, 0, 0);
        mockMvc.perform(get("/user/extensions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOwnExtensionsVerifiedAndNamespaceOwnershipConflict() throws Exception {
        // An extension kept inactive because its namespace conflicts with one already claimed in a
        // referenced external gallery: 'verified' is false (no owner yet) and
        // 'namespaceOwnershipConflict' reports the specific reason, so the webui can point the user at
        // verifying/claiming the namespace instead of a generic "under review" message.
        var userData = mockUserData();
        var versions = mockExtension(userData, 1, 0, 0);
        var latest = versions.getLast();
        var extension = latest.getExtension();
        extension.setActive(false);
        var namespace = extension.getNamespace();

        latest.setPublishedBy(userData);

        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(userData);
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMemberships(userData)).thenReturn(Streamable.of(membership));
        Mockito.when(repositories.isVerifiedPublisher(latest)).thenReturn(false);

        var scan = new ExtensionScan();
        scan.setStatus(ScanStatus.QUARANTINED);
        Mockito.when(repositories.findLatestExtensionScan(latest)).thenReturn(scan);
        Mockito.when(repositories.findExtensionThreats(scan, NamespaceOwnershipCheckScanner.TYPE))
                .thenReturn(
                        Streamable.of(
                                ExtensionThreat.create(
                                        null,
                                        null,
                                        null,
                                        NamespaceOwnershipCheckScanner.TYPE,
                                        NamespaceOwnershipCheckScanner.TYPE + "-conflict",
                                        "Namespace 'foobar' exists in the referenced gallery, but is not verified.",
                                        "high")));

        mockMvc.perform(
                get("/user/extensions")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verified").value(false))
                .andExpect(jsonPath("$[0].namespaceOwnershipConflict").value(true));
    }

    @Test
    void testGetOwnExtensionAsNamespaceOwner() throws Exception {
        var userData = mockUserData();
        var versions = mockExtension(userData, 2, 0, 0);
        var latest = versions.getLast();
        latest.setId(42L);
        var extension = latest.getExtension();
        // The caller owns the namespace: the unscoped lookup is used and every version is deletable.
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.findLatestVersion(eq("foobar"), eq("baz"), any(), eq(false), eq(false)))
                .thenReturn(latest);
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension))
                .thenReturn(
                        List.of(
                                new VersionTargetPlatformsJson(
                                        "2.0.0",
                                        List.of(
                                                new TargetPlatformActiveJson(
                                                        TargetPlatform.NAME_UNIVERSAL,
                                                        true,
                                                        false))),
                                new VersionTargetPlatformsJson(
                                        "1.0.0",
                                        List.of(
                                                new TargetPlatformActiveJson(
                                                        TargetPlatform.NAME_UNIVERSAL,
                                                        true,
                                                        false)))));
        Mockito.when(
                storageUtil.getFileUrls(
                        Mockito.anyCollection(),
                        Mockito.anyString(),
                        Mockito.any(String[].class)))
                .thenReturn(java.util.Map.of(42L, new java.util.HashMap<>()));
        mockMvc.perform(
                get("/user/extension/{namespace}/{extension}", "foobar", "baz")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"baz\",\"namespace\":\"foobar\"}"))
                .andExpect(jsonPath("$.allTargetPlatformVersions[0].canDelete").value(true))
                .andExpect(jsonPath("$.allTargetPlatformVersions[1].canDelete").value(true));
    }

    @Test
    void testGetOwnExtensionAsNamespaceMember() throws Exception {
        var userData = mockUserData();
        var versions = mockExtension(userData, 2, 0, 0);
        var latest = versions.getLast();
        latest.setId(42L);
        var extension = latest.getExtension();
        // The caller is a namespace member but not an owner: the unscoped lookup lets them see every
        // version, but only the versions they published themselves are marked deletable.
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.findLatestVersion(eq("foobar"), eq("baz"), any(), eq(false), eq(false)))
                .thenReturn(latest);
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension))
                .thenReturn(
                        List.of(
                                new VersionTargetPlatformsJson(
                                        "2.0.0",
                                        List.of(
                                                new TargetPlatformActiveJson(
                                                        TargetPlatform.NAME_UNIVERSAL,
                                                        true,
                                                        false))),
                                new VersionTargetPlatformsJson(
                                        "1.0.0",
                                        List.of(
                                                new TargetPlatformActiveJson(
                                                        TargetPlatform.NAME_UNIVERSAL,
                                                        true,
                                                        false)))));
        // Only version 1.0.0 was published by this user.
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension, userData))
                .thenReturn(
                        List.of(
                                new VersionTargetPlatformsJson(
                                        "1.0.0",
                                        List.of(
                                                new TargetPlatformActiveJson(
                                                        TargetPlatform.NAME_UNIVERSAL,
                                                        true,
                                                        false)))));
        Mockito.when(
                storageUtil.getFileUrls(
                        Mockito.anyCollection(),
                        Mockito.anyString(),
                        Mockito.any(String[].class)))
                .thenReturn(java.util.Map.of(42L, new java.util.HashMap<>()));
        mockMvc.perform(
                get("/user/extension/{namespace}/{extension}", "foobar", "baz")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"baz\",\"namespace\":\"foobar\"}"))
                .andExpect(jsonPath("$.allTargetPlatformVersions[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.allTargetPlatformVersions[0].canDelete").value(false))
                .andExpect(jsonPath("$.allTargetPlatformVersions[1].version").value("1.0.0"))
                .andExpect(jsonPath("$.allTargetPlatformVersions[1].canDelete").value(true));
    }

    @Test
    void testGetOwnExtensionNamespaceOwnershipConflict() throws Exception {
        var userData = mockUserData();
        var versions = mockExtension(userData, 2, 0, 0);
        var latest = versions.getLast();
        latest.setId(42L);
        var extension = latest.getExtension();
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.findLatestVersion(eq("foobar"), eq("baz"), any(), eq(false), eq(false)))
                .thenReturn(latest);
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension)).thenReturn(List.of());
        Mockito.when(
                storageUtil.getFileUrls(
                        Mockito.anyCollection(),
                        Mockito.anyString(),
                        Mockito.any(String[].class)))
                .thenReturn(java.util.Map.of(42L, new java.util.HashMap<>()));

        var scan = new ExtensionScan();
        scan.setStatus(ScanStatus.QUARANTINED);
        Mockito.when(repositories.findLatestExtensionScan(latest)).thenReturn(scan);
        Mockito.when(repositories.findExtensionThreats(scan, NamespaceOwnershipCheckScanner.TYPE))
                .thenReturn(
                        Streamable.of(
                                ExtensionThreat.create(
                                        null,
                                        null,
                                        null,
                                        NamespaceOwnershipCheckScanner.TYPE,
                                        NamespaceOwnershipCheckScanner.TYPE + "-conflict",
                                        "Namespace 'foobar' exists in the referenced gallery, but is not verified.",
                                        "high")));

        mockMvc.perform(
                get("/user/extension/{namespace}/{extension}", "foobar", "baz")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.namespaceOwnershipConflict").value(true));
    }

    @Test
    void testGetOwnExtensionNotMember() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 0);
        // Not a namespace member: no access at all, even to versions the user may have published
        // while they were still a member => 404 without ever looking up any version.
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                get("/user/extension/{namespace}/{extension}", "foobar", "baz")
                        .with(user("test_user")))
                .andExpect(status().isNotFound());
        Mockito.verify(repositories, Mockito.never())
                .findLatestVersion(eq("foobar"), eq("baz"), any(), eq(false), eq(false));
    }

    @Test
    void testGetOwnExtensionNotLoggedIn() throws Exception {
        mockMvc.perform(get("/user/extension/{namespace}/{extension}", "foobar", "baz"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testNamespaceMembers() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_OWNER);
        mockMvc.perform(
                get("/user/namespace/{name}/members", "foobar")
                        .with(user("test_user")))
                .andExpect(status().isOk())
                .andExpect(content().json(membershipsJson(a -> {
                    var u1 = new UserJson();
                    u1.setLoginName("test_user");
                    var m1 = new NamespaceMembershipJson("foobar", NamespaceMembership.ROLE_OWNER, u1);
                    a.getNamespaceMemberships().add(m1);
                    var u2 = new UserJson();
                    u2.setLoginName("other_user");
                    var m2 = new NamespaceMembershipJson("foobar", NamespaceMembership.ROLE_CONTRIBUTOR, u2);
                    a.getNamespaceMemberships().add(m2);
                })));
    }

    @Test
    void testNamespaceMembersNotLoggedIn() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_OWNER);
        mockMvc.perform(get("/user/namespace/{name}/members", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testNamespaceMembersNotOwner() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_CONTRIBUTOR);
        mockMvc.perform(
                get("/user/namespace/{name}/members", "foobar")
                        .with(user("test_user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAddNamespaceMember() throws Exception {
        var userData1 = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.isNamespaceOwner(userData1, namespace))
                .thenReturn(true);
        var membership = new NamespaceMembership();
        membership.setUser(userData1);
        membership.setNamespace(namespace);
        membership.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData1, namespace))
                .thenReturn(membership);
        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        Mockito.when(repositories.findUserByLoginName(null, "other_user"))
                .thenReturn(userData2);
        Mockito.when(repositories.findMembership(userData2, namespace))
                .thenReturn(null);

        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "contributor")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Added other_user as contributor of foobar.")));
    }

    @Test
    void testAddNamespaceMemberNotLoggedIn() throws Exception {
        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "contributor")
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testChangeNamespaceMember() throws Exception {
        var userData1 = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.isNamespaceOwner(userData1, namespace))
                .thenReturn(true);
        var membership1 = new NamespaceMembership();
        membership1.setUser(userData1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData1, namespace))
                .thenReturn(membership1);
        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        Mockito.when(repositories.findUserByLoginName(null, "other_user"))
                .thenReturn(userData2);
        var membership2 = new NamespaceMembership();
        membership2.setUser(userData2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "contributor")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Changed role of other_user in foobar to contributor.")));
    }

    @Test
    void testRemoveNamespaceMember() throws Exception {
        var userData1 = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.isNamespaceOwner(userData1, namespace))
                .thenReturn(true);
        var membership1 = new NamespaceMembership();
        membership1.setUser(userData1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData1, namespace))
                .thenReturn(membership1);
        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        Mockito.when(repositories.findUserByLoginName(null, "other_user"))
                .thenReturn(userData2);
        var membership2 = new NamespaceMembership();
        membership2.setUser(userData2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "remove")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Removed other_user from namespace foobar.")));
    }

    @Test
    void testAddNamespaceMemberNotOwner() throws Exception {
        var userData1 = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        var membership = new NamespaceMembership();
        membership.setUser(userData1);
        membership.setNamespace(namespace);
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMembership(userData1, namespace))
                .thenReturn(membership);

        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "contributor")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("You must be an owner of this namespace.")));
    }

    @Test
    void testChangeNamespaceMemberSameRole() throws Exception {
        var userData1 = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.isNamespaceOwner(userData1, namespace))
                .thenReturn(true);
        var membership1 = new NamespaceMembership();
        membership1.setUser(userData1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userData1, namespace))
                .thenReturn(membership1);
        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        Mockito.when(repositories.findUserByLoginName(null, "other_user"))
                .thenReturn(userData2);
        var membership2 = new NamespaceMembership();
        membership2.setUser(userData2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMembership(userData2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(
                post(
                        "/user/namespace/{namespace}/role?user={user}&role={role}",
                        "foobar",
                        "other_user",
                        "contributor")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("User other_user already has the role contributor.")));
    }

    @Test
    void testDeleteExtensionNotLoggedIn() throws Exception {
        mockExtension(null, 2, 0, 0);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteExtensionNotPublisher() throws Exception {
        var userData = mockUserData();

        var otherUser = new UserData();
        otherUser.setLoginName("other_user");
        otherUser.setFullName("Other User");
        otherUser.setProviderUrl("http://example.com/test");
        Mockito.doReturn(otherUser).when(users).findLoggedInUser();

        mockExtension(userData, 2, 0, 0);
        // A namespace member, but not the publisher of the targeted version: the version lookup is
        // scoped to the caller, so it is not found and the delete fails with 404.
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("other_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteExtensionNotMember() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 0);
        // Neither owner nor member of the namespace: rejected before the extension is touched.
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteExtension() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 0);
        // A member deletes all versions they published by naming them explicitly; each is soft-deleted.
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content(
                                "[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                // The web UI reports any error in the body as a failed delete, so a delete where every
                // version succeeded must not carry one at all.
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().json(successJson("Deleted foobar.baz 1.0.0\nDeleted foobar.baz 2.0.0")));
    }

    @Test
    void testDeleteExtensionEmptyTargetsIsNoOp() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 0);
        // With the whole-extension shortcut removed, an empty target list deletes nothing.
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content("[]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                // Nothing was deleted, so the result reports neither a success nor an error.
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void testDeleteExtensionVersion() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 3, 0, 0);
        // Non-owner member may delete versions they published themselves.
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content(
                                "[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz 1.0.0\nDeleted foobar.baz 2.0.0")));
    }

    @Test
    void testDeleteLastExtensionVersion() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 1, 0, 0);
        // Non-owner member deleting the last version they published: soft-deleted per version
        // (the extension record itself survives, deactivated).
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz 1.0.0")));
    }

    @Test
    void testDeleteDependingExtension() throws Exception {
        var userData = mockUserData();
        mockExtension(userData, 2, 0, 1);
        // Deleting all active versions of a depended-on extension triggers the dependency check.
        Mockito.when(repositories.hasMembership(any(UserData.class), any(Namespace.class))).thenReturn(true);
        Mockito.when(repositories.isNamespaceOwner(any(UserData.class), any(Namespace.class))).thenReturn(false);
        mockMvc.perform(
                post("/user/extension/{namespace}/{extension}/delete", "foobar", "baz")
                        .content(
                                "[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson(
                                        "The following extensions have a dependency on foobar.baz: foobar.dependant-1.0.0")));
    }

    //---------- UTILITY ----------//

    private UserData mockUserData() {
        var userData = new UserData();
        userData.setId(1);
        userData.setLoginName("test_user");
        userData.setFullName("Test User");
        userData.setProviderUrl("http://example.com/test");
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private String userJson(Consumer<UserJson> content) throws JacksonException {
        var json = new UserJson();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private void mockAccessTokens() {
        var userData = mockUserData();
        var token1 = new PersonalAccessToken();
        token1.setUser(userData);
        token1.setValue("token1");
        token1.setDescription("This is token 1");
        token1.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token1.setActive(true);
        token1.setType(PersonalAccessTokenType.LLT);
        var token2 = new PersonalAccessToken();
        token2.setUser(userData);
        token2.setValue("token2");
        token2.setDescription("This is token 2");
        token2.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token2.setActive(false);
        token2.setType(PersonalAccessTokenType.LLT);
        var token3 = new PersonalAccessToken();
        token3.setUser(userData);
        token3.setValue("token3");
        token3.setDescription("This is token 3");
        token3.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token3.setActive(true);
        token3.setType(PersonalAccessTokenType.LLT);
        Mockito.when(repositories.findActivePersonalAccessTokensAndType(userData, PersonalAccessTokenType.LLT))
                .thenReturn(Streamable.of(token1, token3));
    }

    private String accessTokenJson(Consumer<AccessTokenJson> content) throws JacksonException {
        var json = new AccessTokenJson();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String accessTokensJson(Consumer<List<AccessTokenJson>> content) throws JacksonException {
        var json = new ArrayList<AccessTokenJson>();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private void mockOwnMemberships() {
        var userData = mockUserData();
        var namespace1 = new Namespace();
        namespace1.setName("foo");
        namespace1.setExtensions(Collections.emptyList());
        Mockito.when(repositories.findActiveExtensions(namespace1)).thenReturn(Streamable.empty());
        var membership1 = new NamespaceMembership();
        membership1.setUser(userData);
        membership1.setNamespace(namespace1);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        var namespace2 = new Namespace();
        namespace2.setName("bar");
        namespace2.setExtensions(Collections.emptyList());
        Mockito.when(repositories.findActiveExtensions(namespace2)).thenReturn(Streamable.empty());
        var membership2 = new NamespaceMembership();
        membership2.setUser(userData);
        membership2.setNamespace(namespace2);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMemberships(userData))
                .thenReturn(Streamable.of(membership1, membership2));
    }

    private String namespacesJson(Consumer<List<NamespaceJson>> content) throws JacksonException {
        var json = new ArrayList<NamespaceJson>();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String extensionJson(Consumer<List<ExtensionJson>> content) throws JacksonException {
        var json = new ArrayList<ExtensionJson>();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private void mockNamespaceMemberships(String userRole) {
        var userData = mockUserData();
        var namespace = new Namespace();
        namespace.setName("foobar");

        var membership1 = new NamespaceMembership();
        membership1.setUser(userData);
        membership1.setNamespace(namespace);
        membership1.setRole(userRole);

        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        var membership2 = new NamespaceMembership();
        membership2.setUser(userData2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);

        Mockito.when(repositories.findMembershipsForOwner(userData, "foobar"))
                .thenReturn(
                        userRole.equals(NamespaceMembership.ROLE_OWNER)
                                ? List.of(membership1, membership2)
                                : Collections.emptyList());
    }

    private String membershipsJson(Consumer<NamespaceMembershipListJson> content) throws JacksonException {
        var json = new NamespaceMembershipListJson();
        json.setNamespaceMemberships(new ArrayList<>());
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String successJson(String message) throws JacksonException {
        var json = ResultJson.success(message);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String errorJson(String message) throws JacksonException {
        var json = ResultJson.error(message);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private Namespace mockNamespace() {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.findActiveExtensions(namespace))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.isVerified(namespace))
                .thenReturn(false);
        return namespace;
    }

    private String createVersion(int major) {
        return major + ".0.0";
    }

    private List<ExtensionVersion> mockExtension(
            UserData user,
            int numberOfVersions,
            int numberOfBundles,
            int numberOfDependants
    ) {
        var namespace = mockNamespace();
        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("baz");
        extension.setActive(true);
        Mockito.when(repositories.findExtension("baz", "foobar"))
                .thenReturn(extension);
        Mockito.when(repositories.findExtensionForUpdateNoWait("baz", "foobar"))
                .thenReturn(extension);

        var versions = new ArrayList<ExtensionVersion>(numberOfVersions);
        for (var i = 0; i < numberOfVersions; i++) {
            var extVersion = new ExtensionVersion();
            extVersion.setExtension(extension);
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setVersion(createVersion(i + 1));
            extVersion.setActive(true);
            versions.add(extVersion);
            Mockito.when(repositories.findFiles(extVersion))
                    .thenReturn(Streamable.empty());
            Mockito.when(
                    repositories.findVersionPublishedByUser(
                            user,
                            extVersion.getVersion(),
                            TargetPlatform.NAME_UNIVERSAL,
                            "baz",
                            "foobar"))
                    .thenReturn(extVersion);
        }

        extension.getVersions().addAll(versions);
        Mockito.when(repositories.findVersions(extension))
                .thenReturn(Streamable.of(versions));
        Mockito.when(repositories.findLatestVersions(user)).thenReturn(List.of(versions.getLast()));
        Mockito.when(
                repositories
                        .isDeleteAllActiveVersions(eq("foobar"), eq("baz"), any(TargetPlatformVersion[].class)))
                .then(new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) {
                        return ((TargetPlatformVersion[]) invocation.getRawArguments()[2]).length == numberOfVersions;
                    }
                });

        var bundleExt = new Extension();
        bundleExt.setName("bundle");
        bundleExt.setNamespace(namespace);

        var bundles = new ArrayList<ExtensionVersion>(numberOfBundles);
        for (var i = 0; i < numberOfBundles; i++) {
            var bundle = new ExtensionVersion();
            bundle.setExtension(bundleExt);
            bundle.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            bundle.setVersion(createVersion(i + 1));
            bundles.add(bundle);
        }
        Mockito.when(repositories.findBundledExtensionsReference(extension))
                .thenReturn(Streamable.of(bundles));

        var dependantExt = new Extension();
        dependantExt.setName("dependant");
        dependantExt.setNamespace(namespace);

        var dependants = new ArrayList<ExtensionVersion>(numberOfDependants);
        for (var i = 0; i < numberOfDependants; i++) {
            var dependant = new ExtensionVersion();
            dependant.setExtension(dependantExt);
            dependant.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            dependant.setVersion(createVersion(i + 1));
            dependants.add(dependant);
        }
        Mockito.when(repositories.findDependenciesReference(extension))
                .thenReturn(Streamable.of(dependants));

        Mockito.when(repositories.findAllReviews(extension))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.findDeprecatedExtensions(extension))
                .thenReturn(Streamable.empty());
        return versions;
    }

    @TestConfiguration
    @Import({ SecurityConfig.class, WebUiProperties.class })
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate() {
            return new MockTransactionTemplate();
        }

        @Bean
        UserService userService(
                EntityManager entityManager,
                RepositoryService repositories,
                StorageUtilService storageUtil,
                CacheService cache,
                ExtensionValidator validator,
                @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository,
                OAuth2AttributesConfig attributesConfig
        ) {
            return new UserService(
                    entityManager,
                    repositories,
                    storageUtil,
                    cache,
                    validator,
                    clientRegistrationRepository,
                    attributesConfig);
        }

        @Bean
        UUIDService uuidService() {
            return new UUIDService();
        }

        @Bean
        AccessTokenConfig tokenConfig() {
            return new AccessTokenConfig(new MirrorConfig());
        }

        @Bean
        AccessTokenService accessTokenService(
                AccessTokenConfig config,
                EntityManager entityManager,
                RepositoryService repositories,
                MailService mailService,
                DSLContext dsl
        ) {
            return new AccessTokenService(config, entityManager, repositories, mailService, dsl);
        }

        @Bean
        OAuth2UserServices oauth2UserServices(
                UserService users,
                EclipseTokenService eclipseTokenService,
                EntityManager entityManager,
                EclipseService eclipse,
                OAuth2AttributesConfig attributesConfig
        ) {
            return new OAuth2UserServices(users, eclipseTokenService, entityManager, eclipse, attributesConfig);
        }

        @Bean
        EclipseTokenService eclipseTokenService(
                TransactionTemplate transactions,
                EntityManager entityManager,
                ClientRegistrationRepository clientRegistrationRepository
        ) {
            return new EclipseTokenService(transactions, entityManager, clientRegistrationRepository);
        }

        @Bean
        LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKeyGenerator() {
            return new LatestExtensionVersionCacheKeyGenerator();
        }

        @Bean
        LocalRegistryService localRegistryService(
                EntityManager entityManager,
                RepositoryService repositories,
                ExtensionService extensions,
                VersionService versions,
                UserService users,
                AccessTokenService accessTokenService,
                SearchUtilService search,
                ExtensionValidator validator,
                StorageUtilService storageUtil,
                EclipseService eclipse,
                CacheService cache,
                ExtensionVersionIntegrityService integrityService,
                SimilarityCheckService similarityCheckService
        ) {
            return new LocalRegistryService(
                    entityManager,
                    repositories,
                    extensions,
                    versions,
                    users,
                    accessTokenService,
                    search,
                    validator,
                    storageUtil,
                    eclipse,
                    cache,
                    integrityService,
                    similarityCheckService,
                    new PublishingConfig(),
                    new TrustedPublishingConfig(),
                    new MigrationsProperties(),
                    new WebUiProperties(),
                    Duration.ofSeconds(30));
        }

        @Bean
        SimilarityConfig similarityConfig() {
            return new SimilarityConfig();
        }

        @Bean
        SimilarityService similarityService(RepositoryService repositories) {
            return new SimilarityService(repositories);
        }

        @Bean
        SimilarityCheckService similarityCheckService(
                SimilarityConfig config,
                SimilarityService similarityService,
                RepositoryService repositories
        ) {
            return new SimilarityCheckService(config, similarityService, repositories);
        }

        @Bean
        ExtensionService extensionService(
                EntityManager entityManager,
                RepositoryService repositories,
                SearchUtilService search,
                CacheService cache,
                LogService logs,
                PublishExtensionVersionHandler publishHandler,
                JobRequestScheduler scheduler,
                ExtensionScanService extensionScanService,
                ExtensionScanPersistenceService scanPersistenceService
        ) {
            return new ExtensionService(
                    new PublishingConfig(),
                    entityManager,
                    repositories,
                    search,
                    cache,
                    logs,
                    publishHandler,
                    scheduler,
                    extensionScanService,
                    scanPersistenceService);
        }
    }
}
