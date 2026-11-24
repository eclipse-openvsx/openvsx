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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.transaction.Transactional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.NameSimilarityAllow;
import org.eclipse.openvsx.entities.NameSimilarityField;

import static org.eclipse.openvsx.jooq.Tables.EXTENSION;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_SCAN;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_VALIDATION_FAILURE;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_VERSION;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;
import static org.eclipse.openvsx.jooq.Tables.NAME_SIMILARITY_ALLOW;

/**
 * Trigram lookups of the active extensions whose name or display name matches or resembles a given
 * one, for the NAME_SIMILARITY publish check and the name squatting admin dashboard.
 * <p>
 * Results come back in no particular order: ordering by similarity in SQL makes the planner give up
 * the trigram indexes, so callers sort what they get. Each lookup sets a statement timeout local to its
 * transaction and leaves it set, and a cancelled statement aborts that transaction, so callers must not
 * hold one open.
 */
@Component
public class NameSimilarityJooqRepository {

    /**
     * Every Unicode space separator, trimmed from both ends of a display name. Must stay identical to the
     * expression of extension_version_display_name_trgm_idx (V1_87), or the lookups scan every version.
     */
    static final String DISPLAY_NAME_TRIM_CHARS = " \u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F\u205F\u3000";

    /** {@link #DISPLAY_NAME_TRIM_CHARS} as the Unicode escape string literal V1_87 spells out. */
    static final String DISPLAY_NAME_TRIM_LITERAL = toUnicodeEscapeLiteral(DISPLAY_NAME_TRIM_CHARS);

    /** The first step of the walk over flagged extensions reads this many failures, each later one twice as many. */
    static final int WALK_FIRST_CHUNK = 1024;

    /** Steps the walk takes before it gives up and looks every flagged extension up in one go. */
    static final int WALK_MAX_STEPS = 4;

    private final DSLContext dsl;

    public NameSimilarityJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find the active extensions outside the probe's namespace that match it, up to {@code limit} per
     * field and display name. Which matches are cut off beyond the limit is unspecified unless the probe
     * asks for the earliest first.
     * <p>
     * Transactional because the similarity threshold and the timeout are settings local to the transaction.
     */
    @Transactional
    public List<Match> findSimilar(Probe probe) {
        var displayNames = new LinkedHashSet<String>();
        for (var displayName : probe.displayNames()) {
            if (displayName != null && !displayName.isBlank()) {
                displayNames.add(displayName);
            }
        }

        applySettings(dsl, probe);
        var matches = new ArrayList<Match>();
        if (!probe.extensionName().isBlank()) {
            matches.addAll(
                    namesQuery(dsl, probe)
                            .fetch(record -> toMatch(record, NameSimilarityField.NAME, probe.extensionName())));
        }
        for (var displayName : displayNames) {
            matches.addAll(
                    displayNamesQuery(dsl, probe, displayName)
                            .fetch(record -> toMatch(record, NameSimilarityField.DISPLAY_NAME, displayName)));
        }
        return matches;
    }

    /** The probe's statement timeout, and the threshold the % operator compares against. */
    static void applySettings(DSLContext tx, Probe probe) {
        setLocal(
                tx,
                probe.statementTimeout(),
                Float.toString(Math.min(probe.nameThreshold(), probe.displayNameThreshold())));
    }

    // The column layout of namesQuery and displayNamesQuery is the same, and toMatch relies on it.
    //
    // Both filter through the trigram index in a MATERIALIZED step first. Inlined, the GROUP BY and LIMIT
    // around it lead the planner to walk the table in key order hoping to stop early, testing every row.

