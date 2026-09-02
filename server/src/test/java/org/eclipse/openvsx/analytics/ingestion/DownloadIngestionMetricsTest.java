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
package org.eclipse.openvsx.analytics.ingestion;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.repositories.RepositoryService;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadIngestionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RepositoryService repositories = Mockito.mock(RepositoryService.class);
    private final DownloadIngestionMetrics metrics = new DownloadIngestionMetrics(registry, repositories);

    @Test
    void testParseCounters() {
        metrics.recordParsedLines(100, 3);
        metrics.recordParsedLines(50, 0);

        assertEquals(150, registry.counter(DownloadIngestionMetrics.LINES_METRIC).count());
        assertEquals(3, registry.counter(DownloadIngestionMetrics.SKIPPED_LINES_METRIC).count());
    }

    @Test
    void testLoadVolumeCounters() {
        metrics.recordLoaded(4, 25);
        metrics.recordLoaded(1, 5);

        assertEquals(5, registry.counter(DownloadIngestionMetrics.EVENTS_METRIC).count());
        assertEquals(30, registry.counter(DownloadIngestionMetrics.DOWNLOADS_METRIC).count());
    }

    @Test
    void testFailedEventsCounter() {
        metrics.recordFailedEvents(3);
        metrics.recordFailedEvents(1);

        assertEquals(4, registry.counter(DownloadIngestionMetrics.FAILED_EVENTS_METRIC).count());
    }

    @Test
    void testExtractLagTimer() {
        metrics.recordExtractLag(Duration.ofMinutes(10));

        var timer = registry.timer(DownloadIngestionMetrics.EXTRACT_LAG_METRIC);
        assertEquals(1, timer.count());
        assertEquals(600, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void testDeadLetterDepthGauge() {
        Mockito.when(repositories.countFailedDownloadIngestions()).thenReturn(7L);

        var gauge = registry.get(DownloadIngestionMetrics.DEAD_LETTER_METRIC).gauge();
        assertEquals(7, gauge.value());
    }
}
