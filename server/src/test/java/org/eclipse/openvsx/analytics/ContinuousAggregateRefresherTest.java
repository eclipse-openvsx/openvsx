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
package org.eclipse.openvsx.analytics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ContinuousAggregateRefresherTest {

    private final DSLContext dsl = Mockito.mock(DSLContext.class);

    @Test
    void refreshesOverTheDayAlignedWindowCoveringTheRange() {
        var refresher = refresher(() -> List.of("download_stats_daily"));

        refresher.refresh(Instant.parse("2026-02-09T13:45:00Z"), Instant.parse("2026-02-10T01:00:00Z"));

        // start truncated to the day, end extended to include the whole day 'to' falls in
        verify(dsl).execute(
                "call refresh_continuous_aggregate('download_stats_daily', ?::timestamptz, ?::timestamptz)",
                OffsetDateTime.parse("2026-02-09T00:00Z"),
                OffsetDateTime.parse("2026-02-11T00:00Z"));
        verifyNoMoreInteractions(dsl);
    }

    @Test
    void refreshesTheWholeAggregateWhenTheRangeIsOpen() {
        var refresher = refresher(() -> List.of("download_stats_daily"));

        refresher.refresh(null, null);
        refresher.refresh(Instant.parse("2026-02-09T13:45:00Z"), null);

        verify(dsl, Mockito.times(2)).execute("call refresh_continuous_aggregate('download_stats_daily', null, null)");
        verifyNoMoreInteractions(dsl);
    }

    @Test
    void refreshesEachNameOnceAcrossSets() {
        var refresher = refresher(() -> List.of("agg_one", "agg_two"), () -> List.of("agg_one"));

        refresher.refresh(null, null);

        verify(dsl).execute("call refresh_continuous_aggregate('agg_one', null, null)");
        verify(dsl).execute("call refresh_continuous_aggregate('agg_two', null, null)");
        verifyNoMoreInteractions(dsl);
    }

    @Test
    void rejectsUnsafeAggregateNamesBeforeRefreshingAnything() {
        var refresher = refresher(() -> List.of("agg_one"), () -> List.of("agg'); drop table download_event; --"));

        assertThrows(IllegalArgumentException.class, () -> refresher.refresh(null, null));

        verifyNoInteractions(dsl);
    }

    private ContinuousAggregateRefresher refresher(ContinuousAggregateSet... sets) {
        return new ContinuousAggregateRefresher(dsl, List.of(sets));
    }
}
