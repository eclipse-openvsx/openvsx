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

import static org.eclipse.openvsx.entities.FileResource.CHANGELOG;
import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD;
import static org.eclipse.openvsx.entities.FileResource.ICON;
import static org.eclipse.openvsx.entities.FileResource.LICENSE;
import static org.eclipse.openvsx.entities.FileResource.MANIFEST;
import static org.eclipse.openvsx.entities.FileResource.README;
import static org.eclipse.openvsx.entities.FileResource.VSIXMANIFEST;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionReview;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ChangeNamespaceJson;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.TargetPlatformVersionJson;
import org.eclipse.openvsx.json.UserPublishInfoJson;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Component
public class AdminService {

    private final RepositoryService repositories;
    private final ExtensionService extensions;
    private final EntityManager entityManager;
    private final UserService users;
    private final AccessTokenService tokens;
    private final ExtensionValidator validator;
    private final SearchUtilService search;
    private final EclipseService eclipse;
    private final StorageUtilService storageUtil;
    private final CacheService cache;
    private final JobRequestScheduler scheduler;
    private final MailService mail;
    private final LogService logs;
    private final ExtensionScanPersistenceService scanPersistenceService;

    public AdminService(
            RepositoryService repositories,
            ExtensionService extensions,
            EntityManager entityManager,
            UserService users,
            AccessTokenService tokens,
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
        this.repositories = repositories;
        this.extensions = extensions;
        this.entityManager = entityManager;
        this.users = users;
        this.tokens = tokens;
        this.validator = validator;
        this.search = search;
        this.eclipse = eclipse;
        this.storageUtil = storageUtil;
        this.cache = cache;
        this.scheduler = scheduler;
        this.mail = mail;
        this.logs = logs;
        this.scanPersistenceService = scanPersistenceService;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        var jobRequest = new HandlerJobRequest<>(MonthlyAdminStatisticsJobRequestHandler.class);
        scheduler.scheduleRecurrently("MonthlyAdminStatistics", Cron.monthly(1, 0, 3), ZoneId.of("UTC"), jobRequest);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public void deleteExtensionAndDependencies(String namespaceName, String extensionName, UserData admin) throws ErrorResultException {
        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            var extensionId = NamingUtil.toExtensionId(namespaceName, extensionName);
            throw new ErrorResultException("Extension not found: " + extensionId, HttpStatus.NOT_FOUND);
        }

        deleteExtensionAndDependencies(extension, admin, 0);
    }

