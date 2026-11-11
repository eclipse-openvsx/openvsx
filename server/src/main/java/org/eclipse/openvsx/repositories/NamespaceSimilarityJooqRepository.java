/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import static org.eclipse.openvsx.jooq.Tables.EXTENSION;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE_MEMBERSHIP;
import static org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.requireTimeout;
import static org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.setLocal;
import static org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.similarity;
import static org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.trigramMatch;
import static org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.withStatementTimeout;

/**
 * Pairs of namespaces whose names resemble each other, for the name squatting admin dashboard.
 * <p>
 * Names are compared ignoring case. A pair matches when the names are within an edit distance that
 * grows with the longer name (see {@link #allowedEdits(int)}), or when they only differ in characters
 * other than letters and digits, such as {@code my-tools} and {@code my_tools}.
 */
@Component
public class NamespaceSimilarityJooqRepository {

    /**
     * Trigram similarity a pair needs before its edit distance is computed, low enough to lose no pair
     * within the allowed edits: it only spares the edit distance of every pair of namespaces.
     */
    static final float PREFILTER_THRESHOLD = 0.3f;

    /** Names up to this long only match by {@link #separatorOnly}: one edit is too much of them. */
    static final int EXACT_MAX_LENGTH = 4;

    /** Names up to this long match within one edit, longer ones within {@link #MAX_EDITS}. */
    static final int ONE_EDIT_MAX_LENGTH = 8;

    static final int MAX_EDITS = 2;

    private final DSLContext dsl;

    public NamespaceSimilarityJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * One namespace of a pair.
     *
     * @param downloads        the downloads of its active extensions
     * @param activeExtensions how many active extensions it has
     * @param firstPublished   when the earliest of its active extensions was published, {@code null}
     *                         without any
     */
    public record Side(
            long id,
            String name,
            long downloads,
            int activeExtensions,
            @Nullable LocalDateTime firstPublished
    ) {}

    /**
     * Two namespaces whose names resemble each other, {@code first} having the lower id.
     *
     * @param editDistance  Levenshtein distance of the lowercased names
     * @param similarity    trigram similarity of the lowercased names, from 0 to 1
     * @param separatorOnly whether the names only differ in characters other than letters and digits
     * @param sharedMember  whether a user is a member of both namespaces
     */
    public record Pair(
            Side first,
            Side second,
            int editDistance,
            float similarity,
            boolean separatorOnly,
            boolean sharedMember
    ) {}

    /** The edits allowed between two names, the longer of which has {@code length} characters. */
    public static int allowedEdits(int length) {
        if (length <= EXACT_MAX_LENGTH) {
            return 0;
        }
        return length <= ONE_EDIT_MAX_LENGTH ? 1 : MAX_EDITS;
    }

    /**
     * Every pair of namespaces whose names resemble each other, ordered by id, leaving out pairs where
     * neither namespace has an active extension. Pairs an admin allowed are included: callers filter them.
     * <p>
     * It probes the trigram index once per namespace, so it takes time in proportion to their number.
     * Transactional because the trigram threshold and the timeout are settings local to the transaction.
     */
    @Transactional
    public List<Pair> findSimilarPairs(Duration statementTimeout) {
        requireTimeout(statementTimeout);
        return withStatementTimeout(dsl, statementTimeout, () -> {
            applyThreshold(dsl);
            return pairsQuery(dsl).fetch(NamespaceSimilarityJooqRepository::toPair);
        });
    }

    /** The % operator compares against this setting. */
    static void applyThreshold(DSLContext tx) {
        setLocal(tx, "pg_trgm.similarity_threshold", Float.toString(PREFILTER_THRESHOLD));
    }

