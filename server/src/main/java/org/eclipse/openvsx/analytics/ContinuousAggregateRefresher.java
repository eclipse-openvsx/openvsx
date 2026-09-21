/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refreshes every registered {@link ContinuousAggregateSet}. Must never be {@code @Transactional}:
 * {@code refresh_continuous_aggregate} cannot run inside a transaction, hence the autocommit
 * time-series context.
 */
public class ContinuousAggregateRefresher {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    private final Logger logger = LoggerFactory.getLogger(ContinuousAggregateRefresher.class);

    private final DSLContext dsl;
    private final List<ContinuousAggregateSet> sets;

    public ContinuousAggregateRefresher(DSLContext dsl, List<ContinuousAggregateSet> sets) {
        this.dsl = dsl;
        this.sets = sets;
    }

    /** Materializes each aggregate over the day-aligned range covering [from, to]; null means all. */
    public void refresh(@Nullable Instant from, @Nullable Instant to) {
        var start = from == null ? null : OffsetDateTime.ofInstant(from.truncatedTo(ChronoUnit.DAYS), ZoneOffset.UTC);
        // include the whole day that 'to' falls in
        var end = to == null
                ? null
                : OffsetDateTime.ofInstant(to.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS), ZoneOffset.UTC);
        for (var name : aggregateNames()) {
            if (start == null || end == null) {
                dsl.execute("call refresh_continuous_aggregate('" + name + "', null, null)");
            } else {
                // the window arguments are pseudo-type "any", so Postgres cannot infer the type of a
                // bare bind parameter and defaults it to varchar; the cast pins it to timestamptz
                dsl.execute(
                        "call refresh_continuous_aggregate('" + name + "', ?::timestamptz, ?::timestamptz)",
                        start,
                        end);
            }
            logger.info("refreshed continuous aggregate {} over [{}, {})", name, start, end);
        }
    }

    private Set<String> aggregateNames() {
        var names = new LinkedHashSet<String>();
        for (var set : sets) {
            for (var name : set.aggregateNames()) {
                if (!SAFE_NAME.matcher(name).matches()) {
                    throw new IllegalArgumentException("not a valid continuous aggregate name: '" + name + "'");
                }
                names.add(name);
            }
        }
        return names;
    }
}
