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

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.json.TargetPlatformVersionJson;
import org.eclipse.openvsx.json.VersionTargetPlatformsJson;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.web.SitemapRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.eclipse.openvsx.entities.FileResource.*;

@Component
public class RepositoryService {

    private static final int MAX_VERSIONS = 100;
    private static final Sort VERSIONS_SORT = Sort.by(Sort.Direction.DESC, "semver.major", "semver.minor", "semver.patch")
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
    private final PersonalAccessTokenRepository tokenRepo;
    private final PersonalAccessTokenJooqRepository tokenJooqRepo;
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
    private final MigrationItemJooqRepository migrationItemJooqRepo;
    private final SignatureKeyPairRepository signatureKeyPairRepo;
    private final SignatureKeyPairJooqRepository signatureKeyPairJooqRepo;
    private final ExtensionScanRepository extensionScanRepo;
    private final ExtensionValidationFailureRepository extensionValidationFailureRepo;
    private final AdminScanDecisionRepository adminScanDecisionRepo;
    private final ExtensionThreatRepository extensionThreatRepo;
    private final FileDecisionRepository fileDecisionRepo;
    private final ScanCheckResultRepository scanCheckResultRepo;
    private final TierRepository tierRepo;
    private final CustomerRepository customerRepo;
    private final CustomerMembershipRepository customerMembershipRepo;
    private final UsageStatsRepository usageStatsRepository;

    public RepositoryService(
            NamespaceRepository namespaceRepo,
            NamespaceJooqRepository namespaceJooqRepo,
            ExtensionRepository extensionRepo,
            ExtensionVersionRepository extensionVersionRepo,
            FileResourceRepository fileResourceRepo,
            ExtensionReviewRepository extensionReviewRepo,
            UserDataRepository userDataRepo,
            NamespaceMembershipRepository membershipRepo,
            PersonalAccessTokenRepository tokenRepo,
            PersonalAccessTokenJooqRepository tokenJooqRepo,
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
            MigrationItemJooqRepository migrationItemJooqRepo,
            SignatureKeyPairRepository signatureKeyPairRepo,
            SignatureKeyPairJooqRepository signatureKeyPairJooqRepo,
            ExtensionScanRepository extensionScanRepo,
            AdminScanDecisionRepository adminScanDecisionRepo,
            ExtensionValidationFailureRepository extensionValidationFailureRepo,
            ExtensionThreatRepository extensionThreatRepo,
            FileDecisionRepository fileDecisionRepo,
            ScanCheckResultRepository scanCheckResultRepo,
            TierRepository tierRepo,
            CustomerRepository customerRepo,
            CustomerMembershipRepository customerMembershipRepo,
            UsageStatsRepository usageStatsRepository
    ) {
        this.namespaceRepo = namespaceRepo;
        this.namespaceJooqRepo = namespaceJooqRepo;
        this.extensionRepo = extensionRepo;
        this.extensionVersionRepo = extensionVersionRepo;
        this.fileResourceRepo = fileResourceRepo;
        this.extensionReviewRepo = extensionReviewRepo;
        this.userDataRepo = userDataRepo;
        this.membershipRepo = membershipRepo;
        this.tokenRepo = tokenRepo;
        this.tokenJooqRepo = tokenJooqRepo;
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
        this.migrationItemJooqRepo = migrationItemJooqRepo;
        this.signatureKeyPairRepo = signatureKeyPairRepo;
        this.signatureKeyPairJooqRepo = signatureKeyPairJooqRepo;
        this.extensionScanRepo = extensionScanRepo;
        this.adminScanDecisionRepo = adminScanDecisionRepo;
        this.extensionValidationFailureRepo = extensionValidationFailureRepo;
        this.extensionThreatRepo = extensionThreatRepo;
        this.fileDecisionRepo = fileDecisionRepo;
        this.scanCheckResultRepo = scanCheckResultRepo;
        this.tierRepo = tierRepo;
        this.customerRepo = customerRepo;
        this.customerMembershipRepo = customerMembershipRepo;
        this.usageStatsRepository = usageStatsRepository;
    }

