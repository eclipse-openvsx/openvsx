/******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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
package org.eclipse.openvsx.mirror;

import java.util.*;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.LocalRegistryService;
import org.eclipse.openvsx.UpstreamRegistryService;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.ReviewJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.TimeUtil;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class DataMirrorService {

    protected final Logger logger = LoggerFactory.getLogger(DataMirrorService.class);

    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final UserService users;
    private final AccessTokenService tokens;
    private final ExtensionService extensions;
    private final UpstreamRegistryService upstream;
    private final LocalRegistryService local;
    private final StorageUtilService storageUtil;
    private final RestTemplate backgroundRestTemplate;
    private final AdminService admin;
    private final Set<String> excludeExtensions;
    private final Set<String> includeExtensions;
    private final Counter mirroredVersions;
    private final Counter failedVersions;

    @Value("${ovsx.data.mirror.user-name}")
    String userName;

    @Value("${ovsx.data.mirror.schedule}")
    String schedule;

    public DataMirrorService(
            RepositoryService repositories,
            EntityManager entityManager,
            UserService users,
            AccessTokenService tokens,
            ExtensionService extensions,
            UpstreamRegistryService upstream,
            LocalRegistryService local,
            StorageUtilService storageUtil,
            RestTemplate backgroundRestTemplate,
            AdminService admin,
            Set<String> excludeExtensions,
            Set<String> includeExtensions,
            MeterRegistry registry
    ) {
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.users = users;
        this.tokens = tokens;
        this.extensions = extensions;
        this.upstream = upstream;
        this.local = local;
        this.storageUtil = storageUtil;
        this.backgroundRestTemplate = backgroundRestTemplate;
        this.admin = admin;
        this.excludeExtensions = excludeExtensions;
        this.includeExtensions = includeExtensions;
        this.mirroredVersions = Counter.builder("ovsx_mirror_versions").tag("outcome", "success").register(registry);
        this.failedVersions = Counter.builder("ovsx_mirror_versions").tag("outcome", "failure").register(registry);
    }

    public Counter getMirroredVersions() {
        return mirroredVersions;
    }

    public Counter getFailedVersions() {
        return failedVersions;
    }

    public String getSchedule() {
        return schedule;
    }

    public boolean needsMatch() {
        return !excludeExtensions.isEmpty() || !includeExtensions.isEmpty();
    }

    public boolean match(String namespaceName, String extensionName) {
        var extensionId = NamingUtil.toExtensionId(namespaceName, extensionName);
        if (!excludeExtensions.isEmpty() &&
                (excludeExtensions.contains(namespaceName + ".*") ||
                        excludeExtensions.contains(extensionId))) {
            return false;
        }
        return includeExtensions.isEmpty() ||
                includeExtensions.contains(namespaceName + ".*") ||
                includeExtensions.contains(extensionId);
    }

    @Transactional
    public List<ExtensionVersion> getExtensionTargetVersions(
            String namespaceName,
            String extensionName,
            String targetPlatform
    ) {
        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            return Collections.<ExtensionVersion>emptyList();
        }
        return extension.getVersions().stream().filter(v -> targetPlatform.equals(v.getTargetPlatform()))
                .collect(Collectors.toList());
    }

    @Transactional
    public UserData createMirrorUser() {
        var user = repositories.findUserByLoginName("system", userName);
        if (user != null) {
            return user;
        }

        user = repositories.findUserByLoginName(null, userName);
        if (user != null) {
            user.setProvider("system");
            return user;
        }

        user = new UserData();
        user.setProvider("system");
        user.setLoginName(userName);
        entityManager.persist(user);
        return user;
    }

    @Transactional
    public UserData getOrAddUser(UserJson json) {
        var user = repositories.findUserByLoginName(json.getProvider(), json.getLoginName());
        if (user == null) {
            user = new UserData();
            user.setLoginName(json.getLoginName());
            user.setFullName(json.getFullName());
            user.setAvatarUrl(json.getAvatarUrl());
            user.setProviderUrl(json.getHomepage());
            user.setProvider(json.getProvider());
            entityManager.persist(user);
        }

        return user;
    }

    public String getOrAddAccessTokenValue(UserData user, String description) {
        var token = repositories.findPersonalAccessToken(user, description);
        return token == null
                ? tokens.createLongLivedAccessToken(user, description, null, null, false).getValue()
                : token.getValue();
    }

    @Transactional
    public void activateExtension(String namespaceName, String extensionName) {
        var extension = repositories.findExtension(extensionName, namespaceName);
        // A mirrored version becomes available here rather than at the upstream timestamp it carries, so
        // that the changes feed this mirror serves stays followable: an entry written at the upstream
        // instant would land in a part of the feed that the mirror's own consumers have already read.
        var now = TimeUtil.getCurrentUTC();
        extension.getVersions().stream().filter(this::canActivate).forEach(extVersion -> {
            extVersion.setActive(true);
            repositories.recordExtensionVersionChange(extVersion, ExtensionVersionState.ACTIVE, now);
        });
        extensions.updateExtension(extension);
    }

    private boolean canActivate(ExtensionVersion extVersion) {
        if (extVersion.isActive()) {
            return false;
        }
        var resource = repositories.findFileByType(extVersion, FileResource.DOWNLOAD);
        if (resource == null) {
            return false;
        }

        var url = storageUtil.getLocation(resource);
        try {
            backgroundRestTemplate.headForHeaders("{canGetVsixUri}", Map.of("canGetVsixUri", url));
        } catch (Exception e) {
            logger.error("failed to activate extension, vsix is invalid: {}", url, e);
            return false;
        }

        return true;
    }

    @Transactional
    public void updateMetadata(String namespaceName, String extensionName, ExtensionJson latest) {
        var extension = repositories.findExtension(extensionName, namespaceName);
        extension.setDownloadCount(latest.getDownloadCount());
        extension.setAverageRating(latest.getAverageRating());
        extension.setReviewCount(latest.getReviewCount());

        var remoteReviews = upstream.getReviews(namespaceName, extensionName);
        var localReviews = repositories.findAllReviews(extension)
                .map(review -> Map.entry(review.toReviewJson(), review));

        remoteReviews.getReviews().stream()
                .filter(review -> localReviews.stream().noneMatch(entry -> entry.getKey().equals(review)))
                .forEach(review -> addReview(review, extension));

        localReviews.stream()
                .filter(entry -> remoteReviews.getReviews().stream().noneMatch(review -> review.equals(entry.getKey())))
                .map(Map.Entry::getValue)
                .forEach(entityManager::remove);
    }

    private void addReview(ReviewJson json, Extension extension) {
        var review = new ExtensionReview();
        review.setExtension(extension);
        review.setActive(true);
        review.setTimestamp(TimeUtil.fromUTCString(json.getTimestamp()));
        review.setUser(getOrAddUser(json.getUser()));
        review.setTitle(json.getTitle());
        review.setComment(json.getComment());
        review.setRating(json.getRating());
        entityManager.persist(review);
    }

    public void deleteExtensionVersion(ExtensionVersion extVersion, UserData user) {
        // The mirror must not keep tombstones: versions dropped upstream are purged so the mirror can
        // re-track upstream state (and re-publish a version that later reappears upstream).
        // Purge by coordinates: the version was collected in a previous transaction and is detached here,
        // so it has to be locked and re-read inside the purge transaction. This also removes the extension
        // itself once its last version is gone.
        var extension = extVersion.getExtension();
        extensions.purgeExtensionNoWait(
                user,
                extension.getNamespace().getName(),
                extension.getName(),
                TargetPlatformVersion.of(extVersion.getTargetPlatform(), extVersion.getVersion()));
    }

    public void mirrorNamespaceMetadata(String namespaceName) {
        var remoteVerified = upstream.getNamespace(namespaceName).getVerified();
        var localVerified = local.getNamespace(namespaceName).getVerified();
        if (!localVerified && remoteVerified) {
            // verify the namespace by adding an owner to it
            var membership = repositories.findFirstMembership(namespaceName);
            users.addNamespaceMember(membership.getNamespace(), membership.getUser(), NamespaceMembership.ROLE_OWNER);
        }
        if (localVerified && !remoteVerified) {
            // unverify namespace by changing owner(s) back to contributor
            var namespace = repositories.findNamespace(namespaceName);
            repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER)
                    .forEach(
                            membership -> users.addNamespaceMember(
                                    namespace,
                                    membership.getUser(),
                                    NamespaceMembership.ROLE_CONTRIBUTOR));
        }
    }

    public void ensureNamespaceMembership(UserData user, Namespace namespace) {
        if (!repositories.hasMembership(user, namespace)) {
            users.addNamespaceMember(namespace, user, NamespaceMembership.ROLE_CONTRIBUTOR);
        }
    }

    public void ensureNamespace(String namespaceName) {
        if (!repositories.namespaceExists(namespaceName)) {
            var json = new NamespaceJson();
            json.setName(namespaceName);
            admin.createNamespace(json);
        }
    }
}
