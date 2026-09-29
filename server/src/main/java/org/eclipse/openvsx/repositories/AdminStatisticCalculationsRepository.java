/******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.NamespaceMembership;

import static org.eclipse.openvsx.jooq.Tables.*;

@Component
public class AdminStatisticCalculationsRepository {

    private final DSLContext dsl;

    public AdminStatisticCalculationsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long downloadsTotal() {
        var sum = DSL.coalesce(DSL.sum(EXTENSION.DOWNLOAD_COUNT), new BigDecimal(0));
        return dsl.select(sum)
                .from(EXTENSION)
                .where(EXTENSION.ACTIVE.eq(true))
                .fetchOne(sum)
                .longValue();
    }

    public int countActiveExtensions() {
        var count = DSL.count(EXTENSION.ID);
        return dsl.select(count)
                .from(EXTENSION)
                .where(EXTENSION.ACTIVE.eq(true))
                .fetchOne(count);
    }

    public int countActiveExtensionPublishers() {
        var publishers = DSL.countDistinct(EXTENSION_VERSION.PUBLISHED_BY_ID);
        return dsl.select(publishers)
                .from(EXTENSION)
                .join(EXTENSION_VERSION).on(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .fetchOne(publishers);
    }

    public Map<Integer, Integer> countActiveExtensionPublishersGroupedByExtensionsPublished() {
        var aliasPublisher = "publisher";
        var aliasExtensionCount = "extension_count";
        var extensionCountsByPublisher = dsl.select(
                EXTENSION_VERSION.PUBLISHED_BY_ID.as(aliasPublisher),
                DSL.countDistinct(EXTENSION.ID).as(aliasExtensionCount))
                .from(EXTENSION)
                .join(EXTENSION_VERSION).on(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .groupBy(EXTENSION_VERSION.PUBLISHED_BY_ID)
                .asTable("aep");

        return dsl.select(
                extensionCountsByPublisher.field(aliasExtensionCount, Integer.class),
                DSL.count(extensionCountsByPublisher.field(aliasPublisher)))
                .from(extensionCountsByPublisher)
                .groupBy(extensionCountsByPublisher.field(aliasExtensionCount, Integer.class))
                .fetch()
                .stream()
                .map(l -> Map.entry(l.value1(), l.value2()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<Integer, Integer> countActiveExtensionsGroupedByExtensionReviewRating() {
        var aliasExtensionId = "extension_id";
        var aliasAverageRating = "average_rating";

        var averageRatingByExtension = dsl.select(
                EXTENSION.ID.as(aliasExtensionId),
                DSL.round(DSL.avg(EXTENSION_REVIEW.RATING)).as(aliasAverageRating))
                .from(EXTENSION)
                .join(EXTENSION_REVIEW).on(EXTENSION_REVIEW.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_REVIEW.ACTIVE.eq(true))
                .groupBy(EXTENSION.ID)
                .asTable("aer");

        return dsl.select(
                averageRatingByExtension.field(aliasAverageRating, Integer.class),
                DSL.count(averageRatingByExtension.field(aliasExtensionId)))
                .from(averageRatingByExtension)
                .groupBy(averageRatingByExtension.field(aliasAverageRating, Integer.class))
                .fetch()
                .stream()
                .map(l -> Map.entry(l.value1(), l.value2()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public double averageNumberOfActiveReviewsPerActiveExtension() {
        var averageReviewsPerExtension = DSL.case_()
                .when(
                        DSL.count(EXTENSION.ID).greaterThan(0),
                        DSL.count(EXTENSION_REVIEW.ID).divide(DSL.countDistinct(EXTENSION.ID).times(1.0)))
                .otherwise(0)
                .coerce(double.class)
                .as("avg_reviews_per_extension");

        return dsl.select(averageReviewsPerExtension)
                .from(EXTENSION)
                .join(EXTENSION_REVIEW).on(EXTENSION_REVIEW.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_REVIEW.ACTIVE.eq(true))
                .fetchOne(averageReviewsPerExtension);
    }

    public int countPublishersThatClaimedNamespaceOwnership() {
        var count = DSL.countDistinct(NAMESPACE_MEMBERSHIP.USER_DATA);
        return dsl.select(count)
                .from(EXTENSION)
                .join(EXTENSION_VERSION).on(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID))
                .join(NAMESPACE_MEMBERSHIP)
                .on(
                        NAMESPACE_MEMBERSHIP.USER_DATA.eq(EXTENSION_VERSION.PUBLISHED_BY_ID)
                                .and(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(EXTENSION.NAMESPACE_ID)))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(NAMESPACE_MEMBERSHIP.ROLE.eq(NamespaceMembership.ROLE_OWNER))
                .fetchOne(count);
    }

    public Map<String, Integer> topMostActivePublishingUsers(int limit) {
        var count = DSL.count(EXTENSION_VERSION.ID).as("extension_version_count");
        return dsl.select(USER_DATA.ID, USER_DATA.LOGIN_NAME, count)
                .from(EXTENSION_VERSION)
                .join(USER_DATA).on(USER_DATA.ID.eq(EXTENSION_VERSION.PUBLISHED_BY_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .groupBy(USER_DATA.ID)
                .orderBy(count.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(r -> {
                    var loginName = r.get(USER_DATA.LOGIN_NAME);
                    var extensionVersionCount = r.get(count);
                    return Map.entry(loginName, extensionVersionCount);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Integer> topNamespaceExtensions(int limit) {
        var count = DSL.count(EXTENSION.ID).as("extension_count");
        return dsl.select(NAMESPACE.ID, NAMESPACE.NAME, count)
                .from(NAMESPACE)
                .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .groupBy(NAMESPACE.ID)
                .orderBy(count.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(r -> {
                    var namespaceName = r.get(NAMESPACE.NAME);
                    var extensionCount = r.get(count);
                    return Map.entry(namespaceName, extensionCount);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Integer> topNamespaceExtensionVersions(int limit) {
        var count = DSL.count(EXTENSION_VERSION.ID).as("extension_version_count");
        return dsl.select(NAMESPACE.ID, NAMESPACE.NAME, count)
                .from(NAMESPACE)
                .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                .join(EXTENSION_VERSION).on(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .groupBy(NAMESPACE.ID)
                .orderBy(count.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(r -> {
                    var namespaceName = r.get(NAMESPACE.NAME);
                    var extensionVersionCount = r.get(count);
                    return Map.entry(namespaceName, extensionVersionCount);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Long> topMostDownloadedExtensions(int limit) {
        var downloads = EXTENSION.DOWNLOAD_COUNT;
        var extensionId = DSL.concat(NAMESPACE.NAME, DSL.value("."), EXTENSION.NAME);
        return dsl.select(extensionId, downloads)
                .from(NAMESPACE)
                .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .orderBy(downloads.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(r -> Map.entry(r.get(extensionId), r.get(downloads).longValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
