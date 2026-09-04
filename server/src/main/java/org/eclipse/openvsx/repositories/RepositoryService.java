/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.AdminScanDecision;
import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.CustomerMembership;
import org.eclipse.openvsx.entities.DailyUsageStats;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionReview;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionThreat;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.MigrationItem;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersistedLog;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.RateLimitToken;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.entities.TierType;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UsageStats;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.json.VersionTargetPlatformsJson;
import org.eclipse.openvsx.util.ChangesCursor;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.web.SitemapRow;

import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD;
import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD_SIG;

@Component
public class RepositoryService {

    private static final int MAX_VERSIONS = 100;
    private static final Sort VERSIONS_SORT = Sort
            .by(Sort.Direction.DESC, "semver.major", "semver.minor", "semver.patch")
            .and(Sort.by(Sort.Direction.ASC, "semver.isPreRelease"))
            .and(Sort.by(Sort.Direction.DESC, "universalTargetPlatform"))
            .and(Sort.by(Sort.Direction.ASC, "targetPlatform"))
            .and(Sort.by(Sort.Direction.DESC, "timestamp"));

    private final NamespaceRepository namespaceRepo;
    private final NamespaceJooqRepository namespaceJooqRepo;
    private final ExtensionRepository extensionRepo;
    private final ExtensionVersionRepository extensionVersionRepo;
    private final FileResourceRepository fileResourceRepo;
    private final ExtensionReviewRepository extensionReviewRepo;
    private final UserDataRepository userDataRepo;
    private final NamespaceMembershipRepository membershipRepo;
    private final PersonalAccessTokenRepository personalAccessTokenRepo;
    private final PersistedLogRepository persistedLogRepo;
    private final DownloadCountProcessedItemRepository downloadCountRepo;
    private final ExtensionJooqRepository extensionJooqRepo;
    private final ExtensionVersionJooqRepository extensionVersionJooqRepo;
    private final FileResourceJooqRepository fileResourceJooqRepo;
    private final ExtensionReviewJooqRepository extensionReviewJooqRepo;
    private final NamespaceMembershipJooqRepository membershipJooqRepo;
    private final AdminStatisticsRepository adminStatisticsRepo;
    private final AdminStatisticCalculationsRepository adminStatisticCalculationsRepo;
    private final MigrationItemRepository migrationItemRepo;
    private final SignatureKeyPairRepository signatureKeyPairRepo;
    private final SignatureKeyPairJooqRepository signatureKeyPairJooqRepo;
    private final ExtensionScanRepository extensionScanRepo;
    private final ExtensionVersionChangeRepository extensionVersionChangeRepo;
    private final ExtensionValidationFailureRepository extensionValidationFailureRepo;
    private final AdminScanDecisionRepository adminScanDecisionRepo;
    private final ExtensionThreatRepository extensionThreatRepo;
    private final FileDecisionRepository fileDecisionRepo;
    private final ScanCheckResultRepository scanCheckResultRepo;
    private final TierRepository tierRepo;
    private final CustomerRepository customerRepo;
    private final CustomerMembershipRepository customerMembershipRepo;
    private final UsageStatsRepository usageStatsRepository;
    private final RateLimitTokenRepository rateLimitTokenRepository;
    private final DailyUsageStatsRepository dailyUsageStatsRepository;
    private final UserDataJooqRepository userDataJooqRepo;
    private final TrustedPublisherRepository trustedPublisherRepo;

    public RepositoryService(
            NamespaceRepository namespaceRepo,
            NamespaceJooqRepository namespaceJooqRepo,
            ExtensionRepository extensionRepo,
            ExtensionVersionRepository extensionVersionRepo,
            FileResourceRepository fileResourceRepo,
            ExtensionReviewRepository extensionReviewRepo,
            UserDataRepository userDataRepo,
            NamespaceMembershipRepository membershipRepo,
            PersonalAccessTokenRepository personalAccessTokenRepo,
            PersistedLogRepository persistedLogRepo,
            DownloadCountProcessedItemRepository downloadCountRepo,
            ExtensionJooqRepository extensionJooqRepo,
            ExtensionVersionJooqRepository extensionVersionJooqRepo,
            FileResourceJooqRepository fileResourceJooqRepo,
            ExtensionReviewJooqRepository extensionReviewJooqRepo,
            NamespaceMembershipJooqRepository membershipJooqRepo,
            AdminStatisticsRepository adminStatisticsRepo,
            AdminStatisticCalculationsRepository adminStatisticCalculationsRepo,
            MigrationItemRepository migrationItemRepo,
            SignatureKeyPairRepository signatureKeyPairRepo,
            SignatureKeyPairJooqRepository signatureKeyPairJooqRepo,
            ExtensionScanRepository extensionScanRepo,
            ExtensionVersionChangeRepository extensionVersionChangeRepo,
            AdminScanDecisionRepository adminScanDecisionRepo,
            ExtensionValidationFailureRepository extensionValidationFailureRepo,
            ExtensionThreatRepository extensionThreatRepo,
            FileDecisionRepository fileDecisionRepo,
            ScanCheckResultRepository scanCheckResultRepo,
            TierRepository tierRepo,
            CustomerRepository customerRepo,
            CustomerMembershipRepository customerMembershipRepo,
            UsageStatsRepository usageStatsRepository,
            RateLimitTokenRepository rateLimitTokenRepository,
            DailyUsageStatsRepository dailyUsageStatsRepository,
            UserDataJooqRepository userDataJooqRepo,
            TrustedPublisherRepository trustedPublisherRepo
    ) {
        this.namespaceRepo = namespaceRepo;
        this.namespaceJooqRepo = namespaceJooqRepo;
        this.extensionRepo = extensionRepo;
        this.extensionVersionRepo = extensionVersionRepo;
        this.fileResourceRepo = fileResourceRepo;
        this.extensionReviewRepo = extensionReviewRepo;
        this.userDataRepo = userDataRepo;
        this.membershipRepo = membershipRepo;
        this.personalAccessTokenRepo = personalAccessTokenRepo;
        this.persistedLogRepo = persistedLogRepo;
        this.downloadCountRepo = downloadCountRepo;
        this.extensionJooqRepo = extensionJooqRepo;
        this.extensionVersionJooqRepo = extensionVersionJooqRepo;
        this.fileResourceJooqRepo = fileResourceJooqRepo;
        this.extensionReviewJooqRepo = extensionReviewJooqRepo;
        this.membershipJooqRepo = membershipJooqRepo;
        this.adminStatisticsRepo = adminStatisticsRepo;
        this.adminStatisticCalculationsRepo = adminStatisticCalculationsRepo;
        this.migrationItemRepo = migrationItemRepo;
        this.signatureKeyPairRepo = signatureKeyPairRepo;
        this.signatureKeyPairJooqRepo = signatureKeyPairJooqRepo;
        this.extensionScanRepo = extensionScanRepo;
        this.extensionVersionChangeRepo = extensionVersionChangeRepo;
        this.adminScanDecisionRepo = adminScanDecisionRepo;
        this.extensionValidationFailureRepo = extensionValidationFailureRepo;
        this.extensionThreatRepo = extensionThreatRepo;
        this.fileDecisionRepo = fileDecisionRepo;
        this.scanCheckResultRepo = scanCheckResultRepo;
        this.tierRepo = tierRepo;
        this.customerRepo = customerRepo;
        this.customerMembershipRepo = customerMembershipRepo;
        this.usageStatsRepository = usageStatsRepository;
        this.rateLimitTokenRepository = rateLimitTokenRepository;
        this.dailyUsageStatsRepository = dailyUsageStatsRepository;
        this.userDataJooqRepo = userDataJooqRepo;
        this.trustedPublisherRepo = trustedPublisherRepo;
    }

