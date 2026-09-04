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

import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The statistics computation, which used to live inline in {@link AdminStatisticsJobRequestHandler}
 * and moved here so the archival job and the on-the-fly current month (see
 * {@code AdminService#getAdminStatistics}) cannot compute them differently.
 */
@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceTest {

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    AdminStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsService(entityManager, repositories);
    }

    @Test
    void computesEveryFigureFromTheCurrentState() {
        var expectedStatistics = mockAdminStatistics();

        var statistics = service.computeAdminStatistics(2023, 11);

        assertThat(statistics).isEqualTo(expectedStatistics);
    }

    // downloads is the one figure that isn't a snapshot: it's the growth in downloadsTotal since
    // the previous month's row.
    @Test
    void derivesTheMonthsDownloadsFromThePreviousMonth() {
        var expectedStatistics = mockAdminStatistics();
        expectedStatistics.setDownloads(678L);

        var prevStatistics = new AdminStatistics();
        prevStatistics.setDownloadsTotal(5000);
        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(2023, 10)).thenReturn(prevStatistics);

        var statistics = service.computeAdminStatistics(2023, 11);

        assertThat(statistics.getDownloads()).isEqualTo(678L);
        assertThat(statistics).isEqualTo(expectedStatistics);
    }

    // Without a previous row there is nothing to subtract, so the month reports every download the
    // registry has ever served. Pre-existing behaviour of the archival job, pinned here because the
    // on-the-fly path now hits it too on a registry that has never archived a month.
    @Test
    void reportsEveryDownloadWhenNoPreviousMonthWasArchived() {
        mockAdminStatistics();
        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(2023, 10)).thenReturn(null);

        var statistics = service.computeAdminStatistics(2023, 11);

        assertThat(statistics.getDownloads()).isEqualTo(statistics.getDownloadsTotal());
    }

    // The month asked for is what the row is labelled with, not the month the computation runs in.
    @Test
    void labelsTheStatisticsWithTheRequestedMonth() {
        mockAdminStatistics();

        var statistics = service.computeAdminStatistics(2023, 11);

        assertThat(statistics.getYear()).isEqualTo(2023);
        assertThat(statistics.getMonth()).isEqualTo(11);
    }

    private AdminStatistics mockAdminStatistics() {
        var year = 2023;
        var month = 11;
        var extensions = 1234L;
        var downloadsTotal = 5678L;
        var publishers = 579L;
        var averageReviewsPerExtension = 2.5;
        var namespaceOwners = 268L;
        var extensionsByRating = Map.of(
                1,
                34,
                2,
                100,
                3,
                700,
                4,
                150,
                5,
                250);
        var publishersByExtensionsPublished = Map.of(
                1,
                500,
                3,
                70,
                10,
                9);
        var topMostActivePublishingUsers = Map.of(
                "foo",
                400,
                "bar",
                150,
                "baz",
                29);
        var topNamespaceExtensions = Map.of(
                "lorum",
                800,
                "ipsum",
                400,
                "dolar",
                34);
        var topNamespaceExtensionVersions = Map.of(
                "lorum",
                8000,
                "ipsum",
                2000,
                "dolar",
                68);
        var topMostDownloadedExtensions = Map.of(
                "lorum.alpha",
                1200L,
                "ipsum.beta",
                450L,
                "dolar.omega",
                300L);

        var expectedStatistics = new AdminStatistics();
        expectedStatistics.setYear(year);
        expectedStatistics.setMonth(month);
        expectedStatistics.setExtensions(extensions);
        expectedStatistics.setDownloads(downloadsTotal);
        expectedStatistics.setDownloadsTotal(downloadsTotal);
        expectedStatistics.setPublishers(publishers);
        expectedStatistics.setAverageReviewsPerExtension(averageReviewsPerExtension);
        expectedStatistics.setNamespaceOwners(namespaceOwners);
        expectedStatistics.setExtensionsByRating(extensionsByRating);
        expectedStatistics.setPublishersByExtensionsPublished(publishersByExtensionsPublished);
        expectedStatistics.setTopMostActivePublishingUsers(topMostActivePublishingUsers);
        expectedStatistics.setTopNamespaceExtensions(topNamespaceExtensions);
        expectedStatistics.setTopNamespaceExtensionVersions(topNamespaceExtensionVersions);
        expectedStatistics.setTopMostDownloadedExtensions(topMostDownloadedExtensions);

        Mockito.when(repositories.countActiveExtensions()).thenReturn(extensions);
        Mockito.when(repositories.downloadsTotal()).thenReturn(downloadsTotal);
        Mockito.when(repositories.countActiveExtensionPublishers()).thenReturn(publishers);
        Mockito.when(repositories.averageNumberOfActiveReviewsPerActiveExtension())
                .thenReturn(averageReviewsPerExtension);
        Mockito.when(repositories.countPublishersThatClaimedNamespaceOwnership()).thenReturn(namespaceOwners);
        Mockito.when(repositories.countActiveExtensionsGroupedByExtensionReviewRating()).thenReturn(extensionsByRating);
        Mockito.when(repositories.countActiveExtensionPublishersGroupedByExtensionsPublished())
                .thenReturn(publishersByExtensionsPublished);
        var limit = 10;
        Mockito.when(repositories.topMostActivePublishingUsers(limit)).thenReturn(topMostActivePublishingUsers);
        Mockito.when(repositories.topNamespaceExtensions(limit)).thenReturn(topNamespaceExtensions);
        Mockito.when(repositories.topNamespaceExtensionVersions(limit)).thenReturn(topNamespaceExtensionVersions);
        Mockito.when(repositories.topMostDownloadedExtensions(limit)).thenReturn(topMostDownloadedExtensions);

        return expectedStatistics;
    }
}
