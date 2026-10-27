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

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.analytics.AnalyticsUnavailableException;
import org.eclipse.openvsx.analytics.DownloadEvent;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.settings.ReadOnlySetting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A log file is cleaned up only once its analytics events are stored, so that an outage of the
 * analytics database loses nothing: the file stays, marked pending, and a later run stores the events.
 */
class DownloadIngestionRunnerTest {

    private static final String STORAGE = FileResource.STORAGE_AWS;
    private static final String NAME = "access.log.gz";

    private final ReadOnlySetting readOnly = Mockito.mock(ReadOnlySetting.class);
    private final DownloadIngestionProcessor processor = Mockito.mock(DownloadIngestionProcessor.class);
    private final DownloadRecordSource source = Mockito.mock(DownloadRecordSource.class);
    private final List<RawDownloadRecord> records = List
            .of(new RawDownloadRecord(Instant.parse("2026-07-01T14:00:00Z"), "A.B-1.0.0.VSIX", null, null, null));

    private DownloadIngestionRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        runner = new DownloadIngestionRunner(readOnly, processor);
        when(source.getStorageType()).thenReturn(STORAGE);
        when(source.listBatches()).thenAnswer(invocation -> List.of(List.of(NAME)).iterator());
        when(source.read(NAME)).thenReturn(records);
        when(processor.succeededIngestions(eq(STORAGE), anyList())).thenReturn(List.of());
        when(processor.failedIngestions(eq(STORAGE), anyList())).thenReturn(List.of());
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of());
        when(processor.isAnalyticsEnabled()).thenReturn(true);
        when(processor.isAnalyticsAvailable()).thenReturn(true);
    }

    @Test
    void testFileIsCleanedUpOnceItsEventsAreStored() {
        givenProcessedFileWithEvents();
        when(processor.saveEvents(anyList())).thenReturn(true);

        runner.run(source);

        var order = Mockito.inOrder(processor, source);
        order.verify(processor).markAnalyticsDone(STORAGE, NAME);
        order.verify(source).finish(NAME);
    }

    @Test
    void testFileIsKeptWhenItsEventsCouldNotBeStored() {
        givenProcessedFileWithEvents();
        when(processor.saveEvents(anyList())).thenReturn(false);

        runner.run(source);

        verify(processor, never()).markAnalyticsDone(anyString(), anyString());
        verify(source, never()).finish(anyString());
        // the counters are committed, so it is not recorded as a failure either
        verify(processor, never()).persistIngestion(anyString(), anyString(), any(), anyInt(), Mockito.eq(false));
    }

    @Test
    void testFileIsKeptWhenMarkingItStoredFails() {
        givenProcessedFileWithEvents();
        when(processor.saveEvents(anyList())).thenReturn(true);
        doThrow(new IllegalStateException("registry unavailable")).when(processor)
                .markAnalyticsDone(STORAGE, NAME);

        runner.run(source);

        verify(source, never()).finish(anyString());
    }

    @Test
    void testPendingFileIsNotCountedAgainButItsEventsAreStored() throws Exception {
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of(NAME));

        runner.run(source);

        verify(processor, never()).process(anyString(), anyString(), any(), anyInt(), anyList());
        var order = Mockito.inOrder(processor, source);
        order.verify(processor).backfill(STORAGE, records);
        order.verify(processor).markAnalyticsDone(STORAGE, NAME);
        order.verify(source).finish(NAME);
    }

    @Test
    void testPendingFileStaysWhileAnalyticsIsUnavailable() throws Exception {
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of(NAME));
        when(processor.isAnalyticsAvailable()).thenReturn(false);

        runner.run(source);

        verify(source, never()).read(anyString());
        verify(source, never()).finish(anyString());
        verify(processor, never()).process(anyString(), anyString(), any(), anyInt(), anyList());
    }

    @Test
    void testPendingFileStaysWhenAnalyticsBecomesUnavailableDuringTheReplay() throws Exception {
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of(NAME));
        when(processor.backfill(STORAGE, records)).thenThrow(new AnalyticsUnavailableException());

        runner.run(source);

        verify(processor, never()).markAnalyticsDone(anyString(), anyString());
        verify(source, never()).finish(anyString());
    }

    @Test
    void testPendingFileIsReleasedWhenAnalyticsWasSwitchedOff() throws Exception {
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of(NAME));
        when(processor.isAnalyticsEnabled()).thenReturn(false);

        runner.run(source);

        verify(processor, never()).backfill(anyString(), anyList());
        verify(processor).markAnalyticsDone(STORAGE, NAME);
        verify(source).finish(NAME);
    }

    @Test
    void testCleanupOfPendingFilesStopsWhenTheRegistryGoesReadOnly() throws Exception {
        when(processor.pendingAnalyticsIngestions(eq(STORAGE), anyList())).thenReturn(List.of(NAME));
        when(processor.isAnalyticsEnabled()).thenReturn(false);
        when(readOnly.isEnabled()).thenReturn(false, true);

        runner.run(source);

        verify(processor, never()).markAnalyticsDone(anyString(), anyString());
        verify(source, never()).finish(anyString());
    }

    private void givenProcessedFileWithEvents() {
        var event = new DownloadEvent(
                Instant.parse("2026-07-01T14:00:00Z"),
                1L,
                2L,
                "a",
                "b",
                "1.0.0",
                "universal",
                null,
                null,
                null,
                1);
        when(processor.process(eq(STORAGE), eq(NAME), any(), anyInt(), eq(records)))
                .thenReturn(new DownloadIngestionProcessor.ProcessedFile(List.of(), List.of(event)));
    }
}
