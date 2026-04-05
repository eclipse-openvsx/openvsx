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

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.util.ExtensionId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Run the DB queries and assert no DB error, just to ensure that the queries
 * are consistent with the schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RepositoryServiceSmokeTest {

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
        var threat = ExtensionThreat.create("test.js", "threatFileHash", ".js", "testScanner", "test-rule", "Test threat", "high");
        threat.setScan(scan);

        // Scan check result entity for testing
        var scanCheckResult = ScanCheckResult.passed("SECRET_SCANNING", ScanCheckResult.CheckCategory.PUBLISH_CHECK, NOW, 10, "All checks passed");
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

        // Persist all entities consistently using EntityManager
        Stream.of(namespace, extension, userData, extVersion, personalAccessToken, keyPair,
                  scan, validationFailure, adminDecision, fileDecision, threat, scanCheckResult,
                  tier, customer, usageStats)
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
                () -> repositories.countExtensions(),
                () -> repositories.hasMemberships(namespace, "role"),
                () -> repositories.isVerified(namespace, userData),
                () -> repositories.countNamespaces(),
                () -> repositories.countPublishersThatClaimedNamespaceOwnership(),
                () -> repositories.countUsers(),
                () -> repositories.downloadsTotal(),
                () -> repositories.findAccessToken("value"),
                () -> repositories.findAccessToken(1L),
                () -> repositories.findAccessTokens(userData),
                () -> repositories.findActiveExtensions(namespace),
                () -> repositories.findActiveReviews(extension),
                () -> repositories.findActiveReviews(extension, userData),
                () -> repositories.findActiveReviews(userData),
                () -> repositories.findActiveVersions(extension),
                () -> repositories.findAdminStatisticsByYearAndMonth(1997, 1),
                () -> repositories.findAllActiveExtensions(),
                () -> repositories.findAllPersistedLogs(),
                () -> repositories.findPersistedLogsAfter(NOW),
                () -> repositories.findPersistedLogsPaginated(page),
                () -> repositories.findPersistedLogsAfterPaginated(NOW, page),
                () -> repositories.findAllReviews(extension),
                () -> repositories.findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn("storageType", STRING_LIST),
                () -> repositories.findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn("storageType", STRING_LIST),
                () -> repositories.findBundledExtensionsReference(extension),
                () -> repositories.findDependenciesReference(extension),
                () -> repositories.findDownloadsByStorageTypeAndName("storageType", STRING_LIST),
                () -> repositories.findExtension("name", namespace),
                () -> repositories.findExtension("name", "namespace"),
                () -> repositories.findExtensions(namespace),
                () -> repositories.findFileByType(extVersion, "type"),
                () -> repositories.findFiles(extVersion),
                () -> repositories.findFilesByStorageType("storageType"),
                () -> repositories.findMembership(userData, namespace),
                () -> repositories.findMemberships(namespace),
                () -> repositories.findMemberships(namespace, "role"),
                () -> repositories.findNamespace("name"),
                () -> repositories.findOrphanNamespaces(),
                () -> repositories.findPersistedLogsAfter(NOW),
                () -> repositories.findTargetPlatformVersions("version", "extensionName", "namespaceName"),
                () -> repositories.findUserByLoginName("provider", "loginName"),
                () -> repositories.findUsersByLoginNameStartingWith("loginNameStart", 1),
                () -> repositories.findVersion("version", "targetPlatform", extension),
                () -> repositories.findVersion("version", "targetPlatform", "extensionName", "namespace"),
                () -> repositories.findVersions(extension),
                () -> repositories.findVersionsByAccessToken(personalAccessToken, true),
                () -> repositories.getMaxExtensionDownloadCount(),
                () -> repositories.getOldestExtensionTimestamp(),
                () -> repositories.findExtensions(LONG_LIST),
                () -> repositories.findExtensions(userData),
                () -> repositories.findFilesByType(List.of(extVersion), STRING_LIST),
                () -> repositories.countVersions("namespaceName", "extensionName"),
                () -> repositories.topMostDownloadedExtensions(1),
                () -> repositories.countActiveAccessTokens(userData),
                () -> repositories.topMostActivePublishingUsers(1),
                () -> repositories.topNamespaceExtensions(1),
                () -> repositories.topNamespaceExtensionVersions(1),
                () -> repositories.findFileResourcesByExtensionVersionIdAndType(LONG_LIST, STRING_LIST),
                () -> repositories.findActiveExtensionVersions(LONG_LIST, "targetPlatform"),
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
                () -> repositories.findActiveVersionStringsSorted("namespaceName", "extensionName", "targetPlatform", page),
                () -> repositories.findVersionStringsSorted(extension, "targetPlatform", true),
                () -> repositories.findVersionStringsSorted(extension, "targetPlatform", true),
                () -> repositories.findActiveVersions(queryRequest),
                () -> repositories.findActiveVersionStringsSorted(LONG_LIST,"targetPlatform"),
                () -> repositories.findActiveVersionReferencesSorted(List.of(1L)),
                () -> repositories.findAllPublicIds(),
                () -> repositories.findPublicId("namespaceName", "extensionName"),
                () -> repositories.findPublicId("namespaceName.extensionName"),
                () -> repositories.findNamespacePublicId("namespaceName.extensionName"),
                () -> repositories.updateExtensionPublicIds(Collections.emptyMap()),
                () -> repositories.updateNamespacePublicIds(Collections.emptyMap()),
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
                () -> repositories.findExtensionTargetPlatforms(extension),
                () -> repositories.isNamespaceOwner(userData, namespace),
                () -> repositories.findMembershipsForOwner(userData,"namespaceName"),
                () -> repositories.findNamespaceName("namespaceName"),
                () -> repositories.findMemberships("namespaceName"),
                () -> repositories.findActiveExtensionNames(namespace),
                () -> repositories.namespaceExists("namespaceName"),
                () -> repositories.findFileByType("namespaceName", "extensionName", "targetPlatform", "version", "type"),
                () -> repositories.findFileByName("namespaceName", "extensionName", "targetPlatform", "version", "name"),
                () -> repositories.findVersionsByUser(userData, false),
                () -> repositories.deleteFiles(extVersion),
                () -> repositories.findExtensionTargetPlatforms(extension),
                () -> repositories.deactivateKeyPairs(),
                () -> repositories.findActiveAccessTokens(userData),
                () -> repositories.findLatestVersions(List.of(1L)),
                () -> repositories.hasSameVersion(extVersion),
                () -> repositories.hasActiveReview(extension, userData),
                () -> repositories.findLatestVersionsIsPreview(List.of(1L)),
                () -> repositories.findAccessToken(userData, "description"),
                () -> repositories.findMemberships(userData),
                () -> repositories.canPublishInNamespace(userData, namespace),
                () -> repositories.findLatestVersion("namespaceName", "extensionName", "targetPlatform", false, false),
                () -> repositories.hasMembership(userData, namespace),
                () -> repositories.findFirstUnresolvedDependency(List.of(new ExtensionId("namespaceName", "extensionName"))),
                () -> repositories.findAllAccessTokens(),
                () -> repositories.hasAccessToken("tokenValue"),
                () -> repositories.findSignatureKeyPairPublicId("namespaceName", "extensionName", "targetPlatform", "version"),
                () -> repositories.findFirstMembership("namespaceName"),
                () -> repositories.findActiveExtensionsForUrls(namespace),
                () -> repositories.deactivateKeyPairs(),
                () -> repositories.hasExtension("namespaceName", "extensionName"),
                () -> repositories.findDeprecatedExtensions(extension),
                () -> repositories.findLatestReplacement(1L, null, false, false),
                () -> repositories.findNotMigratedItems(page),
                () -> repositories.findRemoveFileResourceTypeResourceMigrationItems(0, 1),
                () -> repositories.findTargetPlatformsGroupedByVersion(extension, userData),
                () -> repositories.findVersion(userData,"version", "targetPlatform", "extensionName", "namespace"),
                () -> repositories.findLatestVersion(userData, "namespaceName", "extensionName"),
                () -> repositories.isDeleteAllVersions("namespaceName", "extensionName", Collections.emptyList(), userData),
                () -> repositories.deactivateAccessTokens(userData),
                () -> repositories.expireAccessTokens(NOW),
                () -> repositories.findExpiringAccessTokensWithoutNotification(NOW, page),
                () -> repositories.updateExpiresTimeForLegacyAccessTokens(NOW),
                () -> repositories.findSimilarExtensionsByLevenshtein("extensionName", "namespaceName", "displayName", Collections.emptyList(), 0.5, false, 10),
                () -> repositories.findSimilarNamespacesByLevenshtein("namespaceName", Collections.emptyList(), 0.5, false, 10),
                () -> repositories.findExtensionScans(extVersion),
                () -> repositories.findLatestExtensionScan(extVersion),
                () -> repositories.findExtensionScans(extension),
                () -> repositories.findExtensionScansByNamespace(namespace.getName()),
                () -> repositories.findExtensionScansByStatus(ScanStatus.STARTED),
                () -> repositories.findInProgressExtensionScans(),
                () -> repositories.countExtensionScansByStatus(ScanStatus.STARTED),
                () -> repositories.findExtensionScan(1L),
                () -> repositories.hasExtensionScanWithStatus(extVersion, ScanStatus.STARTED),
                () -> repositories.findAllExtensionScans(),
                () -> repositories.findValidationFailures(scan),
                () -> repositories.findValidationFailuresByType(validationFailure.getCheckType()),
                () -> repositories.findValidationFailures(scan, validationFailure.getCheckType()),
                () -> repositories.countValidationFailures(scan),
                () -> repositories.countValidationFailuresByType(validationFailure.getCheckType()),
                () -> repositories.hasValidationFailures(scan),
                () -> repositories.hasValidationFailuresOfType(scan, validationFailure.getCheckType()),
                () -> repositories.findValidationFailure(validationFailure.getId()),
                () -> repositories.findDistinctValidationFailureRuleNames(),
                () -> repositories.findDistinctValidationFailureCheckTypes(),
                () -> repositories.saveExtensionScan(scan),
                () -> repositories.saveValidationFailure(validationFailure),
                // DB paging and filtering methods for scan API
                () -> repositories.countExtensionScansByStatusAndDateRange(ScanStatus.STARTED, NOW, NOW),
                () -> repositories.countExtensionScansByStatusDateRangeAndEnforcement(ScanStatus.STARTED, NOW, NOW, true),
                () -> repositories.findScansFiltered(List.of(ScanStatus.STARTED), "namespaceName", "publisher", "extensionName", NOW, NOW, page),
                () -> repositories.countScansFiltered(List.of(ScanStatus.STARTED), "namespaceName", "publisher", "extensionName", NOW, NOW),
                () -> repositories.findScansFullyFiltered(List.of(ScanStatus.STARTED), "namespaceName", "publisher", "extensionName", NOW, NOW, List.of("checkType"), List.of("scanner"), true, null, false, page),
                () -> repositories.countScansFullyFiltered(List.of(ScanStatus.STARTED), "namespaceName", "publisher", "extensionName", NOW, NOW, List.of("checkType"), List.of("scanner"), true, null, false),
                // Statistics queries with full filter support
                () -> repositories.countScansForStatistics(ScanStatus.STARTED, NOW, NOW, List.of("checkType"), List.of("scanner"), true),
                () -> repositories.countAdminDecisionsForStatistics("ALLOWED", NOW, NOW, List.of("checkType"), List.of("scanner"), true),
                // Admin scan decision methods
                () -> repositories.findAdminScanDecision(scan),
                () -> repositories.findAdminScanDecision(1L),
                () -> repositories.saveAdminScanDecision(adminDecision),
                () -> repositories.deleteAdminScanDecision(1L),
                () -> repositories.hasAdminScanDecisionByScanId(1L),
                () -> repositories.countAdminScanDecisions("ALLOWED"),
                // Note: We pass valid LocalDateTime values to avoid PostgreSQL null parameter type issues
                () -> repositories.countAdminScanDecisionsByDateRange("ALLOWED", NOW.minusYears(1), NOW.plusYears(1)),
                () -> repositories.countAdminScanDecisionsByEnforcement("ALLOWED", true),
                () -> repositories.findAdminScanDecisionByScanId(1L),
                // File decision methods
                () -> repositories.hasFileDecision("fileHash"),
                () -> repositories.findFileDecision(1L),
                () -> repositories.saveFileDecision(fileDecision),
                () -> repositories.deleteFileDecision(1L),
                () -> repositories.deleteFileDecisionByHash("fileHash"),
                () -> repositories.findFileDecisionsByIds(LONG_LIST),
                () -> repositories.findFileDecisionByHash("fileHash"),
                () -> repositories.findFileDecisionsFiltered("ALLOWED", "publisher", "namespace", "name", NOW.minusYears(1), NOW.plusYears(1), page),
                () -> repositories.countAllFileDecisions(),
                // Note: We pass valid LocalDateTime values to avoid PostgreSQL null parameter type issues
                () -> repositories.countFileDecisionsByDateRange("ALLOWED", NOW.minusYears(1), NOW.plusYears(1)),
                // Extension threat methods
                () -> repositories.saveExtensionThreat(threat),
                () -> repositories.findExtensionThreat(1L),
                () -> repositories.findExtensionThreats(scan),
                () -> repositories.hasExtensionThreats(scan),
                () -> repositories.countExtensionThreats(scan),
                () -> repositories.findDistinctThreatScannerTypes(),
                () -> repositories.findExtensionThreatsByScanId(1L),
                () -> repositories.findExtensionThreatsByFileHash("fileHash"),
                () -> repositories.findDistinctThreatRuleNames(),
                () -> repositories.findExtensionThreatsByType("testType"),
                () -> repositories.findExtensionThreats(scan, "testType"),
                () -> repositories.findExtensionThreatsAfter(NOW.minusYears(1)),
                () -> repositories.findExtensionThreatsOrdered(scan),
                () -> repositories.countExtensionThreatsByType("testType"),
                () -> repositories.hasExtensionThreatsOfType(scan, "testType"),
                // Additional admin scan decision methods
                () -> repositories.hasAdminScanDecision(scan),
                // Additional file decision methods
                () -> repositories.countFileDecisions("ALLOWED"),
                // Scan check result methods
                () -> repositories.saveScanCheckResult(scanCheckResult),
                () -> repositories.hasScanCheckResult(1L, "SECRET_SCANNING"),
                () -> repositories.findScanCheckResults(scan),
                () -> repositories.findScanCheckResultsByScanId(1L),
                // Extension version lookup including inactive
                () -> repositories.findExtensionVersionIncludingInactive(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion()),
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
                () -> repositories.saveUsageStats(usageStats),
                () -> repositories.findUsageStatsByCustomerAndDate(customer, NOW),
                () -> repositories.deleteTier(tier),
                () -> repositories.deleteCustomer(customer),
                // Extension scan delete method - add last, still not clear why but otherwise the test fails
                () -> repositories.deleteExtensionScan(scan)
            );

        // check that we did not miss anything
        // (remember to add new queries also to this test)
        var invocations = Mockito.mockingDetails(repositories).getInvocations().stream()
                .map(Invocation::getMethod)
                .collect(toList());
        assertThat(invocations).containsAll(methodsToBeCalled);
    }
}