    static ResultQuery<? extends Record> namesQuery(DSLContext tx, Probe probe) {
        var probed = DSL.lower(DSL.val(probe.extensionName()));
        var lowerName = DSL.lower(EXTENSION.NAME);
        var exactOnly = probe.extensionName().length() < probe.minFuzzyLength();

        var hit = DSL.name("name_hit").fields("extension_id", "normalized").asMaterialized(
                DSL.select(EXTENSION.ID, lowerName)
                        .from(EXTENSION)
                        .where(EXTENSION.ACTIVE.isTrue())
                        .and(exactOnly ? lowerName.eq(probed) : trigramMatch(lowerName, probed))
                        .and(onlyIn(EXTENSION.ID, probe)));
        var hitId = hit.field("extension_id", Long.class);
        var hitNormalized = hit.field("normalized", String.class);
        var similarity = similarity(hitNormalized, probed);

        return tx.with(hit)
                .select(
                        EXTENSION.ID,
                        NAMESPACE.NAME,
                        EXTENSION.NAME,
                        EXTENSION.NAME.as("matched_value"),
                        similarity,
                        DSL.field(hitNormalized.eq(probed)),
                        namespaceSimilarity(probe),
                        EXTENSION.PUBLISHED_DATE,
                        EXTENSION.DOWNLOAD_COUNT)
                .from(hit)
                .join(EXTENSION).on(EXTENSION.ID.eq(hitId))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(exactOnly ? DSL.noCondition() : similarity.ge(probe.nameThreshold()))
                .and(DSL.lower(NAMESPACE.NAME).ne(DSL.lower(DSL.val(probe.namespaceName()))))
                .and(notExcluded(probe))
                .orderBy(earliestFirst(probe))
                .limit(probe.limit());
    }

    static ResultQuery<? extends Record> displayNamesQuery(DSLContext tx, Probe probe, String displayName) {
        var probed = normalizedDisplayName(DSL.val(displayName));
        var candidate = normalizedDisplayName(EXTENSION_VERSION.DISPLAY_NAME);
        var exactOnly = normalizeDisplayName(displayName).length() < probe.minFuzzyLength();

        var hit = DSL.name("display_name_hit").fields("extension_id", "display_name", "normalized").asMaterialized(
                DSL.select(EXTENSION_VERSION.EXTENSION_ID, EXTENSION_VERSION.DISPLAY_NAME, candidate)
                        .from(EXTENSION_VERSION)
                        .where(EXTENSION_VERSION.ACTIVE.isTrue())
                        .and(exactOnly ? candidate.eq(probed) : trigramMatch(candidate, probed))
                        .and(onlyIn(EXTENSION_VERSION.EXTENSION_ID, probe)));
        var hitId = hit.field("extension_id", Long.class);
        var hitDisplayName = hit.field("display_name", String.class);
        var hitNormalized = hit.field("normalized", String.class);
        var similarity = similarity(hitNormalized, probed);

        // Versions of one extension share their display name, so they collapse to one row per value.
        return tx.with(hit)
                .select(
                        EXTENSION.ID,
                        NAMESPACE.NAME,
                        EXTENSION.NAME,
                        DSL.min(hitDisplayName).as("matched_value"),
                        similarity,
                        DSL.field(hitNormalized.eq(probed)),
                        namespaceSimilarity(probe),
                        EXTENSION.PUBLISHED_DATE,
                        EXTENSION.DOWNLOAD_COUNT)
                .from(hit)
                .join(EXTENSION).on(EXTENSION.ID.eq(hitId))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(EXTENSION.ACTIVE.isTrue())
                .and(exactOnly ? DSL.noCondition() : similarity.ge(probe.displayNameThreshold()))
                .and(DSL.lower(NAMESPACE.NAME).ne(DSL.lower(DSL.val(probe.namespaceName()))))
                .and(notExcluded(probe))
                .groupBy(
                        EXTENSION.ID,
                        NAMESPACE.NAME,
                        EXTENSION.NAME,
                        hitNormalized,
                        EXTENSION.PUBLISHED_DATE,
                        EXTENSION.DOWNLOAD_COUNT)
                .orderBy(earliestFirst(probe))
                .limit(probe.limit());
    }

    private static Condition onlyIn(Field<Long> extensionId, Probe probe) {
        return probe.onlyExtensionIds().isEmpty() ? DSL.noCondition() : extensionId.in(probe.onlyExtensionIds());
    }

    private static Condition notExcluded(Probe probe) {
        return probe.excludedExtensionIds().isEmpty()
                ? DSL.noCondition()
                : EXTENSION.ID.notIn(probe.excludedExtensionIds());
    }

