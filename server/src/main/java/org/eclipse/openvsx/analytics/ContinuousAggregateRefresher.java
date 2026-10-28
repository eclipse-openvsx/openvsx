/******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
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
package org.eclipse.openvsx.analytics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refreshes every registered {@link ContinuousAggregateSet}. Must never be {@code @Transactional}:
 * {@code refresh_continuous_aggregate} cannot run inside a transaction, hence the autocommit
 * time-series context.
 */
public class ContinuousAggregateRefresher {

    private final Logger logger = LoggerFactory.getLogger(ContinuousAggregateRefresher.class);

    private final DSLContext dsl;
    private final List<ContinuousAggregateSet> sets;

    public ContinuousAggregateRefresher(DSLContext dsl, List<ContinuousAggregateSet> sets) {
        this.dsl = dsl;
        this.sets = sets;
    }

    /** Materializes each aggregate over the day-aligned range covering [from, to]; a null bound is open. */
    public void refresh(@Nullable Instant from, @Nullable Instant to) {
        var start = from == null ? null : OffsetDateTime.ofInstant(from.truncatedTo(ChronoUnit.DAYS), ZoneOffset.UTC);
        // include the whole day that 'to' falls in
        var end = to == null
                ? null
                : OffsetDateTime.ofInstant(to.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS), ZoneOffset.UTC);
        var names = aggregateNames();
        for (var name : names) {
            dsl.execute(
                    "call refresh_continuous_aggregate({0}, {1}, {2})",
                    DSL.inline(name),
                    DSL.val(start, SQLDataType.TIMESTAMPWITHTIMEZONE),
                    DSL.val(end, SQLDataType.TIMESTAMPWITHTIMEZONE));
        }
        logger.debug("refreshed {} over [{}, {})", names, start, end);
    }

    private Set<String> aggregateNames() {
        var names = new LinkedHashSet<String>();
        for (var set : sets) {
            names.addAll(set.aggregateNames());
        }
        return names;
    }
}