    public Namespace findNamespace(String name) {
        return namespaceRepo.findByNameIgnoreCase(name);
    }

    public String findNamespaceName(String name) {
        return namespaceJooqRepo.findNameByNameIgnoreCase(name);
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
        return extensionVersionRepo.findByVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(version, targetPlatform, extensionName, namespace);
    }

    public ExtensionVersion findVersion(UserData user, String version, String targetPlatform, String extensionName, String namespace) {
        return extensionVersionRepo.findByPublishedWithUserAndVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(user, version, targetPlatform, extensionName, namespace);
    }

    public Streamable<ExtensionVersion> findVersions(Extension extension) {
         return extensionVersionRepo.findByExtension(extension);
    }

    public Streamable<ExtensionVersion> findActiveVersions(Extension extension) {
         return extensionVersionRepo.findByExtensionAndActiveTrue(extension);
    }

    public Page<ExtensionVersion> findActiveVersionsSorted(String namespace, String extension, PageRequest page) {
        return extensionVersionRepo.findByExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(extension, namespace, page.withSort(VERSIONS_SORT));
    }

    public Page<ExtensionVersion> findActiveVersionsSorted(String namespace, String extension, String targetPlatform, PageRequest page) {
        return extensionVersionRepo.findByTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(targetPlatform, extension, namespace, page.withSort(VERSIONS_SORT));
    }

    public Page<String> findActiveVersionStringsSorted(String namespace, String extension, String targetPlatform, PageRequest page) {
        return extensionVersionJooqRepo.findActiveVersionStringsSorted(namespace, extension, targetPlatform, page);
    }

    public List<String> findVersionStringsSorted(Extension extension, String targetPlatform, boolean onlyActive) {
        return extensionVersionJooqRepo.findVersionStringsSorted(extension.getId(), targetPlatform, onlyActive, MAX_VERSIONS);
    }

    public Map<Long, List<String>> findActiveVersionStringsSorted(Collection<Long> extensionIds, String targetPlatform) {
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
        return extensionRepo.findDistinctByVersionsPublishedWithUser(user);
    }

    public Streamable<ExtensionVersion> findVersionsByAccessToken(PersonalAccessToken publishedWith, boolean active) {
        return extensionVersionRepo.findByPublishedWithAndActive(publishedWith, active);
    }