    private static List<SortField<?>> earliestFirst(Probe probe) {
        return probe.earliestFirst()
                ? List.of(EXTENSION.PUBLISHED_DATE.asc().nullsFirst(), EXTENSION.ID.asc())
                : List.of();
    }

    private static Field<Float> namespaceSimilarity(Probe probe) {
        return similarity(DSL.lower(NAMESPACE.NAME), DSL.lower(DSL.val(probe.namespaceName())));
    }

    /**
     * The active extensions a publish check of type {@code checkType} flagged, most recently flagged
     * first, followed by the others whose display name another namespace's active extension shares, by
     * id. Flagged extensions flagged at the same instant follow the scan that flagged them last, newest
     * first.
     * <p>
     * The second kind costs a pass over every active version, so it is only looked for once a page
     * reaches past the flagged ones.
     *
     * @param offset  where to start in the flagged extensions, unless {@code afterId} is given
     * @param afterId the last extension sharing a display name already listed, to list those after it
     *                and none of the flagged ones; it does not shift when extensions before it go away
     */
    @Transactional
    public List<Candidate> findCandidates(
            String checkType,
            int offset,
            @Nullable Long afterId,
            int limit,
            Duration statementTimeout
    ) {
        return findCandidates(checkType, offset, afterId, limit, statementTimeout, WALK_FIRST_CHUNK, WALK_MAX_STEPS);
    }

    @Transactional
    List<Candidate> findCandidates(
            String checkType,
            int offset,
            @Nullable Long afterId,
            int limit,
            Duration statementTimeout,
            int firstChunk,
            int maxSteps
    ) {
        requireTimeout(statementTimeout);
        // Past the flagged extensions, every one of them is needed, as the shared ones exclude them.
        var needed = afterId != null ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, (long) offset + limit);
        setLocal(dsl, statementTimeout, null);
        var flagged = walkFlagged(checkType, needed, firstChunk, maxSteps);
        if (flagged == null) {
            flagged = allFlaggedQuery(dsl, checkType).fetch(this::toCandidate);
        }
        if (afterId == null && flagged.size() >= needed) {
            return List.copyOf(flagged.subList(offset, needed));
        }