    public void deleteExtensionAndDependencies(Extension extension, UserData admin, int depth) throws ErrorResultException {
        if(depth > 5) {
            throw new ErrorResultException("Failed to delete extension and its dependencies. Exceeded maximum recursion depth.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        var bundledRefs = repositories.findBundledExtensionsReference(extension);
        for(var bundledRef : bundledRefs) {
            deleteExtensionAndDependencies(bundledRef, admin, depth);
        }

        var dependRefs = repositories.findDependenciesReference(extension);
        for(var dependRef : dependRefs) {
            deleteExtensionAndDependencies(dependRef, admin, depth);
        }

        cache.evictExtensionJsons(extension);
        for (var extVersion : repositories.findVersions(extension)) {
            removeExtensionVersion(extVersion);
        }
        for (var review : repositories.findAllReviews(extension)) {
            entityManager.remove(review);
        }

        var deprecatedExtensions = repositories.findDeprecatedExtensions(extension);
        for(var deprecatedExtension : deprecatedExtensions) {
            deprecatedExtension.setReplacement(null);
            cache.evictExtensionJsons(deprecatedExtension);
        }

        entityManager.remove(extension);
        search.removeSearchEntry(extension);
        logs.logAction(admin, ResultJson.success("Deleted " + NamingUtil.toExtensionId(extension)));
    }

    protected void deleteExtensionAndDependencies(ExtensionVersion extVersion, UserData admin, int depth) {
        var extension = extVersion.getExtension();
        if (repositories.countVersions(extension.getNamespace().getName(), extension.getName()) == 1) {
            deleteExtensionAndDependencies(extension, admin, depth + 1);
            return;
        }

        removeExtensionVersion(extVersion);
        extension.getVersions().remove(extVersion);
        extensions.updateExtension(extension);
        logs.logAction(admin, ResultJson.success("Deleted " + NamingUtil.toLogFormat(extVersion)));
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteExtension(
            UserData adminUser,
            String namespaceName,
            String extensionName,
            List<TargetPlatformVersionJson> targetVersions
    ) {
        if(targetVersions == null || repositories.countVersions(namespaceName, extensionName) == targetVersions.size()) {
            return deleteExtension(namespaceName, extensionName, adminUser);
        }

        var results = new ArrayList<ResultJson>();
        for(var targetVersion : targetVersions) {
            results.add(deleteExtension(namespaceName, extensionName, targetVersion.targetPlatform(), targetVersion.version(), adminUser));
        }

        var result = new ResultJson();
        result.setError(results.stream().map(ResultJson::getError).filter(Objects::nonNull).collect(Collectors.joining("\n")));
        result.setSuccess(results.stream().map(ResultJson::getSuccess).filter(Objects::nonNull).collect(Collectors.joining("\n")));
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteExtension(String namespaceName, String extensionName, UserData admin)
            throws ErrorResultException {
        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            var extensionId = NamingUtil.toExtensionId(namespaceName, extensionName);
            throw new ErrorResultException("Extension not found: " + extensionId, HttpStatus.NOT_FOUND);
        }
        return deleteExtension(extension, admin);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteExtension(String namespaceName, String extensionName, String targetPlatform, String version, UserData admin)
            throws ErrorResultException {
        var extVersion = repositories.findVersion(version, targetPlatform, extensionName, namespaceName);
        if (extVersion == null) {
            var message = "Extension not found: " + NamingUtil.toLogFormat(namespaceName, extensionName, targetPlatform, version);

            throw new ErrorResultException(message, HttpStatus.NOT_FOUND);
        }

        return deleteExtension(extVersion, admin);
    }

    protected ResultJson deleteExtension(Extension extension, UserData admin) throws ErrorResultException {
        var bundledRefs = repositories.findBundledExtensionsReference(extension);
        if (!bundledRefs.isEmpty()) {
            throw new ErrorResultException("Extension " + NamingUtil.toExtensionId(extension)
                    + " is bundled by the following extension packs: "
                    + bundledRefs.stream()
                        .map(NamingUtil::toFileFormat)
                        .collect(Collectors.joining(", ")));
        }
        var dependRefs = repositories.findDependenciesReference(extension);
        if (!dependRefs.isEmpty()) {
            throw new ErrorResultException("The following extensions have a dependency on " + NamingUtil.toExtensionId(extension) + ": "
                    + dependRefs.stream()
                        .map(NamingUtil::toFileFormat)
                        .collect(Collectors.joining(", ")));
        }

        cache.evictExtensionJsons(extension);
        for (var extVersion : repositories.findVersions(extension)) {
            removeExtensionVersion(extVersion);
        }
        for (var review : repositories.findAllReviews(extension)) {
            entityManager.remove(review);
        }

        var deprecatedExtensions = repositories.findDeprecatedExtensions(extension);
        for(var deprecatedExtension : deprecatedExtensions) {
            deprecatedExtension.setReplacement(null);
            cache.evictExtensionJsons(deprecatedExtension);
        }

        entityManager.remove(extension);
        search.removeSearchEntry(extension);

        var result = ResultJson.success("Deleted " + NamingUtil.toExtensionId(extension));
        logs.logAction(admin, result);
        return result;
    }

    protected ResultJson deleteExtension(ExtensionVersion extVersion, UserData admin) {
        var extension = extVersion.getExtension();
        removeExtensionVersion(extVersion);
        extension.getVersions().remove(extVersion);
        extensions.updateExtension(extension);

        var result = ResultJson.success("Deleted " + NamingUtil.toLogFormat(extVersion));
        logs.logAction(admin, result);
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteNamespace(String namespaceName, UserData admin) throws ErrorResultException {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }
        return deleteNamespace(namespace, admin);
    }

    private ResultJson deleteNamespace(Namespace namespace, UserData admin) {
        var namespaceExtensions = repositories.findExtensions(namespace);
        if (!namespaceExtensions.isEmpty()) {
          throw new ErrorResultException("Cannot delete namespaces that contain extensions.", HttpStatus.BAD_REQUEST);
        }

        var memberships = repositories.findMemberships(namespace);
        for (var membership : memberships) {
            entityManager.remove(membership);
        }

        if (namespace.getLogoStorageType() != null) {
            try {
                storageUtil.removeNamespaceLogo(namespace);
            } catch (RuntimeException exc) {
                throw new ErrorResultException("Failed to delete namespace icon: " + exc.getMessage());
            }
        }

        entityManager.remove(namespace);

        // Clear cache for the namespace
        cache.evictNamespaceDetails(namespace);

        var result = ResultJson.success("Deleted namespace " + namespace.getName());
        logs.logAction(admin, result);
        return result;
    }

    private void removeExtensionVersion(ExtensionVersion extVersion) {
        // Clean up any pending scan jobs for this extension version
        // to prevent "file not found" errors after deletion
        scanPersistenceService.deleteScansForExtensionVersion(extVersion.getId());
        
        repositories.findFiles(extVersion).map(RemoveFileJobRequest::new).forEach(scheduler::enqueue);
        repositories.deleteFiles(extVersion);
        entityManager.remove(extVersion);
    }

    private String userNotFoundMessage(String user) {
        return "User not found: " + user;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteReview(String namespace, String extensionName, String loginName, String provider) {
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null || !extension.isActive()) {
            var message = "Extension not found: " + NamingUtil.toExtensionId(namespace, extensionName);
            throw new ErrorResultException(message, HttpStatus.NOT_FOUND);
        }

        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(provider + "/" + loginName), HttpStatus.NOT_FOUND);
        }

        var reviews = repositories.findActiveReviews(extension, user);
        if (reviews.isEmpty()) {
            var message = "No active review for extension " + NamingUtil.toExtensionId(extension) + " and user " + loginName + " found";
            throw new ErrorResultException(message, HttpStatus.NOT_FOUND);
        }

        for (var extReview : reviews) {
            deleteReview(extReview);
        }

        return ResultJson.success("Deleted review from " + loginName + " for " + NamingUtil.toExtensionId(extension));
    }

    private void deleteReview(ExtensionReview review) {
        entityManager.remove(review);

        var extension = review.getExtension();
        extension.setAverageRating(repositories.getAverageReviewRating(extension));
        extension.setReviewCount(repositories.countActiveReviews(extension));
        search.updateSearchEntry(extension);
        cache.evictExtensionJsons(extension);
        cache.evictLatestExtensionVersion(extension);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson editNamespaceMember(String namespaceName, String userName, String provider, String role,
            UserData admin) throws ErrorResultException {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Namespace not found: " + namespaceName);
        }
        var user = repositories.findUserByLoginName(provider, userName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(provider + "/" + userName));
        }

        var result = role.equals("remove")
                ? users.removeNamespaceMember(namespace, user)
                : users.addNamespaceMember(namespace, user, role);

        search.updateSearchEntries(repositories.findActiveExtensions(namespace).toList());
        logs.logAction(admin, result);
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson createNamespace(NamespaceJson json) {
        var namespaceIssue = validator.validateNamespace(json.getName());
        if (namespaceIssue.isPresent()) {
            throw new ErrorResultException(namespaceIssue.get().toString());
        }

        var namespaceName = repositories.findNamespaceName(json.getName());
        if (namespaceName != null) {
            throw new ErrorResultException("Namespace already exists: " + namespaceName);
        }
        var namespace = new Namespace();
        namespace.setName(json.getName());
        entityManager.persist(namespace);
        return ResultJson.success("Created namespace " + namespace.getName());
    }

    public void changeNamespace(ChangeNamespaceJson json) {
        if (StringUtils.isEmpty(json.oldNamespace())) {
            throw new ErrorResultException("Old namespace must have a value");
        }
        if (StringUtils.isEmpty(json.newNamespace())) {
            throw new ErrorResultException("New namespace must have a value");
        }

        var oldNamespace = repositories.findNamespace(json.oldNamespace());
        if (oldNamespace == null) {
            throw new ErrorResultException("Old namespace doesn't exists: " + json.oldNamespace());
        }

        var newNamespace = repositories.findNamespace(json.newNamespace());
        if (newNamespace != null && !json.mergeIfNewNamespaceAlreadyExists()) {
            throw new ErrorResultException("New namespace already exists: " + json.newNamespace());
        }
        if (newNamespace != null) {
            var newExtensions = repositories.findExtensions(newNamespace).stream()
                    .collect(Collectors.toMap(Extension::getName, e -> e));
            var oldExtensions = repositories.findExtensions(oldNamespace).stream()
                    .collect(Collectors.toMap(Extension::getName, e -> e));

            var duplicateExtensions = oldExtensions.keySet().stream()
                    .filter(newExtensions::containsKey)
                    .collect(Collectors.joining("','"));
            if(!duplicateExtensions.isEmpty()) {
                var message = "Can't merge namespaces, because new namespace '" +
                        json.newNamespace() +
                        "' and old namespace '" +
                        json.oldNamespace() +
                        "' have " +
                        (duplicateExtensions.indexOf(',') == -1 ? "a " : "") +
                        "duplicate extension" +
                        (duplicateExtensions.indexOf(',') == -1 ? "" : "s") +
                        ": '" +
                        duplicateExtensions +
                        "'.";

                throw new ErrorResultException(message);
            }
        }

        scheduler.enqueue(new ChangeNamespaceJobRequest(json));
    }
    
    public UserPublishInfoJson getUserPublishInfo(String provider, String loginName) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(loginName), HttpStatus.NOT_FOUND);
        }