    public Streamable<TrustedPublisher> findTrustedPublishersByExtension(Extension extension) {
        return trustedPublisherRepo.findTrustedPublishersByExtension(extension);
    }

    public Streamable<TrustedPublisher> findTrustedPublishersByNamespaceAndCreatedBy(
            Namespace namespace,
            UserData createdBy
    ) {
        return trustedPublisherRepo.findByExtension_NamespaceAndCreatedBy(namespace, createdBy);
    }

    public TrustedPublisher findTrustedPublisher(long id) {
        return trustedPublisherRepo.findById(id);
    }

    public void deleteTrustedPublisher(TrustedPublisher trustedPublisher) {
        trustedPublisherRepo.delete(trustedPublisher);
    }

    public Namespace findNamespace(String name) {
        return namespaceRepo.findByNameIgnoreCase(name);
    }

    public List<Namespace> findConflictingNamespaces(String displayName, Namespace excludedNamespace) {
        return namespaceRepo.findConflictingNamespaces(displayName, excludedNamespace);
    }

    public String findNamespaceName(String name) {
        return namespaceJooqRepo.findNameByNameIgnoreCase(name);
    }

    /**
     * Locks the namespace row so that concurrent publications creating the same extension serialize
     * against each other, see {@link NamespaceRepository#findByIdForUpdate(long)}.
     */
    public void lockNamespace(Namespace namespace) {
        namespaceRepo.findByIdForUpdate(namespace.getId());
    }

    public Streamable<Namespace> findOrphanNamespaces() {
        return namespaceRepo.findOrphans();
    }

    public long countNamespaces() {
        return namespaceRepo.count();
    }

    public Extension findExtension(String name, Namespace namespace) {
        return extensionRepo.findByNameIgnoreCaseAndNamespace(name, namespace);
    }

    public Extension findExtension(String name, String namespace) {
        return extensionRepo.findByNameIgnoreCaseAndNamespaceNameIgnoreCase(name, namespace);
    }

    public Extension findExtensionForUpdate(String name, String namespace) {
        return extensionRepo.findByNameIgnoreCaseAndNamespaceNameIgnoreCaseForUpdate(name, namespace);
    }

    // Like findExtensionForUpdate but fails fast (NOWAIT) if the row is already locked by a publish.
    public Extension findExtensionForUpdateNoWait(String name, String namespace) {
        return extensionRepo.findByNameIgnoreCaseAndNamespaceNameIgnoreCaseForUpdateNoWait(name, namespace);
    }

    public Streamable<Extension> findActiveExtensions(Namespace namespace) {
        return extensionRepo.findByNamespaceAndActiveTrueOrderByNameAsc(namespace);
    }

    public Streamable<Extension> findExtensions(Collection<Long> extensionIds) {
        return extensionRepo.findByIdIn(extensionIds);
    }

    public Streamable<Extension> findExtensions(Namespace namespace) {
        return extensionRepo.findByNamespace(namespace);
    }

    public Streamable<Extension> findAllActiveExtensions() {
        return extensionRepo.findByActiveTrue();
    }

    public Streamable<Extension> findAllNotMatchingByExtensionId(List<String> extensionIds) {
        return extensionRepo.findAllNotMatchingByExtensionId(extensionIds);
    }

    public Streamable<Extension> findExtensionsWithInconsistentActiveFlag() {
        return extensionRepo.findExtensionsWithInconsistentActiveFlag();
    }

    public long countExtensions() {
        return extensionRepo.count();
    }

    public int getMaxExtensionDownloadCount() {
        return extensionRepo.getMaxDownloadCount();
    }

    public ExtensionVersion findVersion(String version, String targetPlatform, Extension extension) {
        return extensionVersionRepo.findByVersionAndTargetPlatformAndExtension(version, targetPlatform, extension);
    }