        // Short of the page, the flagged list is complete, so it is also what the shared ones exclude.
        var candidates = afterId != null
                ? new ArrayList<Candidate>()
                : new ArrayList<>(flagged.subList(Math.min(offset, flagged.size()), flagged.size()));
        var flaggedIds = flagged.stream().map(Candidate::extensionId).toList();
        candidates.addAll(
                sharedDisplayNameQuery(dsl, flaggedIds, afterId, limit - candidates.size())
                        .fetch(record -> new Candidate(record.get(0, Long.class), null)));
        return candidates;
    }

    /**
     * Walk the check type's failures newest first, a growing chunk at a time, until {@code needed}
     * flagged extensions are found or the failures run out, in which case the list holds all of them.
     *
     * @return {@code null} when {@code maxSteps} chunks were not enough
     */
    private @Nullable List<Candidate> walkFlagged(String checkType, int needed, int firstChunk, int maxSteps) {
        var flagged = new LinkedHashMap<Long, Candidate>();
        LocalDateTime beforeAt = null;
        Long beforeScanId = null;
        var chunk = firstChunk;
        for (var step = 0; step < maxSteps; step++) {
            var scans = flaggedStepQuery(dsl, checkType, beforeAt, beforeScanId, chunk).fetch();
            if (scans.isEmpty()) {
                return List.copyOf(flagged.values());
            }
            for (var scan : scans) {
                beforeAt = scan.get(0, LocalDateTime.class);
                beforeScanId = scan.get(1, Long.class);
                var extensionId = scan.get(2, Long.class);
                if (extensionId != null) {
                    flagged.putIfAbsent(extensionId, new Candidate(extensionId, beforeAt));
                }
            }
            if (flagged.size() >= needed) {
                return List.copyOf(flagged.values());
            }
            chunk = (int) Math.min(Integer.MAX_VALUE, chunk * 2L);
        }
        return null;
    }

    /**
     * The scans behind the next {@code chunk} failures of the check type older than the given position,
     * newest first, each with the active extension it was for, or {@code null} when there is none: every
     * scan comes back, as the last one is where the next step resumes.
     * <p>
     * The chunk is read off extension_validation_failure_type_detected_idx (V1_87) and deduped by scan
     * before the joins, as one scan records a failure per extension it matched.
     */
    static ResultQuery<? extends Record> flaggedStepQuery(
            DSLContext tx,
            String checkType,
            @Nullable LocalDateTime beforeAt,
            @Nullable Long beforeScanId,
            int chunk
    ) {
        var failure = EXTENSION_VALIDATION_FAILURE;
        var resume = beforeAt == null || beforeScanId == null
                ? DSL.noCondition()
                : DSL.row(failure.DETECTED_AT, failure.SCAN_ID).lt(beforeAt, beforeScanId);
        var failures = DSL.name("failures").fields("detected_at", "scan_id").asMaterialized(
                DSL.select(failure.DETECTED_AT, failure.SCAN_ID)
                        .from(failure)
                        .where(failure.VALIDATION_TYPE.eq(checkType))
                        .and(resume)
                        .orderBy(failure.DETECTED_AT.desc(), failure.SCAN_ID.desc())
                        .limit(chunk));
        var scans = DSL.name("scans").fields("detected_at", "scan_id").as(
                DSL.selectDistinct(failures.field("detected_at"), failures.field("scan_id")).from(failures));
        var detectedAt = scans.field("detected_at", LocalDateTime.class);
        var scanId = scans.field("scan_id", Long.class);

        return tx.with(failures)
                .with(scans)
                .select(detectedAt, scanId, EXTENSION.ID)
                .from(scans)
                .join(EXTENSION_SCAN).on(EXTENSION_SCAN.ID.eq(scanId))
                .leftJoin(NAMESPACE).on(namespaceOfScan())
                .leftJoin(EXTENSION).on(extensionOfScan())
                .orderBy(detectedAt.desc(), scanId.desc());
    }

    /**
     * Every active extension the check type flagged, in the order {@link #walkFlagged} finds them: by the
     * scan that flagged each last, newest first.
     */
    static ResultQuery<? extends Record> allFlaggedQuery(DSLContext tx, String checkType) {
        var failure = EXTENSION_VALIDATION_FAILURE;
        var scans = DSL.name("scans").fields("detected_at", "scan_id").as(
                DSL.selectDistinct(failure.DETECTED_AT, failure.SCAN_ID)
                        .from(failure)
                        .where(failure.VALIDATION_TYPE.eq(checkType)));
        var detectedAt = scans.field("detected_at", LocalDateTime.class);
        var scanId = scans.field("scan_id", Long.class);

        var latest = DSL.name("latest").fields("extension_id", "detected_at", "scan_id").as(
                DSL.selectDistinct(EXTENSION.ID, detectedAt, scanId)
                        .on(EXTENSION.ID)
                        .from(scans)
                        .join(EXTENSION_SCAN).on(EXTENSION_SCAN.ID.eq(scanId))
                        .join(NAMESPACE).on(namespaceOfScan())
                        .join(EXTENSION).on(extensionOfScan())
                        .orderBy(EXTENSION.ID, detectedAt.desc(), scanId.desc()));
        var latestAt = latest.field("detected_at", LocalDateTime.class);
        return tx.with(scans)
                .with(latest)
                .select(latest.field("extension_id", Long.class), latestAt)
                .from(latest)
                .orderBy(latestAt.desc(), latest.field("scan_id", Long.class).desc());
    }

    // Scans keep the names as published; upper() is what the unique indexes on both tables are built on.

    private static Condition namespaceOfScan() {
        return DSL.upper(NAMESPACE.NAME).eq(DSL.upper(EXTENSION_SCAN.NAMESPACE_NAME));
    }

    private static Condition extensionOfScan() {
        return EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID)
                .and(DSL.upper(EXTENSION.NAME).eq(DSL.upper(EXTENSION_SCAN.EXTENSION_NAME)))
                .and(EXTENSION.ACTIVE.isTrue());
    }

    /**
     * The active extensions outside {@code excludedIds} whose display name another namespace's active
     * extension shares once normalized, by id, after {@code afterId} when given. One pass over the active
     * versions normalizes each name once and dedupes it per extension before grouping.
     */
    static ResultQuery<? extends Record> sharedDisplayNameQuery(
            DSLContext tx,
            Collection<Long> excludedIds,
            @Nullable Long afterId,
            int limit
    ) {
        var names = DSL.name("names").fields("extension_id", "namespace_id", "normalized").as(
                DSL.selectDistinct(
                        EXTENSION_VERSION.EXTENSION_ID,
                        EXTENSION.NAMESPACE_ID,
                        normalizedDisplayName(EXTENSION_VERSION.DISPLAY_NAME))
                        .from(EXTENSION_VERSION)
                        .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                        .where(EXTENSION_VERSION.ACTIVE.isTrue())
                        .and(EXTENSION.ACTIVE.isTrue())
                        .and(EXTENSION_VERSION.DISPLAY_NAME.isNotNull()));
        var namesExtensionId = names.field("extension_id", Long.class);
        var namesNamespaceId = names.field("namespace_id", Long.class);
        var namesNormalized = names.field("normalized", String.class);

        var shared = DSL.name("shared").fields("normalized").as(
                DSL.select(namesNormalized)
                        .from(names)
                        .where(namesNormalized.ne(""))
                        .groupBy(namesNormalized)
                        .having(DSL.countDistinct(namesNamespaceId).gt(1)));
        var sharedNormalized = shared.field("normalized", String.class);

        // One array parameter rather than a bind per id, and NOT IN over a subquery hashes it once, where
        // <> ALL would compare every row against every element.
        var excluded = DSL.select(
                DSL.field(
                        "unnest({0})",
                        Long.class,
                        DSL.val(excludedIds.toArray(Long[]::new), SQLDataType.BIGINT.array())));
        return tx.with(names)
                .with(shared)
                .selectDistinct(namesExtensionId)
                .from(names)
                .join(shared).on(sharedNormalized.eq(namesNormalized))
                .where(namesExtensionId.notIn(excluded))
                .and(afterId == null ? DSL.noCondition() : namesExtensionId.gt(afterId))
                .orderBy(namesExtensionId)
                .limit(limit);
    }

    private Candidate toCandidate(Record record) {
        return new Candidate(record.get(0, Long.class), record.get(1, LocalDateTime.class));
    }

    /** The names of the given extensions, active or not, in no particular order. */
    public List<ExtensionNames> findNames(Collection<Long> extensionIds) {
        if (extensionIds.isEmpty()) {
            return List.of();
        }

        var activeVersions = DSL.count(EXTENSION_VERSION.ID).filterWhere(EXTENSION_VERSION.ACTIVE.isTrue());
        var displayNames = DSL.arrayAggDistinct(EXTENSION_VERSION.DISPLAY_NAME)
                .filterWhere(EXTENSION_VERSION.ACTIVE.isTrue().and(EXTENSION_VERSION.DISPLAY_NAME.isNotNull()));
        return dsl.select(
                EXTENSION.ID,
                NAMESPACE.NAME,
                EXTENSION.NAME,
                EXTENSION.ACTIVE,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.DOWNLOAD_COUNT,
                activeVersions,
                displayNames)
                .from(EXTENSION)
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(EXTENSION_VERSION).on(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID))
                .where(EXTENSION.ID.in(extensionIds))
                .groupBy(
                        EXTENSION.ID,
                        NAMESPACE.NAME,
                        EXTENSION.NAME,
                        EXTENSION.ACTIVE,
                        EXTENSION.PUBLISHED_DATE,
                        EXTENSION.DOWNLOAD_COUNT)
                .fetch(record -> {
                    var names = record.get(displayNames);
                    return new ExtensionNames(
                            record.get(EXTENSION.ID),
                            record.get(NAMESPACE.NAME),
                            record.get(EXTENSION.NAME),
                            record.get(EXTENSION.ACTIVE),
                            record.get(EXTENSION.PUBLISHED_DATE),
                            record.get(EXTENSION.DOWNLOAD_COUNT),
                            record.get(activeVersions),
                            names == null ? List.of() : List.of(names));
                });
    }

    /**
     * Store the allow unless the pair is already allowed, whichever way round, which the unique
     * name_similarity_allow_pair_idx (V1_86) decides even when two admins allow it at once.
     *
     * @return whether the allow was stored
     */
    public boolean insertAllow(NameSimilarityAllow allow) {
        var table = NAME_SIMILARITY_ALLOW;
        return dsl.insertInto(table)
                .set(table.EXTENSION_ID, allow.getExtensionId())
                .set(table.OTHER_EXTENSION_ID, allow.getOtherExtensionId())
                .set(table.FIELD, allow.getField().name())
                .set(table.SIMILARITY, allow.getSimilarity())
                .set(table.EXTENSION_VALUE, allow.getExtensionValue())
                .set(table.OTHER_VALUE, allow.getOtherValue())
                .set(table.REASON, allow.getReason())
                .set(table.DECIDED_BY_ID, allow.getDecidedById())
                .set(table.DECIDED_AT, allow.getDecidedAt())
                .onConflictDoNothing()
                .execute() == 1;
    }

    /**
     * Set the statement timeout, and the similarity threshold unless {@code null}, until the transaction
     * ends, in one round trip. The settings are not put back, so the caller must not share the transaction.
     */
    private static void setLocal(DSLContext tx, Duration statementTimeout, @Nullable String similarityThreshold) {
        var settings = new ArrayList<Field<?>>();
        settings.add(setConfig("statement_timeout", statementTimeout.toMillis() + "ms"));
        if (similarityThreshold != null) {
            settings.add(setConfig("pg_trgm.similarity_threshold", similarityThreshold));
        }
        tx.select(settings).fetch();
    }

    private static Field<String> setConfig(String setting, String value) {
        return DSL.function("set_config", String.class, DSL.inline(setting), DSL.val(value), DSL.inline(true));
    }

    private static void requireTimeout(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("statement timeout must be positive: " + timeout);
        }
    }

    static Field<String> normalizedDisplayName(Field<String> displayName) {
        return DSL.field("lower(btrim({0}, " + DISPLAY_NAME_TRIM_LITERAL + "))", String.class, displayName);
    }

    /** The Java side of {@link #normalizedDisplayName(Field)}, apart from casing. */
    public static String normalizeDisplayName(String displayName) {
        int start = 0;
        int end = displayName.length();
        while (start < end && DISPLAY_NAME_TRIM_CHARS.indexOf(displayName.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && DISPLAY_NAME_TRIM_CHARS.indexOf(displayName.charAt(end - 1)) >= 0) {
            end--;
        }
        return displayName.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private static String toUnicodeEscapeLiteral(String chars) {
        var literal = new StringBuilder("U&'");
        chars.chars().forEach(c -> literal.append(c < 0x80 ? String.valueOf((char) c) : String.format("\\%04X", c)));
        return literal.append('\'').toString();
    }

    private static Field<Float> similarity(Field<String> first, Field<String> second) {
        return DSL.function("similarity", Float.class, first, second);
    }

    private static Condition trigramMatch(Field<String> indexed, Field<String> probed) {
        return DSL.condition("{0} % {1}", indexed, probed);
    }

    private static Match toMatch(Record record, NameSimilarityField field, String probeValue) {
        return new Match(
                record.get(EXTENSION.ID),
                record.get(NAMESPACE.NAME),
                record.get(EXTENSION.NAME),
                field,
                record.get(3, String.class),
                probeValue,
                record.get(4, Float.class),
                record.get(5, Boolean.class),
                record.get(6, Float.class),
                record.get(EXTENSION.PUBLISHED_DATE),
                record.get(EXTENSION.DOWNLOAD_COUNT));
    }

    /**
     * What to look for: the names of one extension, which need not exist yet. Extensions in the same
     * namespace never match. A normalized value shorter than {@code minFuzzyLength} only matches exactly,
     * as trigram scores of very short strings say little.
     *
     * @param onlyExtensionIds     when not empty, the only extensions that may match
     * @param excludedExtensionIds extensions that never match
     * @param earliestFirst        whether the limit keeps the extensions published first, in the order of
     *                             published date (unknown first) and then id; otherwise which it keeps is
     *                             unspecified, which spares sorting every hit
     */
    public record Probe(
            String namespaceName,
            String extensionName,
            Collection<String> displayNames,
            float nameThreshold,
            float displayNameThreshold,
            int minFuzzyLength,
            int limit,
            Duration statementTimeout,
            Set<Long> onlyExtensionIds,
            Set<Long> excludedExtensionIds,
            boolean earliestFirst
    ) {
        public Probe {
            requireThreshold(nameThreshold);
            requireThreshold(displayNameThreshold);
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
            requireTimeout(statementTimeout);
            onlyExtensionIds = Set.copyOf(onlyExtensionIds);
            excludedExtensionIds = Set.copyOf(excludedExtensionIds);
        }

        public Probe(
                String namespaceName,
                String extensionName,
                Collection<String> displayNames,
                float nameThreshold,
                float displayNameThreshold,
                int minFuzzyLength,
                int limit,
                Duration statementTimeout
        ) {
            this(
                    namespaceName,
                    extensionName,
                    displayNames,
                    nameThreshold,
                    displayNameThreshold,
                    minFuzzyLength,
                    limit,
                    statementTimeout,
                    Set.of(),
                    Set.of(),
                    false);
        }

        /** This probe, matching only the given extensions. */
        public Probe onlyExtensions(Collection<Long> extensionIds) {
            if (extensionIds.isEmpty()) {
                throw new IllegalArgumentException("extensionIds must not be empty");
            }
            return new Probe(
                    namespaceName,
                    extensionName,
                    displayNames,
                    nameThreshold,
                    displayNameThreshold,
                    minFuzzyLength,
                    limit,
                    statementTimeout,
                    Set.copyOf(extensionIds),
                    excludedExtensionIds,
                    earliestFirst);
        }

        /** This probe, never matching the given extensions. */
        public Probe excluding(Collection<Long> extensionIds) {
            return new Probe(
                    namespaceName,
                    extensionName,
                    displayNames,
                    nameThreshold,
                    displayNameThreshold,
                    minFuzzyLength,
                    limit,
                    statementTimeout,
                    onlyExtensionIds,
                    Set.copyOf(extensionIds),
                    earliestFirst);
        }

        /** This probe, keeping the extensions published first when the limit cuts matches off. */
        public Probe keepingEarliestFirst() {
            return new Probe(
                    namespaceName,
                    extensionName,
                    displayNames,
                    nameThreshold,
                    displayNameThreshold,
                    minFuzzyLength,
                    limit,
                    statementTimeout,
                    onlyExtensionIds,
                    excludedExtensionIds,
                    true);
        }

        private static void requireThreshold(float threshold) {
            if (!(threshold > 0 && threshold <= 1)) {
                throw new IllegalArgumentException("similarity threshold must be in (0, 1]: " + threshold);
            }
        }
    }

    /**
     * One active extension that matched a probe on one field.
     *
     * @param value               the matched extension's value of {@code field}, as published
     * @param probeValue          the probed value it matched
     * @param similarity          trigram similarity of the normalized values, from 0 to 1
     * @param exact               whether the normalized values are equal, which a similarity of 1 does
     *                            not imply: trigrams are a set, so repeating a word does not change them
     * @param namespaceSimilarity trigram similarity of the two namespace names
     */
    public record Match(
            long extensionId,
            String namespaceName,
            String extensionName,
            NameSimilarityField field,
            String value,
            String probeValue,
            float similarity,
            boolean exact,
            float namespaceSimilarity,
            LocalDateTime publishedDate,
            int downloadCount
    ) {}

    /**
     * An active extension worth looking up again.
     *
     * @param flaggedAt when a check last flagged one of its versions, {@code null} for an extension that
     *                  only shares a display name with another namespace's
     */
    public record Candidate(long extensionId, @Nullable LocalDateTime flaggedAt) {}

    /**
     * The names of an extension.
     *
     * @param displayNames the distinct display names of its active versions
     */
    public record ExtensionNames(
            long id,
            String namespaceName,
            String name,
            boolean active,
            @Nullable LocalDateTime publishedDate,
            int downloadCount,
            int activeVersionCount,
            List<String> displayNames
    ) {}
}