    static ResultQuery<? extends Record> pairsQuery(DSLContext tx) {
        // Materialized so each namespace is lowercased once and the probe below runs once per namespace.
        var names = DSL.name("names").fields("id", "normalized", "stripped").asMaterialized(
                DSL.select(NAMESPACE.ID, DSL.lower(NAMESPACE.NAME), stripped(DSL.lower(NAMESPACE.NAME)))
                        .from(NAMESPACE));

        var probe = names.as("probe");
        var probeId = probe.field("id", Long.class);
        var probeNormalized = probe.field("normalized", String.class);
        var hit = NAMESPACE.as("hit");
        var hitNormalized = DSL.lower(hit.NAME);
        var longer = DSL.greatest(DSL.length(probeNormalized), DSL.length(hitNormalized));
        var editsAllowed = DSL.when(longer.le(EXACT_MAX_LENGTH), DSL.inline(0))
                .when(longer.le(ONE_EDIT_MAX_LENGTH), DSL.inline(1))
                .otherwise(DSL.inline(MAX_EDITS));
        // Each pair turns up from both sides, and the union dedupes it: hit.id > probe.id would offer the
        // planner a primary key range scan in place of the trigram probe.
        var withinEdits = DSL.select(DSL.least(probeId, hit.ID), DSL.greatest(probeId, hit.ID))
                .from(probe)
                .join(hit).on(trigramMatch(hitNormalized, probeNormalized)).and(hit.ID.ne(probeId))
                .where(levenshteinLessEqual(probeNormalized, hitNormalized, MAX_EDITS).le(editsAllowed));

        var left = names.as("left_name");
        var right = names.as("right_name");
        var leftId = left.field("id", Long.class);
        var leftStripped = left.field("stripped", String.class);
        var rightId = right.field("id", Long.class);
        var separatorsOnly = DSL.select(leftId, rightId)
                .from(left)
                .join(right).on(right.field("stripped", String.class).eq(leftStripped)).and(rightId.gt(leftId))
                .where(leftStripped.ne(""));

        var pairs = DSL.name("pairs").fields("first_id", "second_id").as(withinEdits.union(separatorsOnly));
        var firstId = pairs.field("first_id", Long.class);
        var secondId = pairs.field("second_id", Long.class);

        var stats = DSL.name("stats").fields("namespace_id", "downloads", "extensions", "first_published").as(
                DSL.select(
                        EXTENSION.NAMESPACE_ID,
                        DSL.sum(EXTENSION.DOWNLOAD_COUNT),
                        DSL.count(),
                        DSL.min(EXTENSION.PUBLISHED_DATE))
                        .from(EXTENSION)
                        .where(EXTENSION.ACTIVE.isTrue())
                        .and(
                                EXTENSION.NAMESPACE_ID.in(
                                        DSL.select(firstId).from(pairs).union(DSL.select(secondId).from(pairs))))
                        .groupBy(EXTENSION.NAMESPACE_ID));
        var firstStats = stats.as("first_stats");
        var secondStats = stats.as("second_stats");

        var first = NAMESPACE.as("first");
        var second = NAMESPACE.as("second");
        var firstName = DSL.lower(first.NAME);
        var secondName = DSL.lower(second.NAME);

        var firstMember = NAMESPACE_MEMBERSHIP.as("first_member");
        var secondMember = NAMESPACE_MEMBERSHIP.as("second_member");
        var sharedMember = DSL.exists(
                DSL.selectOne()
                        .from(firstMember)
                        .join(secondMember).on(secondMember.USER_DATA.eq(firstMember.USER_DATA))
                        .where(firstMember.NAMESPACE.eq(firstId))
                        .and(secondMember.NAMESPACE.eq(secondId)));

        return tx.with(names)
                .with(pairs)
                .with(stats)
                .select(
                        first.ID,
                        first.NAME,
                        firstStats.field("downloads"),
                        firstStats.field("extensions"),
                        firstStats.field("first_published"),
                        second.ID,
                        second.NAME,
                        secondStats.field("downloads"),
                        secondStats.field("extensions"),
                        secondStats.field("first_published"),
                        DSL.function("levenshtein", Integer.class, firstName, secondName),
                        similarity(firstName, secondName),
                        DSL.field(stripped(firstName).eq(stripped(secondName))),
                        DSL.field(sharedMember))
                .from(pairs)
                .join(first).on(first.ID.eq(firstId))
                .join(second).on(second.ID.eq(secondId))
                .leftJoin(firstStats).on(firstStats.field("namespace_id", Long.class).eq(firstId))
                .leftJoin(secondStats).on(secondStats.field("namespace_id", Long.class).eq(secondId))
                .where(
                        firstStats.field("namespace_id").isNotNull()
                                .or(secondStats.field("namespace_id").isNotNull()))
                .orderBy(firstId, secondId);
    }

    /** Lowercased name without the characters other than letters and digits. */
    private static Field<String> stripped(Field<String> lowerName) {
        return DSL.function(
                "regexp_replace",
                String.class,
                lowerName,
                DSL.inline("[^[:alnum:]]"),
                DSL.inline(""),
                DSL.inline("g"));
    }

    private static Field<Integer> levenshteinLessEqual(Field<String> first, Field<String> second, int max) {
        return DSL.function("levenshtein_less_equal", Integer.class, first, second, DSL.inline(max));
    }

    private static Pair toPair(Record record) {
        return new Pair(
                toSide(record, 0),
                toSide(record, 5),
                record.get(10, Integer.class),
                record.get(11, Float.class),
                record.get(12, Boolean.class),
                record.get(13, Boolean.class));
    }

    private static Side toSide(Record record, int offset) {
        var downloads = record.get(offset + 2, Long.class);
        var extensions = record.get(offset + 3, Integer.class);
        return new Side(
                record.get(offset, Long.class),
                record.get(offset + 1, String.class),
                downloads == null ? 0 : downloads,
                extensions == null ? 0 : extensions,
                record.get(offset + 4, LocalDateTime.class));
    }
}
