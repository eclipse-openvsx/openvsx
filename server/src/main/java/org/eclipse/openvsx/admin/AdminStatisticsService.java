/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.admin;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.repositories.RepositoryService;

@Component
public class AdminStatisticsService {

    /** How many entries the "top ..." breakdowns carry. */
    private static final int TOP_LIMIT = 10;

    private final EntityManager entityManager;
    private final RepositoryService repositories;

    public AdminStatisticsService(EntityManager entityManager, RepositoryService repositories) {
        this.entityManager = entityManager;
        this.repositories = repositories;
    }

    /**
     * Computes the statistics for the given month from the registry's current state, without saving
     * them.
     * <p>
     * Every figure except {@code downloads} is a point-in-time snapshot rather than an aggregate
     * over the month: the archival job runs on the first of the following month, so a stored row is
     * the state shortly after that month ended. {@code downloads} is the one exception, derived as
     * the growth in {@code downloadsTotal} since the previous month's row - which means that
     * without a previous row (the first month a registry archives, and for the on-the-fly current
     * month on a registry that has none yet) it reports every download ever rather than the
     * month's. That is pre-existing behaviour of the archival job, kept here so the on-the-fly and
     * archived paths cannot disagree.
     */
    public AdminStatistics computeAdminStatistics(int year, int month) {
        var extensions = repositories.countActiveExtensions();
        var downloadsTotal = repositories.downloadsTotal();

        var lastDate = LocalDateTime.of(year, month, 1, 0, 0).minusMonths(1);
        var lastAdminStatistics = repositories
                .findAdminStatisticsByYearAndMonth(lastDate.getYear(), lastDate.getMonthValue());
        var lastDownloadsTotal = lastAdminStatistics != null ? lastAdminStatistics.getDownloadsTotal() : 0;

        var statistics = new AdminStatistics();
        statistics.setYear(year);
        statistics.setMonth(month);
        statistics.setExtensions(extensions);
        statistics.setDownloads(downloadsTotal - lastDownloadsTotal);
        statistics.setDownloadsTotal(downloadsTotal);
        statistics.setPublishers(repositories.countActiveExtensionPublishers());
        statistics.setAverageReviewsPerExtension(repositories.averageNumberOfActiveReviewsPerActiveExtension());
        statistics.setNamespaceOwners(repositories.countPublishersThatClaimedNamespaceOwnership());
        statistics.setExtensionsByRating(repositories.countActiveExtensionsGroupedByExtensionReviewRating());
        statistics.setPublishersByExtensionsPublished(
                repositories.countActiveExtensionPublishersGroupedByExtensionsPublished());
        statistics.setTopMostActivePublishingUsers(repositories.topMostActivePublishingUsers(TOP_LIMIT));
        statistics.setTopNamespaceExtensions(repositories.topNamespaceExtensions(TOP_LIMIT));
        statistics.setTopNamespaceExtensionVersions(repositories.topNamespaceExtensionVersions(TOP_LIMIT));
        statistics.setTopMostDownloadedExtensions(repositories.topMostDownloadedExtensions(TOP_LIMIT));
        return statistics;
    }

    @Transactional
    public void saveAdminStatistics(AdminStatistics statistics) {
        entityManager.persist(statistics);
    }
}