        var userPublishInfo = new UserPublishInfoJson();
        userPublishInfo.setUser(user.toUserJson());
        eclipse.adminEnrichUserJson(userPublishInfo.getUser(), user);
        userPublishInfo.setActiveAccessTokenNum((int) repositories.countActiveAccessTokens(user));
        var extVersions = repositories.findLatestVersions(user);
        var types = new String[]{DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST};
        var fileUrls = storageUtil.getFileUrls(extVersions, UrlUtil.getBaseUrl(), types);
        userPublishInfo.setExtensions(extVersions.stream()
                .map(latest -> {
                    var json = latest.toExtensionJson();
                    json.setPreview(latest.isPreview());
                    json.setActive(latest.getExtension().isActive());
                    json.setFiles(fileUrls.get(latest.getId()));

                    return json;
                })
                .sorted(Comparator.<ExtensionJson, String>comparing(ExtensionJson::getNamespace)
                                .thenComparing(ExtensionJson::getName)
                                .thenComparing(ExtensionJson::getVersion)
                )
                .toList());

        return userPublishInfo;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson revokePublisherContributions(String provider, String loginName, UserData admin) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(loginName), HttpStatus.NOT_FOUND);
        }

        // Send a DELETE request to the Eclipse publisher agreement API
        if (eclipse.isActive() && user.getEclipsePersonId() != null) {
            eclipse.revokePublisherAgreement(user, admin);
        }

        var accessTokens = repositories.findAccessTokens(user);
        var affectedExtensions = new LinkedHashSet<Extension>();
        var deactivatedTokenCount = 0;
        var deactivatedExtensionCount = 0;
        for (var accessToken : accessTokens) {
            // Deactivate the user's access tokens
            if (accessToken.isActive()) {
                accessToken.setActive(false);
                deactivatedTokenCount++;
            }
        }

        var versions = repositories.findVersionsByUser(user, true);
        for (var version : versions) {
            // Deactivate all published extension versions
            version.setActive(false);
            affectedExtensions.add(version.getExtension());
            deactivatedExtensionCount++;
        }
        
        // Update affected extensions
        for (var extension : affectedExtensions) {
            extensions.updateExtension(extension);
        }

        var result = ResultJson.success("Deactivated " + deactivatedTokenCount
                + " tokens, deactivated " + deactivatedExtensionCount + " extensions of user "
                + provider + "/" + loginName + ".");
        logs.logAction(admin, result);
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson revokePublisherTokens(String provider, String loginName, UserData admin) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(loginName), HttpStatus.NOT_FOUND);
        }

        var deactivatedTokenCount = repositories.deactivateAccessTokens(user);
        var result = ResultJson.success("Deactivated " + deactivatedTokenCount + " tokens of user " + provider + "/" + loginName + ".");
        logs.logAction(admin, result);
        mail.scheduleRevokedAccessTokensMail(user);
        return result;
    }

    public UserData checkAdminUser() {
        return checkAdminUser(users.findLoggedInUser());
    }

    public UserData checkAdminUser(String tokenValue) {
        var user = Optional.of(tokenValue)
                .map(tokens::useAccessToken)
                .map(PersonalAccessToken::getUser)
                .orElse(null);

        return checkAdminUser(user);
    }

    private UserData checkAdminUser(UserData user) {
        if (user == null || !UserData.ROLE_ADMIN.equals(user.getRole())) {
            throw new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public AdminStatistics getAdminStatistics(int year, int month) throws ErrorResultException {
        validateYearAndMonth(year, month);
        var statistics = repositories.findAdminStatisticsByYearAndMonth(year, month);
        if(statistics == null) {
            throw new NotFoundException();
        }

        return statistics;
    }

    private void validateYearAndMonth(int year, int month) {
        if(year < 0) {
            throw new ErrorResultException("Year can't be negative", HttpStatus.BAD_REQUEST);
        }
        if(month < 1 || month > 12) {
            throw new ErrorResultException("Month must be a value between 1 and 12", HttpStatus.BAD_REQUEST);
        }

        var now = TimeUtil.getCurrentUTC();
        if(year > now.getYear() || (year == now.getYear() && month >= now.getMonthValue())) {
            throw new ErrorResultException("Combination of year and month lies in the future", HttpStatus.BAD_REQUEST);
        }
    }
}