    public Streamable<ExtensionVersion> findVersionsByUser(UserData user, boolean active) {
        return extensionVersionRepo.findByPublishedWithUserAndActive(user, active);
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

    public FileResource findFileByName(String namespace, String extension, String targetPlatform, String version, String name) {
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

    public FileResource findFileByType(String namespace, String extension, String targetPlatform, String version, String type) {
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

    public UserData findUserByLoginName(String provider, String loginName) {
        return userDataRepo.findByProviderAndLoginName(provider, loginName);
    }

    public Page<UserData> findUsersByLoginNameStartingWith(String loginNameStart, int limit) {
        return userDataRepo.findByLoginNameStartingWith(loginNameStart, Pageable.ofSize(limit));
    }

    public long countUsers() {
        return userDataRepo.count();
    }

    public NamespaceMembership findMembership(UserData user, Namespace namespace) {
        return membershipRepo.findByUserAndNamespace(user, namespace);
    }

    public boolean hasMembership(UserData user, Namespace namespace) {
        return membershipJooqRepo.hasMembership(user, namespace);
    }

    public boolean isVerified(Namespace namespace, UserData user) {
        return membershipJooqRepo.isVerified(namespace, user);
    }

    public Streamable<NamespaceMembership> findMemberships(Namespace namespace, String role) {
        return membershipRepo.findByNamespaceAndRoleIgnoreCase(namespace, role);
    }

    public boolean hasMemberships(Namespace namespace, String role) {
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

    public Streamable<PersonalAccessToken> findAccessTokens(UserData user) {
        return tokenRepo.findByUser(user);
    }

    public Streamable<PersonalAccessToken> findAllAccessTokens() {
        return tokenRepo.findAll();
    }

    public Streamable<PersonalAccessToken> findActiveAccessTokens(UserData user) {
        return tokenRepo.findByUserAndActiveTrue(user);
    }

    public long countActiveAccessTokens(UserData user) {
        return tokenRepo.countByUserAndActiveTrue(user);
    }

    public PersonalAccessToken findAccessToken(String value) {
        return  tokenRepo.findByValue(value);
    }

    public PersonalAccessToken findAccessToken(long id) {
        return tokenRepo.findById(id);
    }

    public List<PersonalAccessToken> findExpiringAccessTokensWithoutNotification(LocalDateTime expirationTime, Pageable pageable) {
        return tokenRepo.findByExpiresTimestampLessThanEqualAndActiveTrueAndNotifiedFalseOrderById(expirationTime, pageable);
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

    public List<String> findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn(String storageType, List<String> names) {
        return downloadCountRepo.findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn(storageType, names);
    }

    public List<String> findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn(String storageType, List<String> names) {
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

    public List<ExtensionVersion> findActiveExtensionVersions(Collection<Long> extensionIds, String targetPlatform) {
        return extensionVersionJooqRepo.findAllActiveByExtensionIdAndTargetPlatform(extensionIds, targetPlatform);
    }

    public List<FileResource> findFileResourcesByExtensionVersionIdAndType(Collection<Long> extensionVersionIds, Collection<String> types) {
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

    public Map<Integer,Integer> countActiveExtensionPublishersGroupedByExtensionsPublished() {
        return adminStatisticCalculationsRepo.countActiveExtensionPublishersGroupedByExtensionsPublished();
    }

    public Map<Integer,Integer> countActiveExtensionsGroupedByExtensionReviewRating() {
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

    public Streamable<ExtensionVersion> findTargetPlatformVersions(String version, String extensionName, String namespaceName) {
        return extensionVersionRepo.findByVersionAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(version, extensionName, namespaceName);
    }

    public int countVersions(String namespaceName, String extensionName) {
        return extensionVersionJooqRepo.count(namespaceName, extensionName);
    }

    public Slice<MigrationItem> findNotMigratedItems(Pageable page) {
        return migrationItemRepo.findByMigrationScheduledFalseOrderById(page);
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

    public List<Extension> findActiveExtensionsForUrls(Namespace namespace) {
        return extensionJooqRepo.findActiveExtensionsForUrls(namespace);
    }

    public ExtensionVersion findExtensionVersion(String namespace, String extension, String targetPlatform, String version) {
        return extensionVersionJooqRepo.find(namespace, extension, targetPlatform, version);
    }

    /**
     * Find an extension version regardless of active status.
     * Use this for admin operations on quarantined/inactive extensions.
     */
    public ExtensionVersion findExtensionVersionIncludingInactive(String namespace, String extension, String targetPlatform, String version) {
        return extensionVersionJooqRepo.findIncludingInactive(namespace, extension, targetPlatform, version);
    }

    public ExtensionVersion findLatestVersionForAllUrls(Extension extension, String targetPlatform, boolean onlyPreRelease, boolean onlyActive) {
        return extensionVersionJooqRepo.findLatestForAllUrls(extension, targetPlatform, onlyPreRelease, onlyActive);
    }

    public ExtensionVersion findLatestVersion(Extension extension, String targetPlatform, boolean onlyPreRelease, boolean onlyActive) {
        return extensionVersionJooqRepo.findLatest(extension, targetPlatform, onlyPreRelease, onlyActive);
    }

    public ExtensionVersion findLatestVersion(String namespaceName, String extensionName, String targetPlatform, boolean onlyPreRelease, boolean onlyActive) {
        return extensionVersionJooqRepo.findLatest(namespaceName, extensionName, targetPlatform, onlyPreRelease, onlyActive);
    }

    public List<ExtensionVersion> findLatestVersions(Namespace namespace) {
        return extensionVersionJooqRepo.findLatest(namespace);
    }

    public List<ExtensionVersion> findLatestVersions(Collection<Long> extensionIds) {
        return extensionVersionJooqRepo.findLatest(extensionIds);
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

    public int deactivateAccessTokens(UserData user) {
        return tokenRepo.updateActiveSetFalse(user);
    }

    public List<PersonalAccessToken> expireAccessTokens(LocalDateTime timestamp) {
        return tokenRepo.expireAccessTokens(timestamp);
    }

    public int updateExpiresTimeForLegacyAccessTokens(LocalDateTime timestamp) {
        return tokenRepo.updateExpiresTimeForLegacyAccessTokens(timestamp);
    }

    public List<String> findActiveExtensionNames(Namespace namespace) {
        return extensionJooqRepo.findActiveExtensionNames(namespace);
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

    public boolean hasAccessToken(String value) {
        return tokenJooqRepo.hasToken(value);
    }

    public boolean canPublishInNamespace(UserData user, Namespace namespace) {
        return membershipJooqRepo.canPublish(user, namespace);
    }

    public String findSignatureKeyPairPublicId(String namespace, String extension, String targetPlatform, String version) {
        return signatureKeyPairJooqRepo.findPublicId(namespace, extension, targetPlatform, version);
    }

    public String findFirstUnresolvedDependency(List<ExtensionId> dependencies) {
        return extensionJooqRepo.findFirstUnresolvedDependency(dependencies);
    }

    public PersonalAccessToken findAccessToken(UserData user, String description) {
        return tokenRepo.findByUserAndDescriptionAndActiveTrue(user, description);
    }

    public NamespaceMembership findFirstMembership(String namespaceName) {
        return membershipRepo.findFirstByNamespaceNameIgnoreCase(namespaceName);
    }

    public ExtensionVersion findLatestReplacement(long extensionId, String targetPlatform, boolean onlyPreRelease, boolean onlyActive) {
        return extensionVersionJooqRepo.findLatestReplacement(extensionId, targetPlatform, onlyPreRelease, onlyActive);
    }

    public boolean hasExtension(String namespace, String extension) {
        return extensionJooqRepo.hasExtension(namespace, extension);
    }

    public Streamable<Extension> findDeprecatedExtensions(Extension replacement) {
        return extensionRepo.findByReplacement(replacement);
    }

    public List<MigrationItem> findRemoveFileResourceTypeResourceMigrationItems(int offset, int limit) {
        return migrationItemJooqRepo.findRemoveFileResourceTypeResourceMigrationItems(offset, limit);
    }

    public boolean isDeleteAllVersions(String namespaceName, String extensionName, List<TargetPlatformVersionJson> targetVersions, UserData user) {
        return extensionVersionJooqRepo.isDeleteAllVersions(namespaceName, extensionName, targetVersions, user);
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
                limit
        );
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
                limit
        );
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

    public Streamable<ExtensionScan> findExtensionScans(ExtensionVersion version) {
        var extension = version.getExtension();
        var namespace = extension.getNamespace();
        return extensionScanRepo.findByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatform(
            namespace.getName(), extension.getName(), version.getVersion(), version.getTargetPlatform());
    }

    public ExtensionScan findLatestExtensionScan(ExtensionVersion version) {
        var extension = version.getExtension();
        var namespace = extension.getNamespace();
        return extensionScanRepo.findFirstByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatformOrderByStartedAtDesc(
            namespace.getName(), extension.getName(), version.getVersion(), version.getTargetPlatform());
    }

    public Streamable<ExtensionScan> findExtensionScans(Extension extension) {
        var namespace = extension.getNamespace();
        return extensionScanRepo.findByNamespaceNameAndExtensionName(namespace.getName(), extension.getName());
    }

    public Streamable<ExtensionScan> findExtensionScansByNamespace(String namespaceName) {
        return extensionScanRepo.findByNamespaceName(namespaceName);
    }

    public Streamable<ExtensionScan> findExtensionScansByStatus(ScanStatus status) {
        return extensionScanRepo.findByStatus(status);
    }

    public Streamable<ExtensionScan> findInProgressExtensionScans() {
        return extensionScanRepo.findByCompletedAtIsNull();
    }

    public long countExtensionScansByStatus(ScanStatus status) {
        return extensionScanRepo.countByStatus(status);
    }

    /** Check if a scan exists for a specific version with a given status */
    public boolean hasExtensionScanWithStatus(ExtensionVersion version, ScanStatus status) {
        var extension = version.getExtension();
        var namespace = extension.getNamespace();
        return extensionScanRepo.existsByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatformAndStatus(
            namespace.getName(), extension.getName(), version.getVersion(), version.getTargetPlatform(), status);
    }

    public Streamable<ExtensionScan> findAllExtensionScans() {
        return extensionScanRepo.findAllByOrderByStartedAtDesc();
    }

    public org.springframework.data.domain.Page<ExtensionScan> findScansFiltered(
            Collection<ScanStatus> statuses,
            String namespace,
            String publisher,
            String name,
            LocalDateTime startedFrom,
            LocalDateTime startedTo,
            org.springframework.data.domain.Pageable pageable
    ) {
        // Convert empty collections to null, and enums to strings for native query
        var statusesParam = (statuses == null || statuses.isEmpty())
            ? null
            : statuses.stream().map(ScanStatus::name).toList();
        var namespaceParam = (namespace == null || namespace.isBlank()) ? null : namespace;
        var publisherParam = (publisher == null || publisher.isBlank()) ? null : publisher;
        var nameParam = (name == null || name.isBlank()) ? null : name;

        return extensionScanRepo.findScansFiltered(
            statusesParam, namespaceParam, publisherParam, nameParam, startedFrom, startedTo, pageable
        );
    }

    public long countScansFiltered(
            Collection<ScanStatus> statuses,
            String namespace,
            String publisher,
            String name,
            LocalDateTime startedFrom,
            LocalDateTime startedTo
    ) {
        // Convert enums to strings for native query
        var statusesParam = (statuses == null || statuses.isEmpty())
            ? null
            : statuses.stream().map(ScanStatus::name).toList();
        var namespaceParam = (namespace == null || namespace.isBlank()) ? null : namespace;
        var publisherParam = (publisher == null || publisher.isBlank()) ? null : publisher;
        var nameParam = (name == null || name.isBlank()) ? null : name;

        return extensionScanRepo.countScansFiltered(
            statusesParam, namespaceParam, publisherParam, nameParam, startedFrom, startedTo
        );
    }

    public long countExtensionScansByStatusAndDateRange(ScanStatus status, LocalDateTime startedFrom, LocalDateTime startedTo) {
        return extensionScanRepo.countByStatusAndDateRange(status, startedFrom, startedTo);
    }

    public long countExtensionScansByStatusDateRangeAndEnforcement(
            ScanStatus status, LocalDateTime startedFrom, LocalDateTime startedTo, boolean enforcedOnly) {
        return extensionScanRepo.countByStatusDateRangeAndEnforcement(status, startedFrom, startedTo, enforcedOnly);
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
            @Nullable org.eclipse.openvsx.admin.ScanAPI.AdminDecisionFilterValues adminDecisionFilter,
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
            statusesParam, namespaceParam, publisherParam, nameParam,
            startedFrom, startedTo, checkTypesParam, applyCheckTypesFilter,
            scannerNamesParam, applyScannerNamesFilter, enforcedOnly,
            applyAdminDecisionFilter, filterAllowed, filterBlocked, filterNeedsReview,
            includeCheckErrors, pageable
        );
    }

    public long countScansFullyFiltered(
            @Nullable Collection<ScanStatus> statuses,
            @Nullable String namespace,
            @Nullable String publisher,
            @Nullable String name,
            @Nullable LocalDateTime startedFrom,
            @Nullable LocalDateTime startedTo,
            @Nullable Collection<String> checkTypes,
            @Nullable Collection<String> scannerNames,
            @Nullable Boolean enforcedOnly,
            @Nullable org.eclipse.openvsx.admin.ScanAPI.AdminDecisionFilterValues adminDecisionFilter,
            boolean includeCheckErrors
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

        return extensionScanRepo.countScansFullyFiltered(
            statusesParam, namespaceParam, publisherParam, nameParam,
            startedFrom, startedTo, checkTypesParam, applyCheckTypesFilter,
            scannerNamesParam, applyScannerNamesFilter, enforcedOnly,
            applyAdminDecisionFilter, filterAllowed, filterBlocked, filterNeedsReview,
            includeCheckErrors
        );
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
            status.name(), startedFrom, startedTo,
            checkTypesParam, applyCheckTypesFilter,
            scannerNamesParam, applyScannerNamesFilter, enforcedOnly
        );
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
            decision, startedFrom, startedTo, checkTypesParam, applyCheckTypesFilter,
            scannerNamesParam, applyScannerNamesFilter, enforcedOnly
        );
    }

    public ExtensionValidationFailure saveValidationFailure(ExtensionValidationFailure failure) {
        return extensionValidationFailureRepo.save(failure);
    }

    public ExtensionValidationFailure findValidationFailure(long id) {
        return extensionValidationFailureRepo.findById(id);
    }

    public Streamable<ExtensionValidationFailure> findValidationFailures(ExtensionScan scan) {
        return extensionValidationFailureRepo.findByScan(scan);
    }

    public List<String> findDistinctValidationFailureRuleNames() {
        return extensionValidationFailureRepo.findDistinctRuleNames();
    }

    public List<String> findDistinctValidationFailureCheckTypes() {
        return extensionValidationFailureRepo.findDistinctCheckTypes();
    }

    public Streamable<ExtensionValidationFailure> findValidationFailuresByType(String checkType) {
        return extensionValidationFailureRepo.findByCheckType(checkType);
    }

    public Streamable<ExtensionValidationFailure> findValidationFailures(ExtensionScan scan, String checkType) {
        return extensionValidationFailureRepo.findByScanAndCheckType(scan, checkType);
    }

    public long countValidationFailures(ExtensionScan scan) {
        return extensionValidationFailureRepo.countByScan(scan);
    }

    public long countValidationFailuresByType(String checkType) {
        return extensionValidationFailureRepo.countByCheckType(checkType);
    }

    public boolean hasValidationFailures(ExtensionScan scan) {
        return extensionValidationFailureRepo.existsByScan(scan);
    }

    public boolean hasValidationFailuresOfType(ExtensionScan scan, String checkType) {
        return extensionValidationFailureRepo.existsByScanAndCheckType(scan, checkType);
    }

    public AdminScanDecision saveAdminScanDecision(AdminScanDecision decision) {
        return adminScanDecisionRepo.save(decision);
    }

    public AdminScanDecision findAdminScanDecision(long id) {
        return adminScanDecisionRepo.findById(id);
    }

    public AdminScanDecision findAdminScanDecision(ExtensionScan scan) {
        return adminScanDecisionRepo.findByScan(scan);
    }

    public AdminScanDecision findAdminScanDecisionByScanId(long scanId) {
        return adminScanDecisionRepo.findByScanId(scanId);
    }

    public boolean hasAdminScanDecision(ExtensionScan scan) {
        return adminScanDecisionRepo.existsByScan(scan);
    }

    public boolean hasAdminScanDecisionByScanId(long scanId) {
        return adminScanDecisionRepo.existsByScanId(scanId);
    }

    public long countAdminScanDecisions(String decision) {
        return adminScanDecisionRepo.countByDecision(decision);
    }

    public long countAdminScanDecisionsByDateRange(String decision, LocalDateTime startedFrom, LocalDateTime startedTo) {
        return adminScanDecisionRepo.countByDecisionAndDateRange(decision, startedFrom, startedTo);
    }

    public long countAdminScanDecisionsByEnforcement(String decision, boolean enforcedOnly) {
        return adminScanDecisionRepo.countByDecisionAndEnforcement(decision, enforcedOnly);
    }

    public void deleteAdminScanDecision(long id) {
        adminScanDecisionRepo.deleteById(id);
    }

    public ExtensionThreat saveExtensionThreat(ExtensionThreat threat) {
        return extensionThreatRepo.save(threat);
    }

    public ExtensionThreat findExtensionThreat(long id) {
        return extensionThreatRepo.findById(id);
    }

    public Streamable<ExtensionThreat> findExtensionThreats(ExtensionScan scan) {
        return extensionThreatRepo.findByScan(scan);
    }

    public Streamable<ExtensionThreat> findExtensionThreatsByScanId(long scanId) {
        return extensionThreatRepo.findByScanId(scanId);
    }

    public Streamable<ExtensionThreat> findExtensionThreatsByFileHash(String fileHash) {
        return extensionThreatRepo.findByFileHash(fileHash);
    }

    public long countExtensionThreats(ExtensionScan scan) {
        return extensionThreatRepo.countByScan(scan);
    }

    public boolean hasExtensionThreats(ExtensionScan scan) {
        return extensionThreatRepo.existsByScan(scan);
    }

    public List<String> findDistinctThreatScannerTypes() {
        return extensionThreatRepo.findDistinctScannerTypes();
    }

    public List<String> findDistinctThreatRuleNames() {
        return extensionThreatRepo.findDistinctRuleNames();
    }

    public Streamable<ExtensionThreat> findExtensionThreatsByType(String type) {
        return extensionThreatRepo.findByType(type);
    }

    public Streamable<ExtensionThreat> findExtensionThreats(ExtensionScan scan, String type) {
        return extensionThreatRepo.findByScanAndType(scan, type);
    }

    public Streamable<ExtensionThreat> findExtensionThreatsAfter(LocalDateTime date) {
        return extensionThreatRepo.findByDetectedAtAfter(date);
    }

    public Streamable<ExtensionThreat> findExtensionThreatsOrdered(ExtensionScan scan) {
        return extensionThreatRepo.findByScanOrderByDetectedAtAsc(scan);
    }

    public long countExtensionThreatsByType(String type) {
        return extensionThreatRepo.countByType(type);
    }

    public boolean hasExtensionThreatsOfType(ExtensionScan scan, String type) {
        return extensionThreatRepo.existsByScanAndType(scan, type);
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

    public boolean hasFileDecision(String fileHash) {
        return fileDecisionRepo.existsByFileHash(fileHash);
    }

    public long countFileDecisions(String decision) {
        return fileDecisionRepo.countByDecision(decision);
    }

    public long countAllFileDecisions() {
        return fileDecisionRepo.count();
    }

    public long countFileDecisionsByDateRange(String decision, LocalDateTime decidedFrom, LocalDateTime decidedTo) {
        return fileDecisionRepo.countByDecisionAndDateRange(decision, decidedFrom, decidedTo);
    }

    public void deleteFileDecision(long id) {
        fileDecisionRepo.deleteById(id);
    }

    public void deleteFileDecisionByHash(String fileHash) {
        fileDecisionRepo.deleteByFileHash(fileHash);
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
            decisionParam, publisherParam, namespaceParam, nameParam, decidedFrom, decidedTo, pageable
        );
    }

    public List<FileDecision> findFileDecisionsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return fileDecisionRepo.findByIdIn(ids);
    }

    public ScanCheckResult saveScanCheckResult(ScanCheckResult result) {
        return scanCheckResultRepo.save(result);
    }

    public List<ScanCheckResult> findScanCheckResults(ExtensionScan scan) {
        return scanCheckResultRepo.findByScanOrderByStartedAtAsc(scan);
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
        var startTime = date.truncatedTo(ChronoUnit.DAYS).minusMinutes(5);
        var endTime = date.truncatedTo(ChronoUnit.DAYS).plusDays(1);

        return usageStatsRepository.findUsageStatsByCustomerAndWindowStartBetween(customer, startTime, endTime);
    }

    public UsageStats saveUsageStats(UsageStats usageStats) {
        return usageStatsRepository.save(usageStats);
    }
}
