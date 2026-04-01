/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.eclipse.openvsx.*;
import org.eclipse.openvsx.accesstoken.AccessTokenConfig;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.adapter.VSCodeIdService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.LatestExtensionVersionCacheKeyGenerator;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.eclipse.EclipseTokenService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishExtensionVersionHandler;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.search.SimilarityConfig;
import org.eclipse.openvsx.search.SimilarityService;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.security.OAuth2UserServices;
import org.eclipse.openvsx.security.SecurityConfig;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionService;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAPI.class)
@AutoConfigureWebClient
@MockitoBean(types = {
    ClientRegistrationRepository.class, UpstreamRegistryService.class, GoogleCloudStorageService.class,
    AzureBlobStorageService.class, AwsStorageService.class, VSCodeIdService.class, DownloadCountService.class, ExtensionDownloadMetrics.class,
    CacheService.class, PublishExtensionVersionHandler.class, SearchUtilService.class, EclipseService.class,
    SimpleMeterRegistry.class, FileCacheDurationConfig.class, MailService.class, CdnServiceConfig.class,
    ExtensionScanService.class, ExtensionScanPersistenceService.class, LogService.class, AccessTokenConfig.class
})
class AdminAPITest {
    
    @MockitoSpyBean
    UserService users;

    @MockitoBean
    JobRequestScheduler scheduler;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    ExtensionVersionIntegrityService integrityService;

    @Autowired
    MockMvc mockMvc;

