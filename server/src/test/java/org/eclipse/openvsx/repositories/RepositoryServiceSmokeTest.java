/********************************************************************************
 * Copyright (c) 2022 Wladimir Hofmann and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.AdminScanDecision;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.DailyUsageStats;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionThreat;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.entities.TierType;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UsageStats;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.scanning.NamespaceOwnershipCheckScanner;
import org.eclipse.openvsx.util.ChangesCursor;
import org.eclipse.openvsx.util.ExtensionId;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Run the DB queries and assert no DB error, just to ensure that the queries
 * are consistent with the schema.
 */
@SpringBootTest
class RepositoryServiceSmokeTest extends AbstractPostgresContainerTest {

    private static final List<String> STRING_LIST = List.of("id1", "id2");

    private static final List<Long> LONG_LIST = List.of(1L, 2L);

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Test
    @Transactional
    void testExecuteQueries() {
        // some queries require attached entities:
        var extension = new Extension();
        var namespace = new Namespace();
        namespace.setName("namespaceName");
        extension.setName("extensionName");
        extension.setNamespace(namespace);
        var userData = new UserData();
        var extVersion = new ExtensionVersion();
        extVersion.setVersion("3.1.2-rc1+armhf");
        extVersion.setTargetPlatform("targetPlatform");
        extVersion.setExtension(extension);
        var personalAccessToken = new PersonalAccessToken();
        personalAccessToken.setType(PersonalAccessTokenType.LLT);
        var keyPair = new SignatureKeyPair();
        keyPair.setPrivateKey(new byte[0]);
        keyPair.setPublicKeyText("");

        var scan = new ExtensionScan();
        scan.setNamespaceName(namespace.getName());
        scan.setExtensionName(extension.getName());
        scan.setExtensionVersion(extVersion.getVersion());
        scan.setTargetPlatform(extVersion.getTargetPlatform());
        scan.setPublisher("publisher");
        scan.setPublisherUrl("https://example.com");
        scan.setExtensionVersion(extVersion.getVersion());
        scan.setStartedAt(NOW);
        scan.setStatus(ScanStatus.STARTED);

        var validationFailure = ExtensionValidationFailure.create("NAME_SQUATTING", "validation-name", "reason");
        validationFailure.setEnforced(true);
        validationFailure.setScan(scan);

        // Admin scan decision entity for testing
        var adminDecision = AdminScanDecision.allowed(scan, userData);

        // File decision entity for testing
        var fileDecision = FileDecision.allowed("fileHash", userData);
        fileDecision.setScan(scan);
        fileDecision.setFileName("file.txt");
        fileDecision.setFileType(".txt");
        fileDecision.setNamespaceName("namespaceName");
        fileDecision.setExtensionName("extensionName");
        fileDecision.setDisplayName("Display Name");
        fileDecision.setPublisher("publisher");
        fileDecision.setVersion("1.0.0");

        // Extension threat entity for testing
        var threat = ExtensionThreat
                .create("test.js", "threatFileHash", ".js", "testScanner", "test-rule", "Test threat", "high");
        threat.setScan(scan);

        // Scan check result entity for testing
        var scanCheckResult = ScanCheckResult
                .passed("SECRET_SCANNING", ScanCheckResult.CheckCategory.PUBLISH_CHECK, NOW, 10, "All checks passed");
        scanCheckResult.setScan(scan);

        var tier = new Tier();
        tier.setName("tier");
        var customer = new Customer();
        customer.setName("customer");
        customer.setTier(tier);
        var usageStats = new UsageStats();
        usageStats.setCustomer(customer);
        usageStats.setWindowStart(NOW);
        usageStats.setDuration(Duration.ofMinutes(1));
        var dailyUsageStats = new DailyUsageStats();
        dailyUsageStats.setCustomer(customer);
        dailyUsageStats.setDate(NOW.toLocalDate());
        dailyUsageStats.setTotalRequests(1L);
        dailyUsageStats.setP95Requests(1L);

        var trustedPublisher = new TrustedPublisher();
        trustedPublisher.setExtension(extension);
        trustedPublisher.setProvider("provider");
        trustedPublisher.setRegistration(Map.of("foo", "bar"));
        trustedPublisher.setClaims(Map.of("claim", "value"));
        trustedPublisher.setCreatedBy(userData);
        trustedPublisher.setCreatedTimestamp(NOW);

        // Persist all entities consistently using EntityManager
        Stream.of(
                namespace,
                extension,
                userData,
                extVersion,
                personalAccessToken,
                keyPair,
                scan,
                validationFailure,
                adminDecision,
                fileDecision,
                threat,
                scanCheckResult,
                tier,
                customer,
                usageStats,
                trustedPublisher)
                .forEach(em::persist);
        em.flush();

        var page = PageRequest.ofSize(1);
        var queryRequest = new QueryRequest(null, null, null, null, null, null, false, null, 1, 0);

        // Record executed queries
        var methodsToBeCalled = Stream.of(repositories.getClass().getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .collect(toList());
        repositories = Mockito.spy(repositories);

        assertAll(
                () -> repositories.averageNumberOfActiveReviewsPerActiveExtension(),
                () -> repositories.countActiveExtensionPublishers(),
                () -> repositories.countActiveExtensionPublishersGroupedByExtensionsPublished(),
                () -> repositories.countActiveExtensions(),
                () -> repositories.countActiveExtensionsGroupedByExtensionReviewRating(),
                () -> repositories.countActiveReviews(null),
                () -> repositories.countReviews(userData),
                () -> repositories.countExtensions(),
                () -> repositories.isVerified(namespace),
                () -> repositories.isVerifiedPublisher(namespace, userData),
                () -> repositories.isVerifiedPublisher(extVersion),
                () -> repositories.countNamespaces(),
                () -> repositories.countPublishersThatClaimedNamespaceOwnership(),
                () -> repositories.countUsers(),
                () -> repositories.downloadsTotal(),
                () -> repositories.findPersonalAccessToken("value"),
                () -> repositories.findPersonalAccessToken(1L),
                () -> repositories.findPersonalAccessTokens(userData),
                () -> repositories.findActiveExtensions(namespace),
                () -> repositories.findActiveReviews(extension),
                () -> repositories.findActiveReviews(extension, userData),
                () -> repositories.findActiveReviews(userData),
                () -> repositories.findActiveVersions(extension),
                () -> repositories.findAdminStatisticsByYearAndMonth(1997, 1),
                () -> repositories.findAllActiveExtensions(),
                () -> repositories.findAllExtensionNames(namespace),
                () -> repositories.findAllPersistedLogs(),
                () -> repositories.findPersistedLogsAfter(NOW),
                () -> repositories.findPersistedLogsPaginated(page),
                () -> repositories.findPersistedLogsAfterPaginated(NOW, page),
                () -> repositories.countPersistedLogs(userData),
                () -> repositories.findAllReviews(extension),
                () -> repositories
                        .findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn("storageType", STRING_LIST),
                () -> repositories
                        .findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn("storageType", STRING_LIST),
                () -> repositories.findBundledExtensionsReference(extension),
                () -> repositories.findDependenciesReference(extension),
                () -> repositories.findDownloadsByStorageTypeAndName("storageType", STRING_LIST),
                () -> repositories.findExtension("name", namespace),
                () -> repositories.findExtension("name", "namespace"),
                () -> repositories.findExtensionForUpdate("name", "namespace"),
                () -> repositories.findExtensionForUpdateNoWait("name", "namespace"),
                () -> repositories.findExtensions(namespace),
                () -> repositories.findExtensionsWithInconsistentActiveFlag(),
                () -> repositories.findFileByType(extVersion, "type"),
                () -> repositories.findFiles(extVersion),
                () -> repositories.findFilesByStorageType("storageType"),
                () -> repositories.findMembership(userData, namespace),
                () -> repositories.findMemberships(namespace),
                () -> repositories.findMemberships(namespace, "role"),
                () -> repositories.findNamespace("name"),
                () -> repositories.lockNamespace(namespace),
                () -> repositories.findConflictingNamespaces("displayName", namespace),
                () -> repositories.findOrphanNamespaces(),
                () -> repositories.findPersistedLogsAfter(NOW),
                () -> repositories.findUserByLoginName("provider", "loginName"),
                () -> repositories.searchUsers("search", "role", Pageable.ofSize(25)),
                () -> repositories.findVersion("version", "targetPlatform", extension),
                () -> repositories.findVersion("version", "targetPlatform", "extensionName", "namespace"),
                () -> repositories.findVersions(extension),
                () -> repositories.getMaxExtensionDownloadCount(),
                () -> repositories.getOldestExtensionTimestamp(),
                () -> repositories.findExtensions(LONG_LIST),
                () -> repositories.findExtensions(userData),
                () -> repositories.findFilesByType(List.of(extVersion), STRING_LIST),
                () -> repositories.countVersions("namespaceName", "extensionName"),
                () -> repositories.topMostDownloadedExtensions(1),
                () -> repositories.countActivePersonalAccessTokensAndType(userData, PersonalAccessTokenType.LLT),
                () -> repositories.topMostActivePublishingUsers(1),
                () -> repositories.topNamespaceExtensions(1),
                () -> repositories.topNamespaceExtensionVersions(1),
                () -> repositories.findFileResourcesByExtensionVersionIdAndType(LONG_LIST, STRING_LIST),
                () -> repositories.findActiveExtensionVersions(LONG_LIST, "targetPlatform", 100),
                () -> repositories.findActiveExtension("name", "namespaceName"),
                () -> repositories.findActiveExtensionsById(LONG_LIST),
                () -> repositories.findActiveExtensionsByPublicId(STRING_LIST, "namespaceName"),
                () -> repositories.findNamespaceMemberships(LONG_LIST),
                () -> repositories.findAllNotMatchingByExtensionId(STRING_LIST),
                () -> repositories.getAverageReviewRating(null),
                () -> repositories.getAverageReviewRating(),
                () -> repositories.findFileResources(null),
                () -> repositories.findKeyPair(null),
                () -> repositories.findActiveKeyPair(),
                () -> repositories.findFilesByType(null),
                () -> repositories.findVersions(),
                () -> repositories.findVersionsWithout(keyPair),
                () -> repositories.deleteDownloadSigFiles(),
                () -> repositories.deleteAllKeyPairs(),
                () -> repositories.findActiveVersionsSorted("namespaceName", "extensionName", page),
                () -> repositories.findActiveVersionsSorted("namespaceName", "extensionName", "targetPlatform", page),
                () -> repositories
                        .findActiveVersionStringsSorted("namespaceName", "extensionName", "targetPlatform", page),
                () -> repositories.findVersionStringsSorted(extension, "targetPlatform", true),
                () -> repositories.findVersionStringsSorted(extension, "targetPlatform", true),
                () -> repositories.findActiveVersions(queryRequest),
                () -> repositories.findChanges(null, null, null, 100),
                () -> repositories.findChanges(NOW.minus(Duration.ofDays(1)), NOW, null, 100),
                // exercises the (changed_at, id) row comparison the cursor resumes with
                () -> repositories.findChanges(null, null, new ChangesCursor(NOW, 1L), 100),
                () -> repositories.findChanges(null, NOW, new ChangesCursor(NOW.minus(Duration.ofDays(1)), 1L), 100),
                () -> repositories.findLatestExtensionVersionChange(extVersion),
                () -> repositories.recordExtensionVersionChange(
                        extVersion,
                        ExtensionVersionState.ACTIVE,
                        NOW),
                () -> repositories.recordPurgedExtensionVersionChange(
                        extVersion,
                        ExtensionVersionState.REMOVED,
                        NOW),
                () -> repositories.detachExtensionVersionChanges(extVersion),
                () -> repositories.findActiveVersionStringsSorted(LONG_LIST, "targetPlatform"),
                () -> repositories.findActiveVersionReferencesSorted(List.of(1L)),
                () -> repositories.findAllPublicIds(),
                () -> repositories.findPublicId("namespaceName", "extensionName"),
                () -> repositories.findPublicId("namespaceName.extensionName"),
                () -> repositories.findNamespacePublicId("namespaceName.extensionName"),
                () -> repositories.updateExtensionPublicIds(Map.of()),
                () -> repositories.updateNamespacePublicIds(Map.of()),
                () -> repositories.extensionPublicIdExists("namespaceName.extensionName"),
                () -> repositories.namespacePublicIdExists("namespaceName.extensionName"),
                () -> repositories.fetchSitemapRows(),
                () -> repositories.findTargetPlatformsGroupedByVersion(extension),
                () -> repositories.findVersionsForUrls(extension, "targetPlatform", "version"),
                () -> repositories.findExtensionVersion("namespaceName", "extensionName", "targetPlatform", "version"),
                () -> repositories.findLatestVersionForAllUrls(extension, "targetPlatform", false, false),
                () -> repositories.findLatestVersion(extension, "targetPlatform", false, false),
                () -> repositories.findLatestVersions(namespace),
                () -> repositories.findLatestVersions(userData),
                () -> repositories.findLatestVersionByTargetPlatform(extension, true, true),
                () -> repositories.findExtensionTargetPlatforms(extension),
                () -> repositories.isNamespaceOwner(userData, namespace),
                () -> repositories.findMembershipsForOwner(userData, "namespaceName"),
                () -> repositories.findNamespaceName("namespaceName"),
                () -> repositories.findMemberships("namespaceName"),
                () -> repositories.findActiveExtensionNames(namespace),
                () -> repositories.namespaceExists("namespaceName"),
                () -> repositories
                        .findFileByType("namespaceName", "extensionName", "targetPlatform", "version", "type"),
                () -> repositories
                        .findFileByName("namespaceName", "extensionName", "targetPlatform", "version", "name"),
                () -> repositories.findVersionsByUser(userData, false),
                () -> repositories.findPublishersWithActiveVersions(),
                () -> repositories.countVersionsRemovedBy(userData),
                () -> repositories.deleteFiles(extVersion),
                () -> repositories.findExtensionTargetPlatforms(extension),
                () -> repositories.deactivateKeyPairs(),
                () -> repositories.findActivePersonalAccessTokensAndType(userData, PersonalAccessTokenType.LLT),
                () -> repositories.findAllPersonalAccessTokensByVersion(0),
                () -> repositories.findLatestVersions(List.of(1L)),
                () -> repositories.findLatestVersions(List.of(1L), "targetPlatform"),
                () -> repositories.hasSameVersion(extVersion),
                () -> repositories.hasActiveReview(extension, userData),
                () -> repositories.findLatestVersionsIsPreview(List.of(1L)),
                () -> repositories.findPersonalAccessToken(userData, "description"),
                () -> repositories.findMemberships(userData),
                () -> repositories.canPublishInNamespace(userData, namespace),
                () -> repositories.findLatestVersion("namespaceName", "extensionName", "targetPlatform", false, false),
                () -> repositories.hasMembership(userData, namespace),
                () -> repositories
                        .findFirstUnresolvedDependency(List.of(new ExtensionId("namespaceName", "extensionName"))),
                () -> repositories
                        .findSignatureKeyPairPublicId("namespaceName", "extensionName", "targetPlatform", "version"),
                () -> repositories.findFirstMembership("namespaceName"),
                () -> repositories.findExtensionsForUrls(namespace),
                () -> repositories.deactivateKeyPairs(),
                () -> repositories.hasExtension("namespaceName", "extensionName"),
                () -> repositories.findDeprecatedExtensions(extension),
                () -> repositories.findLatestReplacement(1L, null, false, false),
                () -> repositories.findNotMigratedItems(page),
                () -> repositories.countNotMigratedItems(),
                () -> repositories.findTargetPlatformsGroupedByVersion(extension, userData),
                () -> repositories.findVersionPublishedByUser(
                        userData,
                        "version",
                        "targetPlatform",
                        "extensionName",
                        "namespace"),
                () -> repositories.findLatestVersion(userData, "namespaceName", "extensionName"),
                () -> repositories.isDeleteAllActiveVersions("namespaceName", "extensionName"),
                () -> repositories.deactivatePersonalAccessTokens(userData),
                () -> repositories.expirePersonalAccessTokens(NOW),
                () -> repositories
                        .deleteExpiredPersonalAccessTokens(NOW, List.of(PersonalAccessTokenType.TPT)),
                () -> repositories.findExpiringPersonalAccessTokensWithoutNotification(NOW, page),
                () -> repositories.updateExpiresTimeForLegacyPersonalAccessTokens(NOW, PersonalAccessTokenType.LLT),
                () -> repositories.findSimilarExtensionsByLevenshtein(
                        "extensionName",
                        "namespaceName",
                        "displayName",
                        List.of(),
                        0.5,
                        false,
                        10),
                () -> repositories.findSimilarNamespacesByLevenshtein("namespaceName", List.of(), 0.5, false, 10),
                () -> repositories.findLatestExtensionScan(extVersion),
                () -> repositories.hasThreatOfType(extVersion, NamespaceOwnershipCheckScanner.TYPE),
                () -> repositories.findExtensionScansByStatus(ScanStatus.STARTED),
                () -> repositories.countExtensionScansByStatus(ScanStatus.STARTED),
                () -> repositories.findExtensionScan(1L),
                () -> repositories.findValidationFailures(scan),
                () -> repositories.findDistinctValidationFailureCheckTypes(),
                () -> repositories.saveExtensionScan(scan),
                () -> repositories.saveValidationFailure(validationFailure),
                // DB paging and filtering methods for scan API
                () -> repositories.findScansFullyFiltered(
                        List.of(ScanStatus.STARTED),
                        "namespaceName",
                        "publisher",
                        "extensionName",
                        NOW,
                        NOW,
                        List.of("checkType"),
                        List.of("scanner"),
                        true,
                        null,
                        false,
                        page),
                // Statistics queries with full filter support
                () -> repositories.countScansForStatistics(
                        ScanStatus.STARTED,
                        NOW,
                        NOW,
                        List.of("checkType"),
                        List.of("scanner"),
                        true),
                () -> repositories.countAdminDecisionsForStatistics(
                        "ALLOWED",
                        NOW,
                        NOW,
                        List.of("checkType"),
                        List.of("scanner"),
                        true),
                // Admin scan decision methods
                () -> repositories.findAdminScanDecision(scan),
                () -> repositories.saveAdminScanDecision(adminDecision),
                () -> repositories.countAdminScanDecisions("ALLOWED"),
                () -> repositories.countAdminScanDecisions(userData),
                // Note: We pass valid LocalDateTime values to avoid PostgreSQL null parameter type issues
                () -> repositories.findAdminScanDecisionByScanId(1L),
                // File decision methods
                () -> repositories.findFileDecision(1L),
                () -> repositories.saveFileDecision(fileDecision),
                () -> repositories.deleteFileDecision(1L),
                () -> repositories.findFileDecisionByHash("fileHash"),
                () -> repositories.findFileDecisionsFiltered(
                        "ALLOWED",
                        "publisher",
                        "namespace",
                        "name",
                        NOW.minusYears(1),
                        NOW.plusYears(1),
                        page),
                // Note: We pass valid LocalDateTime values to avoid PostgreSQL null parameter type issues
                () -> repositories.countFileDecisionsByDateRange("ALLOWED", NOW.minusYears(1), NOW.plusYears(1)),
                // Extension threat methods
                () -> repositories.saveExtensionThreat(threat),
                () -> repositories.findExtensionThreats(scan),
                () -> repositories.findDistinctThreatScannerTypes(),
                () -> repositories.findExtensionThreats(scan, "testType"),
                // Additional admin scan decision methods
                // Additional file decision methods
                () -> repositories.countFileDecisions("ALLOWED"),
                () -> repositories.countFileDecisions(userData),
                // Scan check result methods
                () -> repositories.saveScanCheckResult(scanCheckResult),
                () -> repositories.hasScanCheckResult(1L, "SECRET_SCANNING"),
                () -> repositories.findScanCheckResultsByScanId(1L),
                // Extension version lookup including inactive
                () -> repositories.findExtensionVersionIncludingInactive(
                        namespace.getName(),
                        extension.getName(),
                        extVersion.getTargetPlatform(),
                        extVersion.getVersion()),
                // Rate limit tests
                () -> repositories.upsertTier(tier),
                () -> repositories.findTier("tier"),
                () -> repositories.findTiersByTierType(TierType.FREE),
                () -> repositories.findTiersByTierTypeExcludingTier(TierType.FREE, tier),
                () -> repositories.findAllTiers(),
                () -> repositories.upsertCustomer(customer),
                () -> repositories.findCustomer("customer"),
                () -> repositories.findCustomerById(1L),
                () -> repositories.findCustomersByTier(tier),
                () -> repositories.countCustomersByTier(tier),
                () -> repositories.findAllCustomers(),
                () -> repositories.findCustomerMemberships(customer),
                () -> repositories.findCustomerMemberships(userData),
                () -> repositories.findCustomerMembership(userData, customer),
                () -> repositories.findActiveRateLimitTokens(customer),
                () -> repositories.findRateLimitToken(1L),
                () -> repositories.findRateLimitToken("value"),
                () -> repositories.hasRateLimitToken("value"),
                () -> repositories.saveUsageStats(usageStats),
                () -> repositories.findUsageStatsByCustomerAndDate(customer, NOW),
                () -> repositories.findDailyUsageStats(customer, NOW.toLocalDate()),
                () -> repositories.findUnprocessedDaysForDailyUsage(customer),
                () -> repositories.saveDailyUsageStats(dailyUsageStats),
                () -> repositories.findTrustedPublishersByExtension(extension),
                () -> repositories.findTrustedPublishersByNamespaceAndCreatedBy(namespace, userData),
                () -> repositories.findTrustedPublisher(1L),
                () -> repositories.deleteTrustedPublisher(trustedPublisher),
                () -> repositories.deleteTier(tier),
                () -> repositories.deleteCustomer(customer),
                // Extension scan delete method - add last, still not clear why but otherwise the test fails
                () -> repositories.deleteExtensionScan(scan));

        // check that we did not miss anything
        // (remember to add new queries also to this test)
        var invocations = Mockito.mockingDetails(repositories).getInvocations().stream()
                .map(Invocation::getMethod)
                .collect(toList());
        assertThat(invocations).containsAll(methodsToBeCalled);
    }
}