    public ExtensionVersion findVersion(String version, String targetPlatform, String extensionName, String namespace) {
        return extensionVersionRepo
                .findByVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
                        version,
                        targetPlatform,
                        extensionName,
                        namespace);
    }

    public ExtensionVersion findVersionPublishedByUser(
            UserData user,
            String version,
            String targetPlatform,
            String extensionName,
            String namespace
    ) {
        return extensionVersionRepo
                .findByPublishedByAndVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
                        user,
                        version,
                        targetPlatform,
                        extensionName,
                        namespace);
    }

    public Streamable<ExtensionVersion> findVersions(Extension extension) {
        return extensionVersionRepo.findByExtension(extension);
    }

    public Streamable<ExtensionVersion> findActiveVersions(Extension extension) {
        return extensionVersionRepo.findByExtensionAndActiveTrue(extension);
    }

    public Page<ExtensionVersion> findActiveVersionsSorted(String namespace, String extension, PageRequest page) {
        return extensionVersionRepo.findByActiveTrueAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
                extension,
                namespace,
                page.withSort(VERSIONS_SORT));
    }

    public Page<ExtensionVersion> findActiveVersionsSorted(
            String namespace,
            String extension,
            String targetPlatform,
            PageRequest page
    ) {
        return extensionVersionRepo
                .findByActiveTrueAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
                        targetPlatform,
                        extension,
                        namespace,
                        page.withSort(VERSIONS_SORT));
    }

    public Page<String> findActiveVersionStringsSorted(
            String namespace,
            String extension,
            String targetPlatform,
            PageRequest page
    ) {
        return extensionVersionJooqRepo.findActiveVersionStringsSorted(namespace, extension, targetPlatform, page);
    }

    public List<String> findVersionStringsSorted(Extension extension, String targetPlatform, boolean onlyActive) {
        return extensionVersionJooqRepo
                .findVersionStringsSorted(extension.getId(), targetPlatform, onlyActive, MAX_VERSIONS);
    }

    public Map<Long, List<String>> findActiveVersionStringsSorted(
            Collection<Long> extensionIds,
            String targetPlatform
    ) {
        return extensionVersionJooqRepo.findActiveVersionStringsSorted(extensionIds, targetPlatform, MAX_VERSIONS);
    }

    public List<ExtensionVersion> findActiveVersionReferencesSorted(Collection<Long> extensionIds) {
        return extensionVersionJooqRepo.findActiveVersionReferencesSorted(extensionIds, MAX_VERSIONS);
    }

    public Streamable<ExtensionVersion> findBundledExtensionsReference(Extension extension) {
        return extensionVersionRepo.findByBundledExtensions(NamingUtil.toExtensionId(extension));
    }

    public Streamable<ExtensionVersion> findDependenciesReference(Extension extension) {
        return extensionVersionRepo.findByDependencies(NamingUtil.toExtensionId(extension));
    }

    public Streamable<Extension> findExtensions(UserData user) {
        return extensionRepo.findDistinctByVersionsPublishedBy(user);
    }

    public Streamable<ExtensionVersion> findVersionsByUser(UserData user, boolean active) {
        return extensionVersionRepo.findByPublishedByAndActive(user, active);
    }

    public Streamable<UserData> findPublishersWithActiveVersions() {
        return extensionVersionRepo.findPublishersWithActiveVersions();
    }

    public long countVersionsRemovedBy(UserData user) {
        return extensionVersionRepo.countByRemovedBy(user);
    }

    public LocalDateTime getOldestExtensionTimestamp() {
        return extensionVersionRepo.getOldestTimestamp();
    }

    public Streamable<FileResource> findFiles(ExtensionVersion extVersion) {
        return fileResourceRepo.findByExtension(extVersion);
    }

    public void deleteFiles(ExtensionVersion extVersion) {
        fileResourceRepo.deleteByExtension(extVersion);
    }

    public Streamable<FileResource> findFilesByStorageType(String storageType) {
        return fileResourceRepo.findByStorageType(storageType);
    }

    public FileResource findFileByName(
            String namespace,
            String extension,
            String targetPlatform,
            String version,
            String name
    ) {
        return fileResourceJooqRepo.findByName(namespace, extension, targetPlatform, version, name);
    }

    public Streamable<FileResource> findDownloadsByStorageTypeAndName(String storageType, Collection<String> names) {
        return fileResourceRepo.findByTypeAndStorageTypeAndNameIgnoreCaseIn(DOWNLOAD, storageType, names);
    }

    public Streamable<FileResource> findFilesByType(String type) {
        return fileResourceRepo.findByType(type);
    }

    public FileResource findFileByType(ExtensionVersion extVersion, String type) {
        return fileResourceRepo.findByExtensionAndType(extVersion, type);
    }

    public FileResource findFileByType(
            String namespace,
            String extension,
            String targetPlatform,
            String version,
            String type
    ) {
        return fileResourceJooqRepo.findByType(namespace, extension, targetPlatform, version, type);
    }

    public List<FileResource> findFilesByType(Collection<ExtensionVersion> extVersions, Collection<String> types) {
        return fileResourceJooqRepo.findByType(extVersions, types);
    }

    public Streamable<ExtensionReview> findActiveReviews(Extension extension) {
        return extensionReviewRepo.findByExtensionAndActiveTrue(extension);
    }

    public Streamable<ExtensionReview> findAllReviews(Extension extension) {
        return extensionReviewRepo.findByExtension(extension);
    }

    public Streamable<ExtensionReview> findActiveReviews(UserData user) {
        return extensionReviewRepo.findByUserAndActiveTrue(user);
    }

    public Streamable<ExtensionReview> findActiveReviews(Extension extension, UserData user) {
        return extensionReviewRepo.findByExtensionAndUserAndActiveTrue(extension, user);
    }

    public long countActiveReviews(Extension extension) {
        return extensionReviewRepo.countByExtensionAndActiveTrue(extension);
    }

    public long countReviews(UserData user) {
        return extensionReviewRepo.countByUser(user);
    }

    public UserData findUserByLoginName(String provider, String loginName) {
        return userDataRepo.findByProviderAndLoginName(provider, loginName);
    }

    public long countUsers() {
        return userDataRepo.count();
    }

    public Page<UserData> searchUsers(String search, String role, Pageable pageable) {
        return userDataJooqRepo.findUsers(search, role, pageable);
    }

    public NamespaceMembership findMembership(UserData user, Namespace namespace) {
        return membershipRepo.findByUserAndNamespace(user, namespace);
    }

    public boolean hasMembership(UserData user, Namespace namespace) {
        return membershipJooqRepo.hasMembership(user, namespace);
    }

    /**
     * Whether {@code namespace} itself is verified, i.e. has at least one owner. Unlike
     * {@link #isVerifiedPublisher(Namespace, UserData)}, this says nothing about any particular user.
     */
    public boolean isVerified(Namespace namespace) {
        return hasMemberships(namespace, NamespaceMembership.ROLE_OWNER);
    }

    /**
     * Whether {@code user} counts as a verified publisher for {@code namespace}: a privileged user
     * bypasses per-namespace verification entirely; otherwise they must currently be a member of the
     * namespace (any role) and the namespace itself must have at least one owner. Namespace ownership
     * alone does not make every user a verified publisher for it, only members of it (or the
     * privileged).
     */
    public boolean isVerifiedPublisher(Namespace namespace, UserData user) {
        return user.isPrivileged() || membershipJooqRepo.isVerified(namespace, user);
    }

    /**
     * Whether the version's publisher counts as verified, per {@link #isVerifiedPublisher(Namespace, UserData)}.
     * {@code false} when the version records no publisher.
     */
    public boolean isVerifiedPublisher(ExtensionVersion extVersion) {
        var publishedBy = extVersion.getPublishedBy();
        if (publishedBy == null) {
            return false;
        }

        return isVerifiedPublisher(extVersion.getExtension().getNamespace(), publishedBy);
    }

    public Streamable<NamespaceMembership> findMemberships(Namespace namespace, String role) {
        return membershipRepo.findByNamespaceAndRoleIgnoreCase(namespace, role);
    }

    // Only used internally by isVerified() now - no other caller needs the general role parameter.
    private boolean hasMemberships(Namespace namespace, String role) {
        return membershipJooqRepo.hasRole(namespace, role);
    }

    public Streamable<NamespaceMembership> findMemberships(UserData user) {
        return membershipRepo.findByUserOrderByNamespaceName(user);
    }

    public Streamable<NamespaceMembership> findMemberships(Namespace namespace) {
        return membershipRepo.findByNamespace(namespace);
    }

    public List<NamespaceMembership> findMemberships(String namespaceName) {
        return membershipJooqRepo.findByNamespaceName(namespaceName);
    }

    public Streamable<PersonalAccessToken> findPersonalAccessTokens(UserData user) {
        return personalAccessTokenRepo.findByUser(user);
    }

    public Streamable<PersonalAccessToken> findAllPersonalAccessTokensByVersion(int version) {
        return personalAccessTokenRepo.findByVersion(version);
    }

    public Streamable<PersonalAccessToken> findActivePersonalAccessTokensAndType(
            UserData user,
            PersonalAccessTokenType type
    ) {
        return personalAccessTokenRepo.findByUserAndActiveTrueAndType(user, type);
    }

    public long countActivePersonalAccessTokensAndType(UserData user, PersonalAccessTokenType type) {
        return personalAccessTokenRepo.countByUserAndActiveTrueAndType(user, type);
    }

    public PersonalAccessToken findPersonalAccessToken(String value) {
        return personalAccessTokenRepo.findByValue(value);
    }

    public PersonalAccessToken findPersonalAccessToken(long id) {
        return personalAccessTokenRepo.findById(id);
    }

    public List<PersonalAccessToken> findExpiringPersonalAccessTokensWithoutNotification(
            LocalDateTime expirationTime,
            Pageable pageable
    ) {
        return personalAccessTokenRepo
                .findByExpiresTimestampLessThanEqualAndActiveTrueAndNotifiedFalseOrderById(expirationTime, pageable);
    }

    public int deactivatePersonalAccessTokens(UserData user) {
        return personalAccessTokenRepo.updateActiveSetFalse(user);
    }

    public List<PersonalAccessToken> expirePersonalAccessTokens(LocalDateTime timestamp) {
        return personalAccessTokenRepo.expireAccessTokens(timestamp);
    }

    public List<PersonalAccessToken> deleteExpiredPersonalAccessTokens(
            LocalDateTime timestamp,
            Collection<PersonalAccessTokenType> types
    ) {
        return personalAccessTokenRepo
                .deleteExpiredAccessTokens(timestamp, types.stream().map(Enum::name).toList());
    }

    public int updateExpiresTimeForLegacyPersonalAccessTokens(LocalDateTime timestamp, PersonalAccessTokenType type) {
        return personalAccessTokenRepo.updateExpiresTimeForLegacyAccessTokens(timestamp, type);
    }

    public PersonalAccessToken findPersonalAccessToken(UserData user, String description) {
        return personalAccessTokenRepo.findByUserAndDescriptionAndActiveTrue(user, description);
    }

    public Streamable<PersistedLog> findAllPersistedLogs() {
        return persistedLogRepo.findByOrderByTimestampAsc();
    }

    public Streamable<PersistedLog> findPersistedLogsAfter(LocalDateTime dateTime) {
        return persistedLogRepo.findByTimestampAfterOrderByTimestampAsc(dateTime);
    }

    public Page<PersistedLog> findPersistedLogsPaginated(Pageable pageable) {
        return persistedLogRepo.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<PersistedLog> findPersistedLogsAfterPaginated(LocalDateTime dateTime, Pageable pageable) {
        return persistedLogRepo.findByTimestampAfterOrderByTimestampDesc(dateTime, pageable);
    }

    public long countPersistedLogs(UserData user) {
        return persistedLogRepo.countByUser(user);
    }

    public List<String> findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn(
            String storageType,
            List<String> names
    ) {
        return downloadCountRepo.findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn(storageType, names);
    }

    public List<String> findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn(
            String storageType,
            List<String> names
    ) {
        return downloadCountRepo.findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn(storageType, names);
    }

    public List<Extension> findActiveExtensionsByPublicId(Collection<String> publicIds, String... namespacesToExclude) {
        return extensionJooqRepo.findAllActiveByPublicId(publicIds, namespacesToExclude);
    }

    public Extension findActiveExtension(String name, String namespaceName) {
        return extensionJooqRepo.findActiveByNameIgnoreCaseAndNamespaceNameIgnoreCase(name, namespaceName);
    }

    public List<Extension> findActiveExtensionsById(Collection<Long> ids) {
        return extensionJooqRepo.findAllActiveById(ids);
    }

    public Page<ExtensionVersion> findActiveVersions(QueryRequest request) {
        return extensionVersionJooqRepo.findActiveVersions(request);
    }

    public ChangesPage findChanges(LocalDateTime since, LocalDateTime until, ChangesCursor after, int size) {
        return extensionVersionJooqRepo.findChanges(since, until, after, size);
    }

    /**
     * Appends a publicly visible transition of the given version to the log the changes feed serves.
     * <p>
     * The instant of the transition is passed in rather than taken here, so that a single administrative
     * action affecting many versions reports all of them at one instant.
     * <p>
     * The coordinates of the version are copied onto the entry, so it has to be called while the version
     * is still there to copy them from -- before purging it, not after.
     */
    public ExtensionVersionChange recordExtensionVersionChange(
            ExtensionVersion extVersion,
            ExtensionVersionState state,
            LocalDateTime changedAt
    ) {
        return recordExtensionVersionChange(extVersion, state, changedAt, true);
    }

    /**
     * Appends the removal of a version that is being purged in this same transaction.
     * <p>
     * Unlike every other transition, the entry does not reference the version: it is about to be deleted,
     * and an entry pointing at a row that the same transaction removes is not a state the persistence
     * provider will flush, however willing the database is to null the column out afterwards. The entry
     * ends up exactly as the purge of an already-reported version would leave it -- detached, identifying
     * the version only through the coordinates copied onto it, which is what the feed reads anyway.
     */
    public ExtensionVersionChange recordPurgedExtensionVersionChange(
            ExtensionVersion extVersion,
            ExtensionVersionState state,
            LocalDateTime changedAt
    ) {
        return recordExtensionVersionChange(extVersion, state, changedAt, false);
    }

    private ExtensionVersionChange recordExtensionVersionChange(
            ExtensionVersion extVersion,
            ExtensionVersionState state,
            LocalDateTime changedAt,
            boolean referenceVersion
    ) {
        var extension = extVersion.getExtension();
        var change = new ExtensionVersionChange();
        if (referenceVersion) {
            change.setExtensionVersion(extVersion);
        }
        change.setNamespace(extension.getNamespace().getName());
        change.setExtension(extension.getName());
        change.setVersion(extVersion.getVersion());
        change.setTargetPlatform(extVersion.getTargetPlatform());
        change.setState(state);
        change.setTimestamp(extVersion.getTimestamp());
        change.setChangedAt(changedAt);
        return extensionVersionChangeRepo.save(change);
    }

    /**
     * Clears the reference to the given version from the entries the feed has already reported for it,
     * leaving the entries themselves in place. To be called just before the version is purged.
     * <p>
     * The database would do this on its own -- the foreign key is {@code ON DELETE SET NULL}, for the sake
     * of the rows nothing holds in memory -- but not soon enough: an entry loaded into the session still
     * references the version at flush time, and the persistence provider rejects that before the delete it
     * would be resolved by is ever sent. Doing it here also keeps the log's survival of a purge a property
     * of the code rather than of the schema alone.
     */
    public void detachExtensionVersionChanges(ExtensionVersion extVersion) {
        extensionVersionChangeRepo.findByExtensionVersionOrderByChangedAtAsc(extVersion)
                .forEach(change -> change.setExtensionVersion(null));
    }

    /**
     * The transition most recently reported for the given version, or empty if the feed has never
     * reported it at all.
     */
    public Optional<ExtensionVersionChange> findLatestExtensionVersionChange(ExtensionVersion extVersion) {
        return extensionVersionChangeRepo.findFirstByExtensionVersionOrderByChangedAtDescIdDesc(extVersion);
    }

    public List<ExtensionVersion> findActiveExtensionVersions(
            Collection<Long> extensionIds,
            String targetPlatform,
            int maxPreReleaseVersions
    ) {
        return extensionVersionJooqRepo
                .findAllActiveByExtensionIdAndTargetPlatform(extensionIds, targetPlatform, maxPreReleaseVersions);
    }

    public List<FileResource> findFileResourcesByExtensionVersionIdAndType(
            Collection<Long> extensionVersionIds,
            Collection<String> types
    ) {
        return fileResourceJooqRepo.findAll(extensionVersionIds, types);
    }

    public List<NamespaceMembership> findNamespaceMemberships(Collection<Long> namespaceIds) {
        return membershipJooqRepo.findAllByNamespaceId(namespaceIds);
    }

    public AdminStatistics findAdminStatisticsByYearAndMonth(int year, int month) {
        return adminStatisticsRepo.findByYearAndMonth(year, month);
    }

    public long countActiveExtensions() {
        return adminStatisticCalculationsRepo.countActiveExtensions();
    }

    public long countActiveExtensionPublishers() {
        return adminStatisticCalculationsRepo.countActiveExtensionPublishers();
    }

    public Map<Integer, Integer> countActiveExtensionPublishersGroupedByExtensionsPublished() {
        return adminStatisticCalculationsRepo.countActiveExtensionPublishersGroupedByExtensionsPublished();
    }

    public Map<Integer, Integer> countActiveExtensionsGroupedByExtensionReviewRating() {
        return adminStatisticCalculationsRepo.countActiveExtensionsGroupedByExtensionReviewRating();
    }

    public double averageNumberOfActiveReviewsPerActiveExtension() {
        return adminStatisticCalculationsRepo.averageNumberOfActiveReviewsPerActiveExtension();
    }

    public long countPublishersThatClaimedNamespaceOwnership() {
        return adminStatisticCalculationsRepo.countPublishersThatClaimedNamespaceOwnership();
    }

    public long downloadsTotal() {
        return adminStatisticCalculationsRepo.downloadsTotal();
    }

    public Map<String, Integer> topMostActivePublishingUsers(int limit) {
        return adminStatisticCalculationsRepo.topMostActivePublishingUsers(limit);
    }

    public Map<String, Integer> topNamespaceExtensions(int limit) {
        return adminStatisticCalculationsRepo.topNamespaceExtensions(limit);
    }

    public Map<String, Integer> topNamespaceExtensionVersions(int limit) {
        return adminStatisticCalculationsRepo.topNamespaceExtensionVersions(limit);
    }

    public Map<String, Long> topMostDownloadedExtensions(int limit) {
        return adminStatisticCalculationsRepo.topMostDownloadedExtensions(limit);
    }

    public int countVersions(String namespaceName, String extensionName) {
        return extensionVersionJooqRepo.countVersions(namespaceName, extensionName);
    }

    public Slice<MigrationItem> findNotMigratedItems(Pageable page) {
        return migrationItemRepo.findByMigrationScheduledFalseOrderById(page);
    }

    public long countNotMigratedItems() {
        return migrationItemRepo.countByMigrationScheduledFalse();
    }

    public double getAverageReviewRating() {
        return extensionReviewRepo.averageRatingAndActiveTrue();
    }

    public Double getAverageReviewRating(Extension extension) {
        return extensionReviewRepo.averageRatingAndActiveTrue(extension);
    }

    public Streamable<FileResource> findFileResources(Namespace namespace) {
        return fileResourceRepo.findByExtensionExtensionNamespace(namespace);
    }

    public SignatureKeyPair findActiveKeyPair() {
        return signatureKeyPairRepo.findByActiveTrue();
    }

    public Streamable<ExtensionVersion> findVersions() {
        return extensionVersionRepo.findAll();
    }

    public Streamable<ExtensionVersion> findVersionsWithout(SignatureKeyPair keyPair) {
        return extensionVersionRepo.findBySignatureKeyPairNotOrSignatureKeyPairIsNull(keyPair);
    }

    public void deleteDownloadSigFiles() {
        fileResourceRepo.deleteByType(DOWNLOAD_SIG);
    }

    public void deleteAllKeyPairs() {
        extensionVersionRepo.setKeyPairsNull();
        signatureKeyPairRepo.deleteAll();
    }

    public SignatureKeyPair findKeyPair(String publicId) {
        return signatureKeyPairRepo.findByPublicId(publicId);
    }

    public List<Extension> findAllPublicIds() {
        return extensionJooqRepo.findAllPublicIds();
    }

    public Extension findPublicId(String namespace, String extension) {
        return extensionJooqRepo.findPublicId(namespace, extension);
    }

    public Extension findPublicId(String publicId) {
        return extensionJooqRepo.findPublicId(publicId);
    }

    public Extension findNamespacePublicId(String publicId) {
        return extensionJooqRepo.findNamespacePublicId(publicId);
    }

    public void updateExtensionPublicIds(Map<Long, String> publicIds) {
        extensionJooqRepo.updatePublicIds(publicIds);
    }

    public void updateNamespacePublicIds(Map<Long, String> publicIds) {
        namespaceJooqRepo.updatePublicIds(publicIds);
    }

    public boolean extensionPublicIdExists(String publicId) {
        return extensionJooqRepo.publicIdExists(publicId);
    }

    public boolean namespacePublicIdExists(String publicId) {
        return namespaceJooqRepo.publicIdExists(publicId);
    }

    public List<SitemapRow> fetchSitemapRows() {
        return extensionJooqRepo.fetchSitemapRows();
    }

    public List<VersionTargetPlatformsJson> findTargetPlatformsGroupedByVersion(Extension extension) {
        return extensionVersionJooqRepo.findTargetPlatformsGroupedByVersion(extension);
    }

    public List<VersionTargetPlatformsJson> findTargetPlatformsGroupedByVersion(Extension extension, UserData user) {
        return extensionVersionJooqRepo.findTargetPlatformsGroupedByVersion(extension, user);
    }

    public List<ExtensionVersion> findVersionsForUrls(Extension extension, String targetPlatform, String version) {
        return extensionVersionJooqRepo.findVersionsForUrls(extension, targetPlatform, version);
    }

    public List<Extension> findExtensionsForUrls(Namespace namespace) {
        return extensionJooqRepo.findExtensionsForUrls(namespace);
    }

    public ExtensionVersion findExtensionVersion(
            String namespace,
            String extension,
            String targetPlatform,
            String version
    ) {
        return extensionVersionJooqRepo.find(namespace, extension, targetPlatform, version);
    }

    /**
     * Find an extension version regardless of active status.
     * Use this for admin operations on quarantined/inactive extensions.
     */
    public ExtensionVersion findExtensionVersionIncludingInactive(
            String namespace,
            String extension,
            String targetPlatform,
            String version
    ) {
        return extensionVersionJooqRepo.findIncludingInactive(namespace, extension, targetPlatform, version);
    }

    public ExtensionVersion findLatestVersionForAllUrls(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        return extensionVersionJooqRepo.findLatestForAllUrls(extension, targetPlatform, onlyPreRelease, onlyActive);
    }

    public ExtensionVersion findLatestVersion(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        return extensionVersionJooqRepo.findLatest(extension, targetPlatform, onlyPreRelease, onlyActive);
    }

    public List<ExtensionVersion> findLatestVersionByTargetPlatform(
            Extension extension,
            boolean preReleases,
            boolean onlyActive
    ) {
        return extensionVersionJooqRepo.findLatestVersionByTargetPlatform(extension, preReleases, onlyActive);
    }

    public ExtensionVersion findLatestVersion(
            String namespaceName,
            String extensionName,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        return extensionVersionJooqRepo
                .findLatest(namespaceName, extensionName, targetPlatform, onlyPreRelease, onlyActive);
    }

    public List<ExtensionVersion> findLatestVersions(Namespace namespace) {
        return extensionVersionJooqRepo.findLatest(namespace);
    }

    public List<ExtensionVersion> findLatestVersions(Collection<Long> extensionIds) {
        return extensionVersionJooqRepo.findLatest(extensionIds);
    }

    public List<ExtensionVersion> findLatestVersions(Collection<Long> extensionIds, String targetPlatform) {
        return extensionVersionJooqRepo.findLatest(extensionIds, targetPlatform);
    }

    public Map<Long, Boolean> findLatestVersionsIsPreview(Collection<Long> extensionIds) {
        return extensionVersionJooqRepo.findLatestIsPreview(extensionIds);
    }

    public List<ExtensionVersion> findLatestVersions(UserData user) {
        return extensionVersionJooqRepo.findLatest(user);
    }

    public ExtensionVersion findLatestVersion(UserData user, String namespace, String extension) {
        return extensionVersionJooqRepo.findLatest(user, namespace, extension);
    }

    public List<String> findExtensionTargetPlatforms(Extension extension) {
        return extensionVersionJooqRepo.findDistinctTargetPlatforms(extension);
    }

    public void deactivateKeyPairs() {
        signatureKeyPairRepo.updateActiveSetFalse();
    }

    public List<String> findActiveExtensionNames(Namespace namespace) {
        return extensionJooqRepo.findActiveExtensionNames(namespace);
    }

    public List<String> findAllExtensionNames(Namespace namespace) {
        return extensionJooqRepo.findAllExtensionNames(namespace);
    }

    public List<NamespaceMembership> findMembershipsForOwner(UserData user, String namespaceName) {
        return membershipJooqRepo.findMembershipsForOwner(user, namespaceName);
    }

    public boolean isNamespaceOwner(UserData user, Namespace namespace) {
        return membershipJooqRepo.isOwner(user, namespace);
    }

    public boolean namespaceExists(String namespaceName) {
        return namespaceJooqRepo.exists(namespaceName);
    }

    public boolean hasSameVersion(ExtensionVersion extVersion) {
        return extensionVersionJooqRepo.hasSameVersion(extVersion);
    }

    public boolean hasActiveReview(Extension extension, UserData user) {
        return extensionReviewJooqRepo.hasActiveReview(extension, user);
    }

    public boolean canPublishInNamespace(UserData user, Namespace namespace) {
        return membershipJooqRepo.canPublish(user, namespace);
    }

    public String findSignatureKeyPairPublicId(
            String namespace,
            String extension,
            String targetPlatform,
            String version
    ) {
        return signatureKeyPairJooqRepo.findPublicId(namespace, extension, targetPlatform, version);
    }

    public String findFirstUnresolvedDependency(List<ExtensionId> dependencies) {
        return extensionJooqRepo.findFirstUnresolvedDependency(dependencies);
    }

    public NamespaceMembership findFirstMembership(String namespaceName) {
        return membershipRepo.findFirstByNamespaceNameIgnoreCase(namespaceName);
    }

    public ExtensionVersion findLatestReplacement(
            long extensionId,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        return extensionVersionJooqRepo.findLatestReplacement(extensionId, targetPlatform, onlyPreRelease, onlyActive);
    }

    public boolean hasExtension(String namespace, String extension) {
        return extensionJooqRepo.hasExtension(namespace, extension);
    }

    public Streamable<Extension> findDeprecatedExtensions(Extension replacement) {
        return extensionRepo.findByReplacement(replacement);
    }

    public boolean isDeleteAllActiveVersions(
            String namespaceName,
            String extensionName,
            TargetPlatformVersion... targetVersions
    ) {
        return extensionVersionJooqRepo.isDeleteAllActiveVersions(namespaceName, extensionName, targetVersions);
    }

    public List<Extension> findSimilarExtensionsByLevenshtein(
            String extensionName,
            String namespaceName,
            String displayName,
            List<String> excludeNamespaces,
            double levenshteinThreshold,
            boolean verifiedOnly,
            int limit
    ) {
        return extensionJooqRepo.findSimilarExtensionsByLevenshtein(
                extensionName,
                namespaceName,
                displayName,
                excludeNamespaces,
                levenshteinThreshold,
                verifiedOnly,
                limit);
    }

    public List<Namespace> findSimilarNamespacesByLevenshtein(
            String namespaceName,
            List<String> excludeNamespaces,
            double levenshteinThreshold,
            boolean verifiedOnly,
            int limit
    ) {
        return namespaceJooqRepo.findSimilarNamespacesByLevenshtein(
                namespaceName,
                excludeNamespaces,
                levenshteinThreshold,
                verifiedOnly,
                limit);
    }

    public ExtensionScan saveExtensionScan(ExtensionScan scan) {
        return extensionScanRepo.save(scan);
    }

    public void deleteExtensionScan(ExtensionScan scan) {
        extensionScanRepo.deleteById(scan.getId());
    }

    public ExtensionScan findExtensionScan(long id) {
        return extensionScanRepo.findById(id);
    }

    public ExtensionScan findLatestExtensionScan(ExtensionVersion version) {
        var extension = version.getExtension();
        var namespace = extension.getNamespace();
        return extensionScanRepo
                .findFirstByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatformOrderByStartedAtDesc(
                        namespace.getName(),
                        extension.getName(),
                        version.getVersion(),
                        version.getTargetPlatform());
    }

    /**
     * Whether the version's latest scan (if any) recorded a threat of the given type, e.g. an
     * unresolved {@code NamespaceOwnershipCheckScanner.TYPE} conflict. Takes the type as a plain
     * string rather than the scanner class itself, so this repository layer doesn't have to depend
     * on the scanning package.
     */
    public boolean hasThreatOfType(ExtensionVersion version, String type) {
        var scan = findLatestExtensionScan(version);
        return scan != null && findExtensionThreats(scan, type).stream().findAny().isPresent();
    }

    public Streamable<ExtensionScan> findExtensionScansByStatus(ScanStatus status) {
        return extensionScanRepo.findByStatus(status);
    }

    public long countExtensionScansByStatus(ScanStatus status) {
        return extensionScanRepo.countByStatus(status);
    }

    public org.springframework.data.domain.Page<ExtensionScan> findScansFullyFiltered(
            @Nullable Collection<ScanStatus> statuses,
            @Nullable String namespace,
            @Nullable String publisher,
            @Nullable String name,
            @Nullable LocalDateTime startedFrom,
            @Nullable LocalDateTime startedTo,
            @Nullable Collection<String> checkTypes,
            @Nullable Collection<String> scannerNames,
            @Nullable Boolean enforcedOnly,
            org.eclipse.openvsx.admin.ScanAPI.@Nullable AdminDecisionFilterValues adminDecisionFilter,
            boolean includeCheckErrors,
            org.springframework.data.domain.Pageable pageable
    ) {
        // Convert enums to strings for native query
        var statusesParam = (statuses == null || statuses.isEmpty())
                ? null
                : statuses.stream().map(ScanStatus::name).toList();
        var namespaceParam = (namespace == null || namespace.isBlank()) ? null : namespace;
        var publisherParam = (publisher == null || publisher.isBlank()) ? null : publisher;
        var nameParam = (name == null || name.isBlank()) ? null : name;
        // PostgreSQL doesn't allow empty IN clauses. When filter is disabled, we pass a
        // dummy list combined with a boolean flag in the query to skip the check entirely.
        var applyCheckTypesFilter = checkTypes != null && !checkTypes.isEmpty();
        var applyScannerNamesFilter = scannerNames != null && !scannerNames.isEmpty();
        var checkTypesParam = applyCheckTypesFilter ? checkTypes : List.of("");
        var scannerNamesParam = applyScannerNamesFilter ? scannerNames : List.of("");

        // Admin decision filter
        var applyAdminDecisionFilter = adminDecisionFilter != null && adminDecisionFilter.hasFilter();
        var filterAllowed = adminDecisionFilter != null && adminDecisionFilter.filterAllowed();
        var filterBlocked = adminDecisionFilter != null && adminDecisionFilter.filterBlocked();
        var filterNeedsReview = adminDecisionFilter != null && adminDecisionFilter.filterNeedsReview();

        return extensionScanRepo.findScansFullyFiltered(
                statusesParam,
                namespaceParam,
                publisherParam,
                nameParam,
                startedFrom,
                startedTo,
                checkTypesParam,
                applyCheckTypesFilter,
                scannerNamesParam,
                applyScannerNamesFilter,
                enforcedOnly,
                applyAdminDecisionFilter,
                filterAllowed,
                filterBlocked,
                filterNeedsReview,
                includeCheckErrors,
                pageable);
    }

    public long countScansForStatistics(
            ScanStatus status,
            @Nullable LocalDateTime startedFrom,
            @Nullable LocalDateTime startedTo,
            @Nullable Collection<String> checkTypes,
            @Nullable Collection<String> scannerNames,
            @Nullable Boolean enforcedOnly
    ) {
        // PostgreSQL doesn't allow empty IN clauses. When filter is disabled, we pass a
        // dummy list combined with a boolean flag in the query to skip the check entirely.
        var applyCheckTypesFilter = checkTypes != null && !checkTypes.isEmpty();
        var applyScannerNamesFilter = scannerNames != null && !scannerNames.isEmpty();
        var checkTypesParam = applyCheckTypesFilter ? checkTypes : List.of("");
        var scannerNamesParam = applyScannerNamesFilter ? scannerNames : List.of("");

        return extensionScanRepo.countForStatistics(
                status.name(),
                startedFrom,
                startedTo,
                checkTypesParam,
                applyCheckTypesFilter,
                scannerNamesParam,
                applyScannerNamesFilter,
                enforcedOnly);
    }

    public long countAdminDecisionsForStatistics(
            String decision,
            @Nullable LocalDateTime startedFrom,
            @Nullable LocalDateTime startedTo,
            @Nullable Collection<String> checkTypes,
            @Nullable Collection<String> scannerNames,
            @Nullable Boolean enforcedOnly
    ) {
        // PostgreSQL doesn't allow empty IN clauses. When filter is disabled, we pass a
        // dummy list combined with a boolean flag in the query to skip the check entirely.
        var applyCheckTypesFilter = checkTypes != null && !checkTypes.isEmpty();
        var applyScannerNamesFilter = scannerNames != null && !scannerNames.isEmpty();
        var checkTypesParam = applyCheckTypesFilter ? checkTypes : List.of("");
        var scannerNamesParam = applyScannerNamesFilter ? scannerNames : List.of("");

        return adminScanDecisionRepo.countForStatistics(
                decision,
                startedFrom,
                startedTo,
                checkTypesParam,
                applyCheckTypesFilter,
                scannerNamesParam,
                applyScannerNamesFilter,
                enforcedOnly);
    }

    public ExtensionValidationFailure saveValidationFailure(ExtensionValidationFailure failure) {
        return extensionValidationFailureRepo.save(failure);
    }

    public Streamable<ExtensionValidationFailure> findValidationFailures(ExtensionScan scan) {
        return extensionValidationFailureRepo.findByScan(scan);
    }

    public List<String> findDistinctValidationFailureCheckTypes() {
        return extensionValidationFailureRepo.findDistinctCheckTypes();
    }

    public AdminScanDecision saveAdminScanDecision(AdminScanDecision decision) {
        return adminScanDecisionRepo.save(decision);
    }

    public AdminScanDecision findAdminScanDecision(ExtensionScan scan) {
        return adminScanDecisionRepo.findByScan(scan);
    }

    public AdminScanDecision findAdminScanDecisionByScanId(long scanId) {
        return adminScanDecisionRepo.findByScanId(scanId);
    }

    public long countAdminScanDecisions(String decision) {
        return adminScanDecisionRepo.countByDecision(decision);
    }

    public long countAdminScanDecisions(UserData decidedBy) {
        return adminScanDecisionRepo.countByDecidedBy(decidedBy);
    }

    public ExtensionThreat saveExtensionThreat(ExtensionThreat threat) {
        return extensionThreatRepo.save(threat);
    }

    public Streamable<ExtensionThreat> findExtensionThreats(ExtensionScan scan) {
        return extensionThreatRepo.findByScan(scan);
    }

    public List<String> findDistinctThreatScannerTypes() {
        return extensionThreatRepo.findDistinctScannerTypes();
    }

    public Streamable<ExtensionThreat> findExtensionThreats(ExtensionScan scan, String type) {
        return extensionThreatRepo.findByScanAndType(scan, type);
    }

    public FileDecision saveFileDecision(FileDecision decision) {
        return fileDecisionRepo.save(decision);
    }

    public FileDecision findFileDecision(long id) {
        return fileDecisionRepo.findById(id);
    }

    public FileDecision findFileDecisionByHash(String fileHash) {
        return fileDecisionRepo.findByFileHash(fileHash);
    }

    public long countFileDecisions(String decision) {
        return fileDecisionRepo.countByDecision(decision);
    }

    public long countFileDecisions(UserData decidedBy) {
        return fileDecisionRepo.countByDecidedBy(decidedBy);
    }

    public long countFileDecisionsByDateRange(String decision, LocalDateTime decidedFrom, LocalDateTime decidedTo) {
        return fileDecisionRepo.countByDecisionAndDateRange(decision, decidedFrom, decidedTo);
    }

    public void deleteFileDecision(long id) {
        fileDecisionRepo.deleteById(id);
    }

    public Page<FileDecision> findFileDecisionsFiltered(
            String decision,
            String publisher,
            String namespace,
            String name,
            LocalDateTime decidedFrom,
            LocalDateTime decidedTo,
            Pageable pageable
    ) {
        var decisionParam = (decision == null || decision.isBlank()) ? null : decision.toUpperCase();
        var publisherParam = (publisher == null || publisher.isBlank()) ? null : publisher;
        var namespaceParam = (namespace == null || namespace.isBlank()) ? null : namespace;
        var nameParam = (name == null || name.isBlank()) ? null : name;

        return fileDecisionRepo.findFilesFiltered(
                decisionParam,
                publisherParam,
                namespaceParam,
                nameParam,
                decidedFrom,
                decidedTo,
                pageable);
    }

    public ScanCheckResult saveScanCheckResult(ScanCheckResult result) {
        return scanCheckResultRepo.save(result);
    }

    public List<ScanCheckResult> findScanCheckResultsByScanId(long scanId) {
        return scanCheckResultRepo.findByScanIdOrderByStartedAtAsc(scanId);
    }

    public boolean hasScanCheckResult(long scanId, String checkType) {
        return scanCheckResultRepo.existsByScanIdAndCheckType(scanId, checkType);
    }

    public List<Tier> findAllTiers() {
        return tierRepo.findAllByOrderByIdAsc();
    }

    public Tier findTier(String name) {
        return tierRepo.findByNameIgnoreCase(name);
    }

    public List<Tier> findTiersByTierType(TierType tierType) {
        return tierRepo.findByTierType(tierType);
    }

    public List<Tier> findTiersByTierTypeExcludingTier(TierType tierType, Tier tier) {
        return tierRepo.findByTierTypeAndIdNot(tierType, tier.getId());
    }

    public Tier upsertTier(Tier tier) {
        return tierRepo.save(tier);
    }

    public void deleteTier(Tier tier) {
        tierRepo.delete(tier);
    }

    public List<Customer> findAllCustomers() {
        return customerRepo.findAll();
    }

    public List<Customer> findCustomersByTier(Tier tier) {
        return customerRepo.findByTier(tier);
    }

    public int countCustomersByTier(Tier tier) {
        return customerRepo.countCustomersByTier(tier);
    }

    public Optional<Customer> findCustomerById(long id) {
        return customerRepo.findById(id);
    }

    public Customer findCustomer(String name) {
        return customerRepo.findByNameIgnoreCase(name);
    }

    public Customer upsertCustomer(Customer customer) {
        return customerRepo.save(customer);
    }

    public void deleteCustomer(Customer customer) {
        customerRepo.delete(customer);
    }

    public Streamable<CustomerMembership> findCustomerMemberships(Customer customer) {
        return customerMembershipRepo.findByCustomer(customer);
    }

    public CustomerMembership findCustomerMembership(UserData user, Customer customer) {
        return customerMembershipRepo.findByUserAndCustomer(user, customer);
    }

    public Streamable<CustomerMembership> findCustomerMemberships(UserData user) {
        return customerMembershipRepo.findByUserOrderByCustomerName(user);
    }

    public List<UsageStats> findUsageStatsByCustomerAndDate(Customer customer, LocalDateTime date) {
        var startTime = date.truncatedTo(ChronoUnit.DAYS);
        var endTime = date.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusMinutes(1);

        return usageStatsRepository.findUsageStatsByCustomerAndWindowStartBetween(customer, startTime, endTime);
    }

    public UsageStats saveUsageStats(UsageStats usageStats) {
        return usageStatsRepository.save(usageStats);
    }

    public Streamable<RateLimitToken> findActiveRateLimitTokens(Customer customer) {
        return rateLimitTokenRepository.findByCustomerAndActiveTrue(customer);
    }

    public RateLimitToken findRateLimitToken(long id) {
        return rateLimitTokenRepository.findById(id);
    }

    public RateLimitToken findRateLimitToken(String value) {
        return rateLimitTokenRepository.findByValue(value);
    }

    public boolean hasRateLimitToken(String value) {
        return rateLimitTokenRepository.existsByValue(value);
    }

    public DailyUsageStats findDailyUsageStats(Customer customer, LocalDate date) {
        return dailyUsageStatsRepository.findDailyUsageStatsByCustomerAndDate(customer, date);
    }

    public List<LocalDateTime> findUnprocessedDaysForDailyUsage(Customer customer) {
        return dailyUsageStatsRepository.findUnprocessedDays(customer);
    }

    public DailyUsageStats saveDailyUsageStats(DailyUsageStats dailyUsageStats) {
        return dailyUsageStatsRepository.save(dailyUsageStats);
    }
}