    @Test
    void testGetExtensionNotLoggedIn() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetExtensionNotAdmin() throws Exception {
        mockNormalUser();
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetExtension() throws Exception {
        mockAdminUser();
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foobar");
                    e.setName("baz");
                    e.setVersion("2.0.0");
                    e.setActive(true);
                })));
    }

    @Test
    void testGetInactiveExtension() throws Exception {
        mockAdminUser();
        mockExtension(2, 0, 0).forEach(ev -> {
            ev.setActive(false);
            ev.getExtension().setActive(false);
        });

        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foobar");
                    e.setName("baz");
                    e.setVersion("2.0.0");
                    e.setActive(false);
                })));
    }

    @Test
    void testAddNamespaceMemberNotLoggedIn() throws Exception {
        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "owner")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAddNamespaceMemberNotAdmin() throws Exception {
        mockNormalUser();
        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "owner")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAddNamespaceMember() throws Exception {
        mockAdminUser();
        var namespace = mockNamespace();
        var userData2 = new UserData();
        userData2.setLoginName("other_user");
        Mockito.when(repositories.findUserByLoginName(null, "other_user"))
                .thenReturn(userData2);
        Mockito.when(repositories.findMembership(userData2, namespace))
                .thenReturn(null);

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "owner")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Added other_user as owner of foobar.")));
    }

    @Test
    void testChangeNamespaceMember() throws Exception {
        mockAdminUser();
        var namespace = mockNamespace();
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

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "contributor")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Changed role of other_user in foobar to contributor.")));
    }

    @Test
    void testChangeNamespaceMemberWithToken() throws Exception {
        var token = mockAdminToken();
        var namespace = mockNamespace();
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

        mockMvc.perform(post("/admin/api/namespace/{namespace}/change-member?user={user}&role={role}&token={token}", "foobar", "other_user", "contributor", token.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Changed role of other_user in foobar to contributor.")));
    }

    @Test
    void testChangeNamespaceMemberWithInvalidToken() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(post("/admin/api/namespace/{namespace}/change-member?user={user}&role={role}&token={token}", "foobar", "other_user", "contributor", token.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRemoveNamespaceMember() throws Exception {
        mockAdminUser();
        var namespace = mockNamespace();
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

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "remove")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Removed other_user from namespace foobar.")));
    }

    @Test
    void testChangeNamespaceMemberSameRole() throws Exception {
        mockAdminUser();
        var namespace = mockNamespace();
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

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?user={user}&role={role}", "foobar", "other_user", "contributor")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("User other_user already has the role contributor.")));
    }

    @Test
    void testDeleteExtensionNotLoggedIn() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteExtensionNotAdmin() throws Exception {
        mockNormalUser();
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteExtension() throws Exception {
        mockAdminUser();
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz")));
    }

    @Test
    void testDeleteExtensionWithToken() throws Exception {
        var token = mockAdminToken();
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/api/extension/{namespace}/{extension}/delete?token={token}", "foobar", "baz", token.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz")));
    }

    @Test
    void testDeleteExtensionWithInvalidToken() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(post("/admin/api/extension/{namespace}/{extension}/delete?token={token}", "foobar", "baz", token.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteExtensionVersion() throws Exception {
        mockAdminUser();
        mockExtension(3, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz 1.0.0\nDeleted foobar.baz 2.0.0")));
    }

    @Test
    void testDeleteExtensionVersionWithToken() throws Exception {
        var token = mockAdminToken();
        mockExtension(3, 0, 0);
        mockMvc.perform(post("/admin/api/extension/{namespace}/{extension}/delete?token={token}", "foobar", "baz", token.getValue())
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz 1.0.0\nDeleted foobar.baz 2.0.0")));
    }

    @Test
    void testDeleteExtensionVersionWithInvalidToken() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(post("/admin/api/extension/{namespace}/{extension}/delete?token={token}", "foobar", "baz", token.getValue())
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteLastExtensionVersion() throws Exception {
        mockAdminUser();
        mockExtension(1, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz")));
    }

    @Test
    void testDeleteBundledExtension() throws Exception {
        mockAdminUser();
        mockExtension(2, 1, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Extension foobar.baz is bundled by the following extension packs: foobar.bundle-1.0.0")));
    }

    @Test
    void testDeleteDependingExtension() throws Exception {
        mockAdminUser();
        mockExtension(2, 0, 1);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"},{\"targetPlatform\":\"universal\",\"version\":\"2.0.0\"}]")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The following extensions have a dependency on foobar.baz: foobar.dependant-1.0.0")));
    }

    @Test
    void testGetNamespaceNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetNamespaceNotAdmin() throws Exception {
        mockNormalUser();
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetNamespace() throws Exception {
        mockAdminUser();
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceJson(n -> {
                    n.setName("foobar");
                })));
    }

    @Test
    void testGetNamespaceMembersNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetNamespaceMembersNotAdmin() throws Exception {
        mockNormalUser();
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetNamespaceMembers() throws Exception {
        mockAdminUser();
        var namespace = mockNamespace();
        var user = new UserData();
        user.setLoginName("other_user");
        var membership1 = new NamespaceMembership();
        membership1.setNamespace(namespace);
        membership1.setUser(user);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMemberships(namespace.getName()))
                .thenReturn(List.of(membership1));
        
        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceMemberJson(nml -> {
                    var u = new UserJson();
                    u.setLoginName("other_user");
                    var m = new NamespaceMembershipJson("foobar", "owner", u);
                    nml.setNamespaceMemberships(List.of(m));
                })));
    }

    @Test
    void testGetNamespaceMembersWithToken() throws Exception {
        var token = mockAdminToken();
        var namespace = mockNamespace();
        var user = new UserData();
        user.setLoginName("other_user");
        var membership1 = new NamespaceMembership();
        membership1.setNamespace(namespace);
        membership1.setUser(user);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMemberships(namespace.getName()))
                .thenReturn(List.of(membership1));

        mockMvc.perform(get("/admin/api/namespace/{namespace}/members?token={token}", "foobar", token.getValue())
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceMemberJson(nml -> {
                    var u = new UserJson();
                    u.setLoginName("other_user");
                    var m = new NamespaceMembershipJson("foobar", "owner", u);
                    nml.setNamespaceMemberships(List.of(m));
                })));
    }

    @Test
    void testGetNamespaceMembersWithInvalidToken() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(get("/admin/api/namespace/{namespace}/members?token={token}", "foobar", token.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateNamespaceNotLoggedIn() throws Exception {
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); }))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateNamespaceNotAdmin() throws Exception {
        mockNormalUser();
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); }))
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateNamespace() throws Exception {
        mockAdminUser();
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); }))
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/admin/namespace/foobar"))
                .andExpect(content().json(successJson("Created namespace foobar")));
    }

    @Test
    void testCreateExistingNamespace() throws Exception {
        mockAdminUser();
        Mockito.when(repositories.findNamespaceName("foobar"))
                .thenReturn("foobar");
 
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); }))
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Namespace already exists: foobar")));
    }

    @Test
    void testGetUserPublishInfoNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/publisher/{provider}/{loginName}", "github", "test")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetUserPublishInfoNotAdmin() throws Exception {
        mockNormalUser();
        mockMvc.perform(get("/admin/publisher/{provider}/{loginName}", "github", "test")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetUserPublishInfo() throws Exception {
        mockAdminUser();
        var versions = mockExtension(1, 0, 0);
        var user = new UserData();
        user.setLoginName("test");
        user.setProvider("github");

        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setActive(true);
        versions.forEach(v -> v.setPublishedWith(token));

        Mockito.when(repositories.findUserByLoginName("github", "test")).thenReturn(user);
        Mockito.when(repositories.countActiveAccessTokens(user)).thenReturn(1L);
        Mockito.when(repositories.findLatestVersions(user)).thenReturn(versions);
        Mockito.when(repositories.findActiveReviews(user)).thenReturn(Streamable.empty());

        mockMvc.perform(get("/admin/publisher/{provider}/{loginName}", "github", "test")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(publishInfoJson(upi -> {
                    upi.setUser(new UserJson());
                    upi.getUser().setLoginName("test");
                    upi.setActiveAccessTokenNum(1);
                    var ext1 = new ExtensionJson();
                    ext1.setNamespace("foobar");
                    ext1.setName("baz");
                    ext1.setVersion("1.0.0");
                    upi.setExtensions(List.of(ext1));
                })));
    }

    @Test
    void testRevokePublisherAgreementNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/revoke", "github", "test")
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRevokePublisherAgreementNotAdmin() throws Exception {
        mockNormalUser();
        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/revoke", "github", "test")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRevokePublisherAgreement() throws Exception {
        mockAdminUser();
        var versions = mockExtension(1, 0, 0);
        var user = new UserData();
        user.setLoginName("test");
        user.setProvider("github");
        Mockito.when(repositories.findUserByLoginName("github", "test"))
                .thenReturn(user);
        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setActive(true);
        Mockito.when(repositories.findAccessTokens(user))
                .thenReturn(Streamable.of(token));
        versions.forEach(v -> v.setPublishedWith(token));
        Mockito.when(repositories.findVersionsByUser(user, true))
                .thenReturn(Streamable.of(versions));

        Mockito.when(repositories.findActiveReviews(user))
                .thenReturn(Streamable.empty());

        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/revoke", "github", "test")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deactivated 1 tokens, deactivated 1 extensions of user github/test.")));

        assertThat(token.isActive()).isFalse();
        assertThat(versions.getFirst().isActive()).isFalse();
    }

    @Test
    void testRevokeAccessTokensNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/tokens/revoke", "github", "test")
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRevokeAccessTokensNotAdmin() throws Exception {
        mockNormalUser();
        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/tokens/revoke", "github", "test")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRevokeAccessTokens() throws Exception {
        mockAdminUser();
        var user = new UserData();
        user.setLoginName("test");
        user.setProvider("github");
        Mockito.when(repositories.findUserByLoginName("github", "test"))
                .thenReturn(user);

        Mockito.when(repositories.deactivateAccessTokens(user)).thenReturn(2);
        mockMvc.perform(post("/admin/publisher/{provider}/{loginName}/tokens/revoke", "github", "test")
                        .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deactivated 2 tokens of user github/test.")));
    }

    @Test
    void testReportUnsupportedMediaType() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=2021&month=3", token.getValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void testReportNoAdminTokenCsv() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=2021&month=3", token.getValue())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testReportNoAdminTokenJson() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=2021&month=3", token.getValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testReportNegativeYearCsv() throws Exception {
        var token = mockAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=-1&month=3", token.getValue())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Year can't be negative"));
    }

    @Test
    void testReportNegativeYearJson() throws Exception {
        var token = mockAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=-1&month=3", token.getValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Year can't be negative")));
    }

    @Test
    void testReportFutureYearCsv() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusYears(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=3", token.getValue(), future.getYear())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Combination of year and month lies in the future"));
    }

    @Test
    void testReportFutureYearJson() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusYears(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=3", token.getValue(), future.getYear())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Combination of year and month lies in the future")));
    }

    @Test
    void testReportMonthLessThanOneCsv() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=0", token.getValue(), now.getYear())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Month must be a value between 1 and 12"));
    }

    @Test
    void testReportMonthLessThanOneJson() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=0", token.getValue(), now.getYear())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Month must be a value between 1 and 12")));
    }

    @Test
    void testReportMonthGreaterThanTwelveCsv() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=13", token.getValue(), now.getYear())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Month must be a value between 1 and 12"));
    }

    @Test
    void testReportMonthGreaterThanTwelveJson() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=13", token.getValue(), now.getYear())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Month must be a value between 1 and 12")));
    }

    @Test
    void testReportFutureMonthCsv() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusMonths(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), future.getYear(), future.getMonthValue())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Combination of year and month lies in the future"));
    }

    @Test
    void testReportFutureMonthJson() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusMonths(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), future.getYear(), future.getMonthValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Combination of year and month lies in the future")));
    }

    @Test
    void testArchivedReportCsv() throws Exception {
        var token = mockAdminToken();
        var past = LocalDateTime.now().minusMonths(1);
        var year = past.getYear();
        var month = past.getMonthValue();
        var extensions = 1234;
        var downloads = 423;
        var downloadsTotal = 67890;
        var publishers = 891;
        var averageReviewsPerExtension = 4.5;
        var namespaceOwners = 56;

        var stats = new AdminStatistics();
        stats.setYear(year);
        stats.setMonth(month);
        stats.setExtensions(extensions);
        stats.setDownloads(downloads);
        stats.setDownloadsTotal(downloadsTotal);
        stats.setPublishers(publishers);
        stats.setAverageReviewsPerExtension(averageReviewsPerExtension);
        stats.setNamespaceOwners(namespaceOwners);
        stats.setExtensionsByRating(Map.of(1, 7, 2, 16, 3, 560, 4, 427, 5, 136));
        stats.setPublishersByExtensionsPublished(Map.of(1, 670, 2, 99, 3, 70, 4, 52));
        stats.setTopMostActivePublishingUsers(Map.of("u_foo", 93, "u_bar", 543, "u_baz", 82));
        stats.setTopNamespaceExtensions(Map.of("n_foo", 9, "n_bar", 48, "n_baz", 1239));
        stats.setTopNamespaceExtensionVersions(Map.of("nv_foo", 234, "nv_bar", 67, "nv_baz", 932));
        stats.setTopMostDownloadedExtensions(Map.of("foo.bar", 3847L, "bar.foo", 1237L, "foo.baz", 4378L));

        var values = List.<Object>of(year, month, extensions, downloads, downloadsTotal, publishers,
                averageReviewsPerExtension, namespaceOwners, 7, 16, 560, 427, 136, 670, 99, 70, 52, 543, 93, 82,
                1239, 48, 9, 932, 234, 67, 4378, 3847, 1237);
        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(year, month)).thenReturn(stats);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), year, month)
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().string("year,month,extensions,downloads,downloads_total,publishers," +
                        "average_reviews_per_extension,namespace_owners,extensions_by_rating_1,extensions_by_rating_2," +
                        "extensions_by_rating_3,extensions_by_rating_4,extensions_by_rating_5," +
                        "publishers_published_extensions_1,publishers_published_extensions_2," +
                        "publishers_published_extensions_3,publishers_published_extensions_4," +
                        "most_active_publishing_users_u_bar,most_active_publishing_users_u_foo,most_active_publishing_users_u_baz," +
                        "namespace_extensions_n_baz,namespace_extensions_n_bar,namespace_extensions_n_foo," +
                        "namespace_extension_versions_nv_baz,namespace_extension_versions_nv_foo,namespace_extension_versions_nv_bar," +
                        "most_downloaded_extensions_foo.baz,most_downloaded_extensions_foo.bar,most_downloaded_extensions_bar.foo\n" +
                        values.stream().map(Object::toString).collect(Collectors.joining(","))));
    }

    @Test
    void testArchivedReportJson() throws Exception {
        var token = mockAdminToken();
        var past = LocalDateTime.now().minusMonths(1);
        var year = past.getYear();
        var month = past.getMonthValue();
        var extensions = 1234;
        var downloads = 423;
        var downloadsTotal = 67890;
        var publishers = 891;
        var averageReviewsPerExtension = 4.5;
        var namespaceOwners = 56;

        var stats = new AdminStatistics();
        stats.setYear(year);
        stats.setMonth(month);
        stats.setExtensions(extensions);
        stats.setDownloads(downloads);
        stats.setDownloadsTotal(downloadsTotal);
        stats.setPublishers(publishers);
        stats.setAverageReviewsPerExtension(averageReviewsPerExtension);
        stats.setNamespaceOwners(namespaceOwners);
        stats.setExtensionsByRating(Map.of(1, 7, 2, 16, 3, 560, 4, 427, 5, 136));
        stats.setPublishersByExtensionsPublished(Map.of(1, 670, 2, 99, 3, 70, 4, 52));
        stats.setTopMostActivePublishingUsers(Map.of("u_foo", 93, "u_bar", 543, "u_baz", 82));
        stats.setTopNamespaceExtensions(Map.of("n_foo", 9, "n_bar", 48, "n_baz", 1239));
        stats.setTopNamespaceExtensionVersions(Map.of("nv_foo", 234, "nv_bar", 67, "nv_baz", 932));
        stats.setTopMostDownloadedExtensions(Map.of("foo.bar", 3847L, "bar.foo", 1237L, "foo.baz", 4378L));

        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(year, month)).thenReturn(stats);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), year, month)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().json(adminStatisticsJson(s -> {
                    s.setYear(year);
                    s.setMonth(month);
                    s.setExtensions(extensions);
                    s.setDownloads(downloads);
                    s.setDownloadsTotal(downloadsTotal);
                    s.setPublishers(publishers);
                    s.setAverageReviewsPerExtension(averageReviewsPerExtension);
                    s.setNamespaceOwners(namespaceOwners);

                    var rating5 = new AdminStatisticsJson.ExtensionsByRating();
                    rating5.setRating(5);
                    rating5.setExtensions(136);
                    var rating4 = new AdminStatisticsJson.ExtensionsByRating();
                    rating4.setRating(4);
                    rating4.setExtensions(427);
                    var rating3 = new AdminStatisticsJson.ExtensionsByRating();
                    rating3.setRating(3);
                    rating3.setExtensions(560);
                    var rating2 = new AdminStatisticsJson.ExtensionsByRating();
                    rating2.setRating(2);
                    rating2.setExtensions(16);
                    var rating1 = new AdminStatisticsJson.ExtensionsByRating();
                    rating1.setRating(1);
                    rating1.setExtensions(7);
                    s.setExtensionsByRating(List.of(rating5, rating4, rating3, rating2, rating1));

                    var publishers4 = new AdminStatisticsJson.PublishersByExtensionsPublished();
                    publishers4.setExtensionsPublished(4);
                    publishers4.setPublishers(52);
                    var publishers3 = new AdminStatisticsJson.PublishersByExtensionsPublished();
                    publishers3.setExtensionsPublished(3);
                    publishers3.setPublishers(70);
                    var publishers2 = new AdminStatisticsJson.PublishersByExtensionsPublished();
                    publishers2.setExtensionsPublished(2);
                    publishers2.setPublishers(99);
                    var publishers1 = new AdminStatisticsJson.PublishersByExtensionsPublished();
                    publishers1.setExtensionsPublished(1);
                    publishers1.setPublishers(670);
                    s.setPublishersByExtensionsPublished(List.of(publishers4, publishers3, publishers2, publishers1));

                    var activePublisher1 = new AdminStatisticsJson.TopMostActivePublishingUsers();
                    activePublisher1.setUserLoginName("u_bar");
                    activePublisher1.setPublishedExtensionVersions(543);
                    var activePublisher2 = new AdminStatisticsJson.TopMostActivePublishingUsers();
                    activePublisher2.setUserLoginName("u_foo");
                    activePublisher2.setPublishedExtensionVersions(93);
                    var activePublisher3 = new AdminStatisticsJson.TopMostActivePublishingUsers();
                    activePublisher3.setUserLoginName("u_baz");
                    activePublisher3.setPublishedExtensionVersions(82);
                    s.setTopMostActivePublishingUsers(List.of(activePublisher1, activePublisher2, activePublisher3));

                    var namespaceExtensions1 = new AdminStatisticsJson.TopNamespaceExtensions();
                    namespaceExtensions1.setNamespace("n_baz");
                    namespaceExtensions1.setExtensions(1239);
                    var namespaceExtensions2 = new AdminStatisticsJson.TopNamespaceExtensions();
                    namespaceExtensions2.setNamespace("n_bar");
                    namespaceExtensions2.setExtensions(48);
                    var namespaceExtensions3 = new AdminStatisticsJson.TopNamespaceExtensions();
                    namespaceExtensions3.setNamespace("n_foo");
                    namespaceExtensions3.setExtensions(9);
                    s.setTopNamespaceExtensions(List.of(namespaceExtensions1, namespaceExtensions2, namespaceExtensions3));

                    var namespaceExtensionVersions1 = new AdminStatisticsJson.TopNamespaceExtensionVersions();
                    namespaceExtensionVersions1.setNamespace("nv_baz");
                    namespaceExtensionVersions1.setExtensionVersions(932);
                    var namespaceExtensionVersions2 = new AdminStatisticsJson.TopNamespaceExtensionVersions();
                    namespaceExtensionVersions2.setNamespace("nv_foo");
                    namespaceExtensionVersions2.setExtensionVersions(234);
                    var namespaceExtensionVersions3 = new AdminStatisticsJson.TopNamespaceExtensionVersions();
                    namespaceExtensionVersions3.setNamespace("nv_bar");
                    namespaceExtensionVersions3.setExtensionVersions(67);
                    s.setTopNamespaceExtensionVersions(List.of(namespaceExtensionVersions1, namespaceExtensionVersions2, namespaceExtensionVersions3));

                    var mostDownloadedExtensions1 = new AdminStatisticsJson.TopMostDownloadedExtensions();
                    mostDownloadedExtensions1.setExtensionIdentifier("foo.baz");
                    mostDownloadedExtensions1.setDownloads(4378L);
                    var mostDownloadedExtensions2 = new AdminStatisticsJson.TopMostDownloadedExtensions();
                    mostDownloadedExtensions2.setExtensionIdentifier("foo.bar");
                    mostDownloadedExtensions2.setDownloads(3847L);
                    var mostDownloadedExtensions3 = new AdminStatisticsJson.TopMostDownloadedExtensions();
                    mostDownloadedExtensions3.setExtensionIdentifier("bar.foo");
                    mostDownloadedExtensions3.setDownloads(1237L);
                    s.setTopMostDownloadedExtensions(List.of(mostDownloadedExtensions1, mostDownloadedExtensions2, mostDownloadedExtensions3));
                })));
    }

    @Test
    void testCurrentMonthAdminReportCsv() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), now.getYear(), now.getMonthValue())
                .header(HttpHeaders.ACCEPT, "text/csv"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Combination of year and month lies in the future"));
    }

    @Test
    void testCurrentMonthAdminReportJson() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), now.getYear(), now.getMonthValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Combination of year and month lies in the future")));
    }

    @Test
    void testChangeNamespace() throws Exception {
        mockAdminUser();
        var foo = new Namespace();
        foo.setName("foo");
        Mockito.when(repositories.findNamespace(foo.getName())).thenReturn(foo);

        var bar = new Namespace();
        bar.setName("bar");
        Mockito.when(repositories.findNamespace(bar.getName())).thenReturn(null);

        var content = "{" +
                "\"oldNamespace\": \"foo\", " +
                "\"newNamespace\": \"bar\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
            "}";

        var json = new ChangeNamespaceJson("foo", "bar", false, true);
        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Scheduled namespace change from 'foo' to 'bar'.\nIt can take 15 minutes to a couple hours for the change to become visible.")))
                .andExpect(result -> Mockito.verify(scheduler).enqueue(new ChangeNamespaceJobRequest(json)));
    }

    @Test
    void testChangeNamespaceOldNamespaceNull() throws Exception {
        mockAdminUser();
        var content = "{" +
                "\"oldNamespace\": null, " +
                "\"newNamespace\": \"bar\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Old namespace must have a value")));
    }

    @Test
    void testChangeNamespaceOldNamespaceEmpty() throws Exception {
        mockAdminUser();
        var content = "{" +
                "\"oldNamespace\": \"\", " +
                "\"newNamespace\": \"bar\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Old namespace must have a value")));
    }

    @Test
    void testChangeNamespaceOldNamespaceDoesNotExist() throws Exception {
        mockAdminUser();
        Mockito.when(repositories.findNamespace("foo")).thenReturn(null);

        var bar = new Namespace();
        bar.setName("bar");
        Mockito.when(repositories.findNamespace(bar.getName())).thenReturn(bar);

        var content = "{" +
                "\"oldNamespace\": \"foo\", " +
                "\"newNamespace\": \"bar\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Old namespace doesn't exists: foo")));
    }

    @Test
    void testChangeNamespaceNewNamespaceNull() throws Exception {
        mockAdminUser();
        var content = "{" +
                "\"oldNamespace\": \"foo\", " +
                "\"newNamespace\": null, " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("New namespace must have a value")));
    }

    @Test
    void testChangeNamespaceNewNamespaceEmpty() throws Exception {
        mockAdminUser();
        var content = "{" +
                "\"oldNamespace\": \"foo\", " +
                "\"newNamespace\": \"\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": true" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("New namespace must have a value")));
    }

    @Test
    void testChangeNamespaceAbortOnNewNamespaceExists() throws Exception {
        mockAdminUser();
        var foo = new Namespace();
        foo.setName("foo");
        Mockito.when(repositories.findNamespace(foo.getName())).thenReturn(foo);

        var bar = new Namespace();
        bar.setName("bar");
        Mockito.when(repositories.findNamespace(bar.getName())).thenReturn(bar);

        var content = "{" +
                "\"oldNamespace\": \"foo\", " +
                "\"newNamespace\": \"bar\", " +
                "\"removeOldNamespace\": false, " +
                "\"mergeIfNewNamespaceAlreadyExists\": false" +
                "}";

        mockMvc.perform(post("/admin/change-namespace")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader())
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("New namespace already exists: bar")));
    }

    @Test
    void testDeleteReview() throws Exception {
        mockAdminUser();
        mockReviews();

        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete", "foobar", "baz", "github", "user1")
                        .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted review from user1 for foobar.baz")));
    }

    @Test
    void testDeleteReviewNotLoggedIn() throws Exception {
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete", "foo", "bar", "github", "user1")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteReviewNormalUser() throws Exception {
        mockNormalUser();

        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete", "foo", "bar", "github", "user1")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteReviewUnknownExtension() throws Exception {
        mockAdminUser();
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete", "foo", "bar", "github", "user1")
                .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar")));
    }

    @Test
    void testDeleteReviewNonExistingReview() throws Exception {
        mockAdminUser();
        mockReviews();

        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete", "foobar", "baz", "github", "user3")
                        .with(user("admin_user").authorities(new SimpleGrantedAuthority(("ROLE_ADMIN"))))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("No active review for extension foobar.baz and user user3 found")));
    }

    //---------- UTILITY ----------//

    private PersonalAccessToken mockAdminToken() {
        var user = new UserData();
        user.setRole(UserData.ROLE_ADMIN);

        var tokenValue = "admin_token";
        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setValue(tokenValue);
        token.setUser(user);
        Mockito.when(repositories.findAccessToken(tokenValue)).thenReturn(token);

        return token;
    }

    private PersonalAccessToken mockNonAdminToken() {
        var user = new UserData();
        user.setRole(UserData.ROLE_PRIVILEGED);

        var tokenValue = "normal_token";
        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setValue(tokenValue);
        token.setUser(user);
        Mockito.when(repositories.findAccessToken(tokenValue)).thenReturn(token);

        return token;
    }

    private UserData mockNormalUser() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        userData.setFullName("Test User");
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private UserData mockAdminUser() {
        var userData = new UserData();
        userData.setLoginName("admin_user");
        userData.setFullName("Admin User");
        userData.setRole(UserData.ROLE_ADMIN);
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private Namespace mockNamespace() {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.findActiveExtensions(namespace))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(false);
        return namespace;
    }

    private String namespaceJson(Consumer<NamespaceJson> content) throws JsonProcessingException {
        var json = new NamespaceJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String namespaceMemberJson(Consumer<NamespaceMembershipListJson> content) throws JsonProcessingException {
        var json = new NamespaceMembershipListJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private List<ExtensionVersion> mockExtension(int numberOfVersions, int numberOfBundles, int numberOfDependants) {
        var namespace = mockNamespace();
        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("baz");
        extension.setActive(true);
        Mockito.when(entityManager.merge(extension)).thenReturn(extension);
        Mockito.when(repositories.findExtension("baz", "foobar"))
                .thenReturn(extension);

        var versions = new ArrayList<ExtensionVersion>(numberOfVersions);
        for (var i = 0; i < numberOfVersions; i++) {
            var extVersion = new ExtensionVersion();
            extVersion.setExtension(extension);
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setVersion(createVersion(i + 1));
            extVersion.setActive(true);
            Mockito.when(repositories.findFiles(extVersion))
                    .thenReturn(Streamable.empty());
            Mockito.when(repositories.findFilesByType(anyCollection(), any()))
                    .thenReturn(Collections.emptyList());
            Mockito.when(repositories.findVersion(extVersion.getVersion(), TargetPlatform.NAME_UNIVERSAL, "baz", "foobar"))
                    .thenReturn(extVersion);
            Mockito.when(repositories.findTargetPlatformVersions(extVersion.getVersion(), "baz", "foobar"))
                    .thenReturn(Streamable.of(versions));
            versions.add(extVersion);
        }

        extension.getVersions().addAll(versions);
        Mockito.when(repositories.countVersions(namespace.getName(), extension.getName())).thenReturn(numberOfVersions);
        Mockito.when(repositories.findLatestVersion(namespace.getName(), extension.getName(), null, false, false))
                .thenReturn(versions.get(numberOfVersions - 1));
        Mockito.when(repositories.findVersions(extension))
                .thenReturn(Streamable.of(versions));
        Mockito.when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(versions));

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

    private List<ExtensionReview> mockReviews() {
        var extVersions = mockExtension(1, 0, 0);
        var extVersion = extVersions.get(0);
        var extension = extVersion.getExtension();

        var user1 = new UserData();
        user1.setLoginName("user1");
        var review1 = new ExtensionReview();
        review1.setId(1);
        review1.setExtension(extension);
        review1.setUser(user1);
        review1.setRating(3);
        review1.setComment("Somewhat ok");
        review1.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        review1.setActive(true);

        var user2 = new UserData();
        user2.setLoginName("user2");
        var review2 = new ExtensionReview();
        review2.setId(2);
        review2.setExtension(extension);
        review2.setUser(user2);
        review2.setRating(4);
        review2.setComment("Quite good");
        review2.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        review2.setActive(true);

        var user3 = new UserData();
        user3.setLoginName("user3");

        Mockito.when(repositories.findUserByLoginName(anyString(), eq("user1")))
                .thenReturn(user1);
        Mockito.when(repositories.findUserByLoginName(anyString(), eq("user2")))
                .thenReturn(user2);
        Mockito.when(repositories.findUserByLoginName(anyString(), eq("user3")))
                .thenReturn(user3);

        Mockito.when(repositories.findActiveReviews(any(), any()))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.findActiveReviews(extension, user1))
                .thenReturn(Streamable.of(review1));
        Mockito.when(repositories.findActiveReviews(extension, user2))
                .thenReturn(Streamable.of(review2));

        Mockito.when(repositories.findActiveReviews(extension))
                .thenReturn(Streamable.of(review1, review2));

        return List.of(review1, review2);
    }

    private String createVersion(int major) {
        return major + ".0.0";
    }

    private String adminStatisticsJson(Consumer<AdminStatisticsJson> content) throws JsonProcessingException {
        var json = new AdminStatisticsJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String extensionJson(Consumer<ExtensionJson> content) throws JsonProcessingException {
        var json = new ExtensionJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String publishInfoJson(Consumer<UserPublishInfoJson> content) throws JsonProcessingException {
        var json = new UserPublishInfoJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String successJson(String message) throws JsonProcessingException {
        var json = ResultJson.success(message);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String errorJson(String message) throws JsonProcessingException {
        var json = ResultJson.error(message);
        return new ObjectMapper().writeValueAsString(json);
    }
    
    @TestConfiguration
    @Import(SecurityConfig.class)
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
            return new UserService(entityManager, repositories, storageUtil, cache, validator, clientRegistrationRepository, attributesConfig);
        }

        @Bean
        AccessTokenService tokenService(
                AccessTokenConfig config,
                EntityManager entityManager,
                RepositoryService repositories,
                MailService mailService
        ) {
            return new AccessTokenService(config, entityManager, repositories, mailService);
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
        AdminService adminService(
                RepositoryService repositories,
                ExtensionService extensions,
                EntityManager entityManager,
                UserService users,
                AccessTokenService tokenService,
                ExtensionValidator validator,
                SearchUtilService search,
                EclipseService eclipse,
                StorageUtilService storageUtil,
                CacheService cache,
                JobRequestScheduler scheduler,
                MailService mail,
                LogService logs,
                ExtensionScanPersistenceService scanPersistenceService
        ) {
            return new AdminService(
                    repositories,
                    extensions,
                    entityManager,
                    users,
                    tokenService,
                    validator,
                    search,
                    eclipse,
                    storageUtil,
                    cache,
                    scheduler,
                    mail,
                    logs,
                    scanPersistenceService
            );
        }

        @Bean
        LocalRegistryService localRegistryService(
                EntityManager entityManager,
                RepositoryService repositories,
                ExtensionService extensions,
                VersionService versionService,
                UserService users,
                AccessTokenService tokenService,
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
                    versionService,
                    users,
                    tokenService,
                    search,
                    validator,
                    storageUtil,
                    eclipse,
                    cache,
                    integrityService,
                    similarityCheckService
            );
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
                    entityManager,
                    repositories,
                    search,
                    cache,
                    logs,
                    publishHandler,
                    scheduler,
                    extensionScanService,
                    scanPersistenceService
            );
        }

        @Bean
        ExtensionValidator extensionValidator() {
            return new ExtensionValidator();
        }

        @Bean
        StorageUtilService storageUtilService(
                RepositoryService repositories,
                GoogleCloudStorageService googleStorage,
                AzureBlobStorageService azureStorage,
                LocalStorageService localStorage,
                AwsStorageService awsStorage,
                DownloadCountService downloadCountService,
                ExtensionDownloadMetrics downloadMetrics,
                SearchUtilService search,
                CacheService cache,
                EntityManager entityManager,
                FileCacheDurationConfig fileCacheDurationConfig,
                CdnServiceConfig cdnServiceConfig
        ) {
            return new StorageUtilService(
                    repositories,
                    googleStorage,
                    azureStorage,
                    localStorage,
                    awsStorage,
                    downloadCountService,
                    downloadMetrics,
                    search,
                    cache,
                    entityManager,
                    fileCacheDurationConfig,
                    cdnServiceConfig,
                    Optional.empty()
            );
        }

        @Bean
        LocalStorageService localStorage() {
            return new LocalStorageService();
        }

        @Bean
        VersionService versionService() {
            return new VersionService();
        }

        @Bean
        LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKeyGenerator() {
            return new LatestExtensionVersionCacheKeyGenerator();
        }
    }
}