/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.admin.ScanAPI.AdminDecisionFilterValues;
import org.eclipse.openvsx.entities.AdminScanDecision;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionThreat;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UserData;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExtensionScanFilterTest extends AbstractPostgresContainerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Test
    @Transactional
    void needsReviewFilterMatchesUndecidedErroredScans() {
        var admin = new UserData();
        admin.setLoginName("scan-filter-admin");
        em.persist(admin);

        var undecided = persistScan("undecided", ScanStatus.ERRORED);
        var allowed = persistScan("allowed", ScanStatus.ERRORED);
        em.persist(AdminScanDecision.allowed(allowed, admin));
        em.flush();

        var needsReview = new AdminDecisionFilterValues(false, false, true);
        var page = repositories.findScansFullyFiltered(
                List.of(ScanStatus.ERRORED),
                null,
                "scan-filter-publisher",
                null,
                null,
                null,
                null,
                null,
                null,
                needsReview,
                false,
                false,
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ExtensionScan::getId).containsExactly(undecided.getId());
    }

    @Test
    @Transactional
    void errorStatusOnlyIncludesScansWithErroredChecksUnlessCheckErrorsAreExcluded() {
        var errored = persistScan("errored", ScanStatus.ERRORED);
        var passedWithCheckError = persistScan("passed", ScanStatus.PASSED);
        var check = ScanCheckResult.error("scanner", ScanCheckResult.CheckCategory.SCANNER_JOB, NOW, "boom");
        check.setScan(passedWithCheckError);
        em.persist(check);
        em.flush();

        var withCheckErrors = findErrored(true);
        var exact = findErrored(false);

        assertThat(withCheckErrors).containsExactlyInAnyOrder(errored.getId(), passedWithCheckError.getId());
        assertThat(exact).containsExactly(errored.getId());
    }

    @Test
    @Transactional
    void checkQuarantinesAreAddedToTheThreatScannerFilterOnlyWhenRequested() {
        var admin = new UserData();
        admin.setLoginName("scan-filter-admin");
        em.persist(admin);

        var byThreat = persistScan("by-threat", ScanStatus.QUARANTINED);
        var threat = ExtensionThreat.create("a.js", "hash", "js", "ClamAV", "rule", "reason", null);
        threat.setScan(byThreat);
        em.persist(threat);
        var byCheck = persistScan("by-check", ScanStatus.QUARANTINED);
        persistFailure(byCheck, true);
        em.persist(AdminScanDecision.allowed(byCheck, admin));
        persistFailure(persistScan("unenforced-check", ScanStatus.QUARANTINED), false);
        persistFailure(persistScan("rejected", ScanStatus.REJECTED), true);
        em.flush();

        assertThat(findQuarantined(false)).containsExactly(byThreat.getId());
        assertThat(findQuarantined(true)).containsExactlyInAnyOrder(byThreat.getId(), byCheck.getId());
        assertThat(
                repositories
                        .countScansForStatistics(ScanStatus.QUARANTINED, NOW, NOW, null, List.of("ClamAV"), true, null))
                .isEqualTo(2);
        assertThat(
                repositories
                        .countScansForStatistics(ScanStatus.REJECTED, NOW, NOW, null, List.of("ClamAV"), true, null))
                .isZero();
        assertThat(
                repositories.countAdminDecisionsForStatistics(
                        AdminScanDecision.ALLOWED,
                        NOW,
                        NOW,
                        null,
                        List.of("ClamAV"),
                        false,
                        null))
                .isZero();
        assertThat(
                repositories.countAdminDecisionsForStatistics(
                        AdminScanDecision.ALLOWED,
                        NOW,
                        NOW,
                        null,
                        List.of("ClamAV"),
                        true,
                        null))
                .isEqualTo(1);
    }

    private List<Long> findQuarantined(boolean includeCheckQuarantines) {
        return repositories.findScansFullyFiltered(
                null,
                null,
                "scan-filter-publisher",
                null,
                null,
                null,
                null,
                List.of("ClamAV"),
                null,
                null,
                false,
                includeCheckQuarantines,
                PageRequest.of(0, 10))
                .map(ExtensionScan::getId)
                .getContent();
    }

    private void persistFailure(ExtensionScan scan, boolean enforced) {
        var failure = ExtensionValidationFailure.create("DISPLAY_NAME_CONFLICT", "rule", "reason");
        failure.setEnforced(enforced);
        failure.setScan(scan);
        em.persist(failure);
    }

    private List<Long> findErrored(boolean includeCheckErrors) {
        return repositories.findScansFullyFiltered(
                List.of(ScanStatus.ERRORED),
                null,
                "scan-filter-publisher",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                includeCheckErrors,
                false,
                PageRequest.of(0, 10))
                .map(ExtensionScan::getId)
                .getContent();
    }

    private ExtensionScan persistScan(String name, ScanStatus status) {
        var scan = new ExtensionScan();
        scan.setNamespaceName("scan-filter-ns");
        scan.setExtensionName(name);
        scan.setExtensionVersion("1.0.0");
        scan.setTargetPlatform("universal");
        scan.setUniversalTargetPlatform(true);
        scan.setPublisher("scan-filter-publisher");
        scan.setStartedAt(NOW);
        scan.setStatus(status);
        em.persist(scan);
        return scan;
    }
}
