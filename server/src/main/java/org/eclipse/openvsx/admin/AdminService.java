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

import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.accesstoken.AccessTokenAction;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionReview;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ChangeNamespaceJson;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.UserPublishInfoJson;
import org.eclipse.openvsx.json.UserRelationshipsJson;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;

import static org.eclipse.openvsx.entities.FileResource.CHANGELOG;
import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD;
import static org.eclipse.openvsx.entities.FileResource.ICON;
import static org.eclipse.openvsx.entities.FileResource.LICENSE;
import static org.eclipse.openvsx.entities.FileResource.MANIFEST;
import static org.eclipse.openvsx.entities.FileResource.README;
import static org.eclipse.openvsx.entities.FileResource.VSIXMANIFEST;

@Component
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

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
    private final AdminStatisticsService statistics;

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
            AdminStatisticsService statistics
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
        this.statistics = statistics;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        var jobRequest = new HandlerJobRequest<>(MonthlyAdminStatisticsJobRequestHandler.class);
        scheduler.scheduleRecurrently("MonthlyAdminStatistics", Cron.monthly(1, 0, 3), ZoneId.of("UTC"), jobRequest);
    }

    /**
     * Purges the given extension together with every extension that <em>references</em> it — i.e. the
     * extension packs that bundle it and the extensions that declare a dependency on it — applied
     * recursively, so that nothing is left referencing a purged extension. Note that this walks the
     * reverse direction: it does <em>not</em> purge the extensions that the given extension itself
     * bundles or depends on.
     * <p>
     * No dependency check is performed: referencing extensions are purged rather than blocking the
     * operation.
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public void purgeExtensionAndReferencingExtensions(UserData admin, String namespaceName, String extensionName)
            throws ErrorResultException {
        var extension = extensions.lockExtension(namespaceName, extensionName);
        purgeExtensionAndReferencingExtensions(admin, extension, new LinkedHashSet<>());
    }

    private void purgeExtensionAndReferencingExtensions(
            UserData admin,
            Extension extension,
            Set<Long> purgedExtensionIds
    ) throws ErrorResultException {
        // Break reference cycles (e.g. two extensions bundling each other) and avoid purging the same
        // extension twice: skip if we already started purging it in this recursion. Tracking visited
        // extensions (rather than a fixed recursion depth) also lets arbitrarily long reference chains
        // be purged without a spurious depth-limit failure.
        if (!purgedExtensionIds.add(extension.getId())) {
            return;
        }

        // Versions that reference this extension would break once it is purged: extension packs that
        // bundle it and extensions that depend on it. A single version can do both, so de-duplicate by
        // version id to avoid double-counting (which could skew the per-extension check below).
        var referencingVersions = new LinkedHashMap<Long, ExtensionVersion>();
        repositories.findBundledExtensionsReference(extension)
                .forEach(version -> referencingVersions.putIfAbsent(version.getId(), version));
        repositories.findDependenciesReference(extension)
                .forEach(version -> referencingVersions.putIfAbsent(version.getId(), version));

        // Group the referencing versions by their extension so we can decide, per extension, whether to
        // purge it as a whole (all of its versions reference this one) or only the referencing versions.
        var referencingByExtensionId = referencingVersions.values().stream()
                .collect(Collectors.groupingBy(version -> version.getExtension().getId()));

        for (var versions : referencingByExtensionId.values()) {
            var referencingExtension = versions.getFirst().getExtension();
            var totalVersions = repositories.countVersions(
                    referencingExtension.getNamespace().getName(),
                    referencingExtension.getName());
            if (versions.size() >= totalVersions) {
                // every version of the referencing extension references this one: purge it as a whole
                // so that no empty extension record (or its reviews/search entry) is left orphaned.
                purgeExtensionAndReferencingExtensions(admin, referencingExtension, purgedExtensionIds);
            } else {
                // only some versions reference this one: purge just those, keeping the extension.
                for (var version : versions) {
                    extensions.purgeExtensionVersion(admin, version);
                }
            }
        }

        // Finally purge this extension itself. We unconditionally purge it, not checking whether other
        // extensions reference it, because those referencing extensions have just been purged above.
        extensions.purgeExtension(admin, extension, false);
    }

    /**
     * Deletes the given extension unconditionally. No further checks are made if the extension
     * is referenced by bundles or as a dependency.
     * <p>
     * This method is intended for non-user interaction as it will wait till the lock can be acquired.
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public void purgeExtension(UserData admin, String namespaceName, String extensionName)
            throws ErrorResultException {
        extensions.purgeExtension(admin, namespaceName, extensionName);
    }

    /**
     * Soft-delete the provided versions of an extension. The versions must be named explicitly; an empty
     * {@code targetVersions} deletes nothing. When the named versions cover all active versions and the
     * extension is referenced by bundles or used as a dependency, the operation will fail.
     * <p>
     * The method will try to lock the extension and fail with an {@code ErrorResultException} if it can't acquire it.
     * <p>
     * This method is intended to be used for user interaction as it can fail when the extension is concurrently updated.
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteExtensionNoWait(
            UserData user,
            String namespaceName,
            String extensionName,
            TargetPlatformVersion... targetVersions
    ) throws ErrorResultException {
        return extensions.deleteExtension(user, false, namespaceName, extensionName, targetVersions);
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
            var message = "No active review for extension " + NamingUtil.toExtensionId(extension) + " and user "
                    + loginName + " found";
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
    public ResultJson editNamespaceMember(
            String namespaceName,
            String userName,
            String provider,
            String role,
            UserData admin
    ) throws ErrorResultException {
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
            if (!duplicateExtensions.isEmpty()) {
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
        var userJson = user.toUserJson();
        userJson.setRole(user.getRoleAsString());
        userPublishInfo.setUser(userJson);
        eclipse.adminEnrichUserJson(userPublishInfo.getUser(), user);
        userPublishInfo.setActiveAccessTokenNum(
                (int) repositories.countActivePersonalAccessTokensAndType(user, PersonalAccessTokenType.LLT));
        var extVersions = repositories.findLatestVersions(user);
        var types = new String[] { DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST };
        var fileUrls = storageUtil.getFileUrls(extVersions, UrlUtil.getBaseUrl(), types);
        userPublishInfo.setExtensions(
                extVersions.stream()
                        .map(latest -> {
                            var json = latest.toExtensionJson();
                            json.setPreview(latest.isPreview());
                            json.setActive(latest.getExtension().isActive());
                            // findLatestVersions(user) includes inactive versions, which may be soft-deleted
                            // tombstones; surface that so the admin UI can distinguish removed from merely
                            // deactivated (mirrors UserAPI.getOwnExtensions and AdminAPI.getExtension).
                            json.setRemoved(latest.isExtensionRemoved());
                            json.setFiles(fileUrls.get(latest.getId()));

                            return json;
                        })
                        .sorted(
                                Comparator.<ExtensionJson, String>comparing(ExtensionJson::getNamespace)
                                        .thenComparing(ExtensionJson::getName)
                                        .thenComparing(ExtensionJson::getVersion))
                        .toList());

        return userPublishInfo;
    }

    @Transactional
    public Page<UserRelationshipsJson> searchUsers(String search, String role, Pageable pageable) {
        return repositories.searchUsers(search, role, pageable)
                .map(user -> {
                    var json = new UserRelationshipsJson();
                    var userJson = user.toUserJson();
                    userJson.setRole(user.getRoleAsString());
                    json.setUser(userJson);
                    json.setNamespaces(
                            repositories.findMemberships(user).stream()
                                    .map(membership -> membership.getNamespace().toNamespaceDetailsJson())
                                    .toList());
                    return json;
                });
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson updateUserRole(String provider, String loginName, String role, UserData admin) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(provider + "/" + loginName), HttpStatus.NOT_FOUND);
        }

        var updatedRole = "none".equalsIgnoreCase(role) ? null : parseRole(role);
        if (Objects.equals(user.getRole(), updatedRole)) {
            throw new ErrorResultException(
                    "User " + provider + "/" + loginName + " already has the role " + user.getRole() + ".");
        }

        user.setRole(updatedRole);
        var message = updatedRole == null
                ? "Removed role from user " + provider + "/" + loginName + "."
                : "Updated role for user " + provider + "/" + loginName + " to " + updatedRole + ".";
        var result = ResultJson.success(message);
        logs.logAction(admin, result);
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson revokePublisherContributions(String provider, String loginName, UserData admin) {
        return revokePublisherContributions(provider, loginName, admin, null);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson revokePublisherContributions(String provider, String loginName, UserData admin, String reason) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(loginName), HttpStatus.NOT_FOUND);
        }

        String revokeFailure = null;
        if (eclipse.isActive()) {
            // Only send a DELETE request to the publisher agreement API when we have
            // reliably determined that one exists ('signed' or 'outdated') - not when the status
            // is 'none' or could not be determined at all, since in that case there is nothing
            // confirmed to revoke and calling the Eclipse API would be a guess. Whatever the DELETE
            // call itself throws must still not abort the rest of the revoke below - tokens,
            // extensions and namespace memberships are unrelated to Eclipse and should still be
            // revoked; that failure is reported back as part of the result instead.

            var agreementStatus = eclipse.determinePublisherAgreementStatus(user);
            if ("signed".equals(agreementStatus) || "outdated".equals(agreementStatus)) {
                try {
                    eclipse.revokePublisherAgreement(user, admin);
                } catch (RuntimeException exc) {
                    logger.error("Failed to revoke publisher agreement for user {}/{}", provider, loginName, exc);
                    revokeFailure = exc.getMessage();
                }
            }
        }

        var accessTokens = repositories.findPersonalAccessTokens(user);
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
        var now = TimeUtil.getCurrentUTC();
        for (var version : versions) {
            // Deactivate all published extension versions
            version.setActive(false);
            // the versions stop being publicly visible here, which the changes feed reports at this
            // instant rather than at the one they were published at
            repositories.recordExtensionVersionChange(version, ExtensionVersionState.INACTIVE, now);
            affectedExtensions.add(version.getExtension());
            deactivatedExtensionCount++;
        }

        // Update affected extensions
        for (var extension : affectedExtensions) {
            extensions.updateExtension(extension);
        }

        // Revoke namespace memberships one by one through UserService rather than deleting them in bulk:
        // that is what deletes the trusted publishers registered under an ownership being revoked here, and
        // what evicts the namespace details cache - same as when an owner removes a member themselves. Each
        // membership is handed over as the row we already hold, so no second lookup can fail and abort the
        // whole revoke.
        var namespaceMemberships = repositories.findMemberships(user);
        var numberOfNamespaceMemberships = 0L;
        // add a null check due to tests using mocks which return null
        if (namespaceMemberships != null) {
            for (var membership : namespaceMemberships.toList()) {
                users.removeNamespaceMembership(membership);
                numberOfNamespaceMemberships++;
            }
        }

        var message = "Deactivated " + deactivatedTokenCount + " tokens, "
                + "deactivated " + deactivatedExtensionCount + " extensions, "
                + "removed " + numberOfNamespaceMemberships + " namespace memberships of user "
                + provider + "/" + loginName + ".";

        if (reason != null) {
            message += " Reason: " + reason;
        }

        ResultJson result;
        if (revokeFailure != null) {
            message += " Failed to revoke the publisher agreement: " + revokeFailure;
            result = ResultJson.warning(message);
        } else {
            result = ResultJson.success(message);
        }
        logs.logAction(admin, result);
        return result;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson revokePublisherTokens(String provider, String loginName, UserData admin) {
        var user = repositories.findUserByLoginName(provider, loginName);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(loginName), HttpStatus.NOT_FOUND);
        }

        var deactivatedTokenCount = repositories.deactivatePersonalAccessTokens(user);
        var result = ResultJson.success(
                "Deactivated " + deactivatedTokenCount + " tokens of user " + provider + "/" + loginName + ".");
        logs.logAction(admin, result);
        mail.scheduleRevokedAccessTokensMail(user);
        return result;
    }

    /**
     * Forget a user in line with a data-protection (GDPR) erasure request.
     * <p>
     * If nothing in the database still refers to the user afterward (no reviews, no removed
     * versions, no admin scan or file decisions, no audit log entries, and no retained access
     * tokens), the row itself is deleted. Otherwise it is anonymized in place, so that the
     * remaining content (extension reviews, security scan and file decisions, and audit logs)
     * keeps referring to a row that no longer holds any personal data. Extensions in namespaces
     * where the user was the sole member are unpublished but kept in the database and storage, so
     * people who have already installed them are unaffected.
     *
     * @param provider the authentication provider the user belongs to
     * @param username the provider-specific username of the user to forget
     * @param admin the administrator performing the erasure
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson forgetUser(String provider, String username, UserData admin) {
        var user = repositories.findUserByLoginName(provider, username);
        if (user == null) {
            throw new ErrorResultException(userNotFoundMessage(provider + "/" + username), HttpStatus.NOT_FOUND);
        }

        // Handle namespace memberships, removing the users active memberships where found
        var removedMembershipCount = 0;
        for (var membership : repositories.findMemberships(user)) {
            var namespace = membership.getNamespace();
            users.removeNamespaceMembership(membership);
            removedMembershipCount++;
            search.updateSearchEntries(repositories.findActiveExtensions(namespace).toList());
        }

        var removedExtensionCount = 0;
        // Delete all published extension versions for the user, both active and not
        var allVersions = Stream.concat(
                repositories.findVersionsByUser(user, true).stream(),
                repositories.findVersionsByUser(user, false).stream()).toList();
        for (var version : allVersions) {
            extensions.deleteExtensionVersion(user, version);
            removedExtensionCount++;
        }

        // Remove customer memberships. The customer and its rate-limit tokens are organisation-level
        // and shared, so they are retained.
        var removedCustomerMembershipCount = 0;
        for (var customerMembership : repositories.findCustomerMemberships(user)) {
            entityManager.remove(customerMembership);
            removedCustomerMembershipCount++;
        }

        // Personal access tokens are no longer referenced by extension versions, so they can
        // always be deleted outright.
        var deletedTokenCount = 0;
        for (var token : repositories.findPersonalAccessTokens(user)) {
            entityManager.remove(token);
            deletedTokenCount++;
        }

        // Namespace and customer memberships are already fully removed above. If nothing else in
        // the database still refers to this user either, delete the row outright instead of
        // anonymizing it.
        //
        // extension_version.published_by_id is documented as permanent - it is never cleared, not
        // even once the version itself is soft-deleted - so a user who has ever published a version
        // can never be row-deleted, only anonymized: allVersions above already holds every version
        // this user ever published (active or not), and that FK has no ON DELETE clause, so leaving
        // even one of those rows behind would make entityManager.remove(user) fail outright.
        var canDeleteUser = allVersions.isEmpty()
                && repositories.countReviews(user) == 0
                && repositories.countVersionsRemovedBy(user) == 0
                && repositories.countAdminScanDecisions(user) == 0
                && repositories.countFileDecisions(user) == 0
                && repositories.countPersistedLogs(user) == 0;

        var tombstoneLogin = "deleted-user-" + user.getId();
        if (canDeleteUser) {
            entityManager.remove(user);
        } else {
            // Anonymize the user record in place. Reviews, scan and file decisions, and audit logs
            // keep referencing this row, which no longer holds any personal data.
            user.setLoginName(tombstoneLogin);
            user.setFullName(null);
            user.setEmail(null);
            user.setAvatarUrl(null);
            user.setAuthId(null);
            user.setProvider(null);
            user.setProviderUrl(null);
            user.setEclipsePersonId(null);
            user.setEclipseToken(null);
            user.setRole(null);
        }

        // The success message deliberately contains no personal data, only the tombstone id and counts.
        var result = ResultJson.success(
                "Forgot user " + tombstoneLogin
                        + (canDeleteUser ? ": deleted user record, deleted " : ": deleted ")
                        + removedExtensionCount + " extensions, removed "
                        + removedMembershipCount + " namespace memberships, removed "
                        + removedCustomerMembershipCount + " customer memberships, deleted "
                        + deletedTokenCount + " tokens.");
        logs.logAction(admin, result);
        return result;
    }

    public UserData checkAdminUser() {
        return checkAdminUser(users.findLoggedInUser());
    }

    public UserData checkAdminUser(String tokenValue) {
        var user = Optional.of(tokenValue)
                .map(tv -> tokens.useAccessToken(tv, new AccessTokenAction.Administration()))
                .map(AccessTokenAuthentication::userData)
                .orElse(null);

        return checkAdminUser(user);
    }

    private UserData checkAdminUser(UserData user) {
        if (user == null || !UserData.Role.ADMIN.equals(user.getRole())) {
            throw new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private UserData.Role parseRole(String role) {
        try {
            return UserData.Role.valueOfIgnoreCase(role);
        } catch (IllegalArgumentException ignored) {
            throw new ErrorResultException("Invalid role: " + role, HttpStatus.BAD_REQUEST);
        }
    }

    public AdminStatistics getAdminStatistics(int year, int month) throws ErrorResultException {
        validateYearAndMonth(year, month);
        var archived = repositories.findAdminStatisticsByYearAndMonth(year, month);
        if (archived != null) {
            return archived;
        }

        // The archival job only runs on the first of the following month, so the month in progress
        // never has a stored row. Computing it here is what #235 described and what makes a
        // dashboard useful today rather than a month from now. Not saved: it is a partial month,
        // and the job will archive the complete figure in its own time.
        if (isCurrentMonth(year, month)) {
            return statistics.computeAdminStatistics(year, month);
        }

        // A past month with no row was never archived - the job did not run then, and it cannot be
        // reconstructed after the fact, because every figure but downloads is a snapshot of the
        // registry as it was.
        throw new NotFoundException();
    }

    private boolean isCurrentMonth(int year, int month) {
        var now = TimeUtil.getCurrentUTC();
        return year == now.getYear() && month == now.getMonthValue();
    }

    private void validateYearAndMonth(int year, int month) {
        if (year < 0) {
            throw new ErrorResultException("Year can't be negative", HttpStatus.BAD_REQUEST);
        }
        if (month < 1 || month > 12) {
            throw new ErrorResultException("Month must be a value between 1 and 12", HttpStatus.BAD_REQUEST);
        }

        // The month in progress is allowed: it is served on the fly (see getAdminStatistics). Only
        // a month that hasn't started yet is rejected.
        var now = TimeUtil.getCurrentUTC();
        if (year > now.getYear() || (year == now.getYear() && month > now.getMonthValue())) {
            throw new ErrorResultException("Combination of year and month lies in the future", HttpStatus.BAD_REQUEST);
        }
    }
}
