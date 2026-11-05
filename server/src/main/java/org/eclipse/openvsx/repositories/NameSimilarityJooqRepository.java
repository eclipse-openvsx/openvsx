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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import jakarta.transaction.Transactional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.NameSimilarityField;

import static org.eclipse.openvsx.jooq.Tables.EXTENSION;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION_VERSION;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;

/**
 * Trigram lookups of the active extensions whose name or display name matches or resembles a given
 * one, for the NAME_SIMILARITY publish check and the name squatting admin dashboard.
 * <p>
 * Results come back in no particular order: ordering by similarity in SQL makes the planner give up
 * the trigram indexes, so callers sort what they get.
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

    private final DSLContext dsl;

    public NameSimilarityJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * What to look for: the names of one extension, which need not exist yet. Extensions in the same
     * namespace never match. A normalized value shorter than {@code minFuzzyLength} only matches exactly,
     * as trigram scores of very short strings say little.
     */
    public record Probe(
            String namespaceName,
            String extensionName,
            Collection<String> displayNames,
            float nameThreshold,
            float displayNameThreshold,
            int minFuzzyLength,
            int limit
    ) {
        public Probe {
            requireThreshold(nameThreshold);
            requireThreshold(displayNameThreshold);
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
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

    /** Active extensions in more than one namespace whose display names are equal once normalized. */
    public record DisplayNameGroup(String normalizedDisplayName, List<Long> extensionIds) {}

    /**
     * Find the active extensions outside the probe's namespace that match it, up to {@code limit} per
     * field and display name. Which matches are cut off beyond the limit is unspecified.
     * <p>
     * Transactional because the similarity threshold is a setting local to the transaction.
     */
    @Transactional
    public List<Match> findSimilar(Probe probe) {
        var displayNames = new LinkedHashSet<String>();
        for (var displayName : probe.displayNames()) {
            if (displayName != null && !displayName.isBlank()) {
                displayNames.add(displayName);
            }
        }

        applyThreshold(dsl, probe);

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

    /** The % operator compares against this setting; set_config's local scope keeps it to the transaction. */
    static void applyThreshold(DSLContext tx, Probe probe) {
        var threshold = Math.min(probe.nameThreshold(), probe.displayNameThreshold());
        tx.select(
                DSL.function(
                        "set_config",
                        String.class,
                        DSL.inline("pg_trgm.similarity_threshold"),
                        DSL.val(Float.toString(threshold)),
                        DSL.inline(true)))
                .fetch();
    }

    // The column layout of namesQuery and displayNamesQuery is the same, and toMatch relies on it.

    static ResultQuery<? extends Record> namesQuery(DSLContext tx, Probe probe) {
        var probed = DSL.lower(DSL.val(probe.extensionName()));
        var candidate = DSL.lower(EXTENSION.NAME);
        var similarity = similarity(candidate, probed);
        var exact = candidate.eq(probed);

        Condition matches = probe.extensionName().length() < probe.minFuzzyLength()
                ? exact
                : trigramMatch(candidate, probed).and(similarity.ge(probe.nameThreshold()));

        return tx.select(
                EXTENSION.ID,
                NAMESPACE.NAME,
                EXTENSION.NAME,
                EXTENSION.NAME.as("matched_value"),
                similarity,
                DSL.field(exact),
                namespaceSimilarity(probe),
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.DOWNLOAD_COUNT)
                .from(EXTENSION)
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(EXTENSION.ACTIVE.isTrue())
                .and(matches)
                .and(DSL.lower(NAMESPACE.NAME).ne(DSL.lower(DSL.val(probe.namespaceName()))))
                .limit(probe.limit());
    }

    static ResultQuery<? extends Record> displayNamesQuery(DSLContext tx, Probe probe, String displayName) {
        var probed = normalizedDisplayName(DSL.val(displayName));
        var candidate = normalizedDisplayName(EXTENSION_VERSION.DISPLAY_NAME);
        var similarity = similarity(candidate, probed);

        Condition matches = normalizeDisplayName(displayName).length() < probe.minFuzzyLength()
                ? candidate.eq(probed)
                : trigramMatch(candidate, probed).and(similarity.ge(probe.displayNameThreshold()));

        // Versions of one extension share their display name, so they collapse to one row per value.
        return tx.select(
                EXTENSION.ID,
                NAMESPACE.NAME,
                EXTENSION.NAME,
                DSL.min(EXTENSION_VERSION.DISPLAY_NAME).as("matched_value"),
                similarity,
                DSL.field(candidate.eq(probed)),
                namespaceSimilarity(probe),
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.DOWNLOAD_COUNT)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(EXTENSION_VERSION.ACTIVE.isTrue())
                .and(EXTENSION.ACTIVE.isTrue())
                .and(matches)
                .and(DSL.lower(NAMESPACE.NAME).ne(DSL.lower(DSL.val(probe.namespaceName()))))
                .groupBy(
                        EXTENSION.ID,
                        NAMESPACE.NAME,
                        EXTENSION.NAME,
                        candidate,
                        EXTENSION.PUBLISHED_DATE,
                        EXTENSION.DOWNLOAD_COUNT)
                .limit(probe.limit());
    }

    private static Field<Float> namespaceSimilarity(Probe probe) {
        return similarity(DSL.lower(NAMESPACE.NAME), DSL.lower(DSL.val(probe.namespaceName())));
    }

    /**
     * Find the display names that active extensions in more than one namespace share once normalized,
     * whether or not they were published before the NAME_SIMILARITY check existed.
     */
    public List<DisplayNameGroup> findSharedDisplayNames() {
        var normalized = normalizedDisplayName(EXTENSION_VERSION.DISPLAY_NAME);
        var extensionIds = DSL.arrayAggDistinct(EXTENSION.ID);
        return dsl.select(normalized, extensionIds)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .where(EXTENSION_VERSION.ACTIVE.isTrue())
                .and(EXTENSION.ACTIVE.isTrue())
                .and(normalized.ne(""))
                .groupBy(normalized)
                .having(DSL.countDistinct(EXTENSION.NAMESPACE_ID).gt(1))
                .fetch(record -> new DisplayNameGroup(record.get(normalized), List.of(record.get(extensionIds))));
    }

    static Field<String> normalizedDisplayName(Field<String> displayName) {
        return DSL.field("lower(btrim({0}, " + DISPLAY_NAME_TRIM_LITERAL + "))", String.class, displayName);
    }

    /** The Java side of {@link #normalizedDisplayName(Field)}, apart from casing. */
    static String normalizeDisplayName(String displayName) {
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
}
