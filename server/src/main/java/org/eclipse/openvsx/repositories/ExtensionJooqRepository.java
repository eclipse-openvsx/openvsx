/** ******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.repositories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.web.SitemapRow;

import static org.eclipse.openvsx.jooq.Tables.EXTENSION;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_VERSION;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE_MEMBERSHIP;

@Component
public class ExtensionJooqRepository {

    private final DSLContext dsl;
    private final ExtensionVersionJooqRepository extensionVersionRepo;

    public ExtensionJooqRepository(DSLContext dsl, ExtensionVersionJooqRepository extensionVersionRepo) {
        this.dsl = dsl;
        this.extensionVersionRepo = extensionVersionRepo;
    }

    public List<Extension> findAllActiveById(Collection<Long> ids) {
        var query = findAllActive();
        query.addConditions(EXTENSION.ID.in(ids));
        return fetch(query);
    }

    public List<Extension> findAllActiveByPublicId(Collection<String> publicIds, String... namespacesToExclude) {
        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION.PUBLIC_ID.in(publicIds));
        for (var namespaceToExclude : namespacesToExclude) {
            conditions.add(NAMESPACE.NAME.notEqualIgnoreCase(namespaceToExclude));
        }

        var query = findAllActive();
        query.addConditions(conditions);
        return fetch(query);
    }

    public Extension findActiveByNameIgnoreCaseAndNamespaceNameIgnoreCase(String name, String namespaceName) {
        var query = findAllActive();
        query.addConditions(
                EXTENSION.NAME.equalIgnoreCase(name),
                NAMESPACE.NAME.equalIgnoreCase(namespaceName));

        return query.fetchOne(this::toExtension);
    }

    public List<Extension> findAllPublicIds() {
        return findPublicId().fetch().map(this::toPublicId);
    }

    public Extension findPublicId(String namespace, String extension) {
        var query = findPublicId();
        query.addConditions(
                EXTENSION.NAME.equalIgnoreCase(extension),
                NAMESPACE.NAME.equalIgnoreCase(namespace));

        return query.fetchOne(this::toPublicId);
    }

    public Extension findPublicId(String publicId) {
        var query = findPublicId();
        query.addConditions(EXTENSION.PUBLIC_ID.eq(publicId));
        return query.fetchOne(this::toPublicId);
    }

    public Extension findNamespacePublicId(String publicId) {
        var query = findPublicId();
        query.addConditions(NAMESPACE.PUBLIC_ID.eq(publicId));
        query.addLimit(1);
        return query.fetchOne(this::toPublicId);
    }

    private SelectQuery<Record> findPublicId() {
        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME);

        query.addFrom(EXTENSION);
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        return query;
    }

    private Extension toPublicId(Record row) {
        var namespace = new Namespace();
        namespace.setId(row.get(NAMESPACE.ID));
        namespace.setPublicId(row.get(NAMESPACE.PUBLIC_ID));
        namespace.setName(row.get(NAMESPACE.NAME));

        var extension = new Extension();
        extension.setId(row.get(EXTENSION.ID));
        extension.setPublicId(row.get(EXTENSION.PUBLIC_ID));
        extension.setName(row.get(EXTENSION.NAME));
        extension.setNamespace(namespace);
        return extension;
    }

    private SelectQuery<Record> findAllActive() {
        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME);

        query.addFrom(EXTENSION);
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(EXTENSION.ACTIVE.eq(true));
        return query;
    }

    private List<Extension> fetch(SelectQuery<Record> query) {
        return query.fetch().map(this::toExtension);
    }

    private Extension toExtension(Record row) {
        var extension = new Extension();
        extension.setId(row.get(EXTENSION.ID));
        extension.setPublicId(row.get(EXTENSION.PUBLIC_ID));
        extension.setName(row.get(EXTENSION.NAME));
        extension.setAverageRating(row.get(EXTENSION.AVERAGE_RATING));
        extension.setReviewCount(row.get(EXTENSION.REVIEW_COUNT));
        extension.setDownloadCount(row.get(EXTENSION.DOWNLOAD_COUNT));
        extension.setPublishedDate(row.get(EXTENSION.PUBLISHED_DATE));
        extension.setLastUpdatedDate(row.get(EXTENSION.LAST_UPDATED_DATE));

        var namespace = new Namespace();
        namespace.setId(row.get(NAMESPACE.ID));
        namespace.setPublicId(row.get(NAMESPACE.PUBLIC_ID));
        namespace.setName(row.get(NAMESPACE.NAME));
        namespace.setDisplayName(row.get(NAMESPACE.DISPLAY_NAME));
        extension.setNamespace(namespace);

        return extension;
    }

    public void updatePublicIds(Map<Long, String> publicIds) {
        if (publicIds.isEmpty()) {
            return;
        }

        var extension = EXTENSION.as("e");
        var rows = publicIds.entrySet().stream()
                .map(e -> DSL.row(e.getKey(), e.getValue()))
                .toList();

        var updates = DSL.values(rows.toArray(Row2[]::new)).as("u", "id", "public_id");
        dsl.update(extension)
                .set(extension.PUBLIC_ID, updates.field("public_id", String.class))
                .from(updates)
                .where(updates.field("id", Long.class).eq(extension.ID))
                .execute();
    }

    public boolean publicIdExists(String publicId) {
        return dsl.selectOne()
                .from(EXTENSION)
                .where(EXTENSION.PUBLIC_ID.eq(publicId))
                .fetch()
                .isNotEmpty();
    }

    public List<SitemapRow> fetchSitemapRows() {
        var LAST_UPDATED = DSL.toChar(EXTENSION.LAST_UPDATED_DATE, "YYYY-MM-DD");
        return dsl.select(
                NAMESPACE.NAME,
                EXTENSION.NAME,
                LAST_UPDATED)
                .from(NAMESPACE)
                .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                .where(EXTENSION.ACTIVE.eq(true))
                .fetch()
                .map(
                        row -> new SitemapRow(
                                row.get(NAMESPACE.NAME),
                                row.get(EXTENSION.NAME),
                                row.get(LAST_UPDATED)));
    }

    public List<String> findActiveExtensionNames(Namespace namespace) {
        return dsl.select(EXTENSION.NAME)
                .from(EXTENSION)
                .where(EXTENSION.NAMESPACE_ID.eq(namespace.getId()))
                .and(EXTENSION.ACTIVE.eq(true))
                .orderBy(EXTENSION.NAME.asc())
                .fetch(EXTENSION.NAME);
    }

    public List<String> findAllExtensionNames(Namespace namespace) {
        return dsl.select(EXTENSION.NAME)
                .from(EXTENSION)
                .where(EXTENSION.NAMESPACE_ID.eq(namespace.getId()))
                .orderBy(EXTENSION.NAME.asc())
                .fetch(EXTENSION.NAME);
    }

    public String findFirstUnresolvedDependency(List<ExtensionId> dependencies) {
        if (dependencies.isEmpty()) {
            return null;
        }

        var ids = DSL.values(dependencies.stream().map(d -> DSL.row(d.namespace(), d.extension())).toArray(Row2[]::new))
                .as("ids", "namespace", "extension");
        var namespace = ids.field("namespace", String.class);
        var extension = ids.field("extension", String.class);
        var unresolvedDependency = DSL.concat(namespace, DSL.value("."), extension).as("unresolved_dependency");
        return dsl.select(unresolvedDependency)
                .from(ids)
                .leftJoin(NAMESPACE).on(NAMESPACE.NAME.equalIgnoreCase(namespace))
                .leftJoin(EXTENSION)
                .on(EXTENSION.NAME.equalIgnoreCase(extension).and(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID)))
                .where(NAMESPACE.NAME.isNull()).or(EXTENSION.NAME.isNull())
                .limit(1)
                .fetchOne(unresolvedDependency);
    }

    public List<Extension> findExtensionsForUrls(Namespace namespace) {
        return dsl.select(EXTENSION.ID, EXTENSION.NAME)
                .from(EXTENSION)
                .where(EXTENSION.NAMESPACE_ID.eq(namespace.getId()))
                .fetch(row -> {
                    var extension = new Extension();
                    extension.setId(row.get(EXTENSION.ID));
                    extension.setName(row.get(EXTENSION.NAME));
                    extension.setNamespace(namespace);
                    return extension;
                });
    }

    public boolean hasExtension(String namespace, String extension) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(NAMESPACE)
                        .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                        .where(NAMESPACE.NAME.equalIgnoreCase(namespace))
                        .and(EXTENSION.NAME.equalIgnoreCase(extension)));
    }

    /**
     * Find extensions similar to the given fields using PostgreSQL's Levenshtein distance.
     */
    public List<Extension> findSimilarExtensionsByLevenshtein(
            String extensionName,
            String namespaceName,
            String displayName,
            List<String> excludeNamespaces,
            double levenshteinThreshold,
            boolean verifiedOnly,
            int limit
    ) {
        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME);

        query.addFrom(EXTENSION);
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));

        query.addConditions(EXTENSION.ACTIVE.eq(true));

        if (excludeNamespaces != null && !excludeNamespaces.isEmpty()) {
            query.addConditions(NAMESPACE.NAME.notIn(excludeNamespaces));
        }

        if (verifiedOnly) {
            var hasOwnerSubquery = DSL.selectOne()
                    .from(NAMESPACE_MEMBERSHIP)
                    .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(NAMESPACE.ID))
                    .and(NAMESPACE_MEMBERSHIP.ROLE.eq("owner"));

            query.addConditions(DSL.exists(hasOwnerSubquery));
        }

        var conditions = new ArrayList<Condition>();

        if (extensionName != null && !extensionName.isEmpty()) {
            var lowerExtensionName = extensionName.toLowerCase();

            var conditionList = new ArrayList<Condition>();

            int inputLen = extensionName.length();
            int minLen = (int) Math.floor(inputLen * (1.0 - levenshteinThreshold));
            int lenMax = (int) Math.ceil(inputLen / (1.0 - levenshteinThreshold));
            conditionList.add(DSL.length(EXTENSION.NAME).between(minLen, lenMax));

            var maxLen = DSL.greatest(
                    DSL.val(lowerExtensionName.length()),
                    DSL.length(EXTENSION.NAME));
            var maxDistance = maxLen.mul(levenshteinThreshold);

            var levenshteinDist = DSL.function(
                    "levenshtein_less_equal",
                    Integer.class,
                    DSL.val(lowerExtensionName),
                    DSL.lower(EXTENSION.NAME),
                    DSL.val(1), // insertion cost
                    DSL.val(1), // deletion cost
                    DSL.val(1), // substitution cost
                    maxDistance.cast(Integer.class));

            conditionList.add(levenshteinDist.le(maxDistance));

            conditions.add(DSL.and(conditionList));
        }

        if (namespaceName != null && !namespaceName.isEmpty()) {
            var lowerNamespaceName = namespaceName.toLowerCase();

            var conditionList = new ArrayList<Condition>();

            int inputLen = namespaceName.length();
            int minLen = (int) Math.floor(inputLen * (1.0 - levenshteinThreshold));
            int lenMax = (int) Math.ceil(inputLen / (1.0 - levenshteinThreshold));
            conditionList.add(DSL.length(NAMESPACE.NAME).between(minLen, lenMax));

            var maxLen = DSL.greatest(
                    DSL.val(lowerNamespaceName.length()),
                    DSL.length(NAMESPACE.NAME));
            var maxDistance = maxLen.mul(levenshteinThreshold);

            var levenshteinDist = DSL.function(
                    "levenshtein_less_equal",
                    Integer.class,
                    DSL.val(lowerNamespaceName),
                    DSL.lower(NAMESPACE.NAME),
                    DSL.val(1), // insertion cost
                    DSL.val(1), // deletion cost
                    DSL.val(1), // substitution cost
                    maxDistance.cast(Integer.class));

            conditionList.add(levenshteinDist.le(maxDistance));

            conditions.add(DSL.and(conditionList));
        }

        if (displayName != null && !displayName.isEmpty()) {
            var evLatest = EXTENSION_VERSION.as("ev_latest");

            var lowerDisplayName = displayName.toLowerCase();

            int inputLen = displayName.length();
            int minLen = (int) Math.floor(inputLen * (1.0 - levenshteinThreshold));
            int lenMax = (int) Math.ceil(inputLen / (1.0 - levenshteinThreshold));

            var maxDisplayNameLen = DSL.greatest(
                    DSL.val(lowerDisplayName.length()),
                    DSL.length(evLatest.DISPLAY_NAME));
            var maxDisplayNameDistance = maxDisplayNameLen.mul(levenshteinThreshold);

            var displayNameLevenshtein = DSL.function(
                    "levenshtein_less_equal",
                    Integer.class,
                    DSL.val(lowerDisplayName),
                    DSL.lower(evLatest.DISPLAY_NAME),
                    DSL.val(1), // insertion cost
                    DSL.val(1), // deletion cost
                    DSL.val(1), // substitution cost
                    maxDisplayNameDistance.cast(Integer.class));

            var latestQuery = extensionVersionRepo.findLatestQuery(null, false, true);
            latestQuery.addSelect(EXTENSION_VERSION.ID);
            latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
            var latestVersionId = latestQuery.asField().coerce(Long.class);

            var displayNameSimilaritySubquery = DSL.selectOne()
                    .from(evLatest)
                    .where(evLatest.EXTENSION_ID.eq(EXTENSION.ID))
                    .and(evLatest.ACTIVE.eq(true))
                    .and(evLatest.ID.eq(latestVersionId))
                    .and(evLatest.DISPLAY_NAME.isNotNull())
                    .and(evLatest.DISPLAY_NAME.ne(""))
                    .and(DSL.length(evLatest.DISPLAY_NAME).between(minLen, lenMax))
                    .and(displayNameLevenshtein.le(maxDisplayNameDistance));

            conditions.add(DSL.exists(displayNameSimilaritySubquery));
        }

        if (!conditions.isEmpty()) {
            query.addConditions(DSL.or(conditions));
        } else {
            return List.of();
        }

        // Order by best match first (lowest Levenshtein distance)
        if (extensionName != null && !extensionName.isEmpty()) {
            var lowerExtensionName = extensionName.toLowerCase();
            var levenshteinDist = DSL.function(
                    "levenshtein",
                    Integer.class,
                    DSL.val(lowerExtensionName),
                    DSL.lower(EXTENSION.NAME));
            query.addOrderBy(levenshteinDist.asc());
        }

        query.addLimit(limit);

        return query.fetch().map(this::toExtension);
    }

    /**
     * Finds an active extension whose latest version carries the given display name, skipping the
     * namespaces in {@code excludeNamespaces}.
     * <p>
     * The display names are compared as a reader seeing the two extensions side by side would compare
     * them, rather than byte for byte: casing and surrounding whitespace are normalised away, as
     * neither is visible enough to tell two extensions apart. Everything beyond that -- transposed
     * characters, homoglyphs, added punctuation -- is left to {@link
     * #findSimilarExtensionsByLevenshtein}, which reports rather than rejects and is far too
     * imprecise to decide a publication on.
     * <p>
     * Only the latest version of an extension is considered, because that is the name the registry
     * shows for it. An extension that carried the name in an earlier version and has since renamed
     * itself does not hold the name any longer, and does not stand in the way of another extension
     * taking it.
     *
     * @return the conflicting extension, or {@code null} if no other extension shows that name
     */
    public Extension findActiveExtensionByDisplayName(String displayName, Collection<String> excludeNamespaces) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        // Both sides are normalised by the database rather than one of them in Java, so that the
        // comparison cannot be thrown off by the two disagreeing on what lower casing means.
        var normalizedDisplayName = DSL.lower(DSL.trim(DSL.val(displayName)));

        // The row that matches the display name. Aliased because the correlated subquery below selects
        // from the unaliased EXTENSION_VERSION and would otherwise be shadowed by this one.
        var evMatch = EXTENSION_VERSION.as("ev_match");

        // Correlated to the display name match rather than to EXTENSION, so that the subquery depends
        // only on the rows the display name index already narrowed down to. Correlating it to
        // EXTENSION.ID instead lets the planner hash join the two and evaluate the subquery once per
        // row of a sequential scan over EXTENSION, which costs a full scan of that table plus one index
        // probe per extension on every publication, and grows with the table rather than with the
        // number of extensions actually carrying the name.
        var latestQuery = extensionVersionRepo.findLatestQuery(null, false, true);
        latestQuery.addSelect(EXTENSION_VERSION.ID);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(evMatch.EXTENSION_ID));
        var latestVersionId = latestQuery.asField().coerce(Long.class);

        var query = findAllActive();
        query.addJoin(evMatch, evMatch.EXTENSION_ID.eq(EXTENSION.ID));
        query.addConditions(
                evMatch.ACTIVE.eq(true),
                DSL.lower(DSL.trim(evMatch.DISPLAY_NAME)).eq(normalizedDisplayName),
                evMatch.ID.eq(latestVersionId));

        if (excludeNamespaces != null && !excludeNamespaces.isEmpty()) {
            // Upper cased to match the unique index on UPPER(name), and nulls dropped because a single
            // null in a NOT IN list makes the whole predicate null, which would silently stop the check
            // from ever reporting a conflict.
            var excluded = excludeNamespaces.stream()
                    .filter(Objects::nonNull)
                    .map(name -> name.toUpperCase(Locale.ROOT))
                    .toList();
            if (!excluded.isEmpty()) {
                query.addConditions(DSL.upper(NAMESPACE.NAME).notIn(excluded));
            }
        }

        // Deliberately unordered. Any ORDER BY here lets the planner satisfy the ordering by walking
        // that index and stopping at the first match, rather than driving off the display name index --
        // which it costs as cheap because it has no idea how rare a match is, and which walks the whole
        // EXTENSION table when there is none. Which of several conflicting extensions gets named in the
        // rejection message is arbitrary, and not worth that.
        query.addLimit(1);
        return query.fetchOne(this::toExtension);
    }
}
