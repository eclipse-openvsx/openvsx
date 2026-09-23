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

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.analytics.ContinuousAggregateRefresher;
import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionProcessor.BackfillResult;
import org.eclipse.openvsx.analytics.ingestion.aws.DownloadLogParser;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the backfill endpoint with the same access-log fixture the parser tests use, so an upload
 * exercises the real parse path; only the registry-side collaborators are mocked.
 */
class DownloadBackfillAPITest {

    private final AdminService admins = Mockito.mock(AdminService.class);
    private final DownloadIngestionProcessor processor = Mockito.mock(DownloadIngestionProcessor.class);
    private final ContinuousAggregateRefresher refresher = Mockito.mock(ContinuousAggregateRefresher.class);
    private final StorageUtilService storageUtil = Mockito.mock(StorageUtilService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var parser = new DownloadLogParser(Mockito.mock(DownloadIngestionMetrics.class));
        var controller = new DownloadBackfillAPI(admins, parser, processor, refresher, storageUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(storageUtil.getActiveStorageType()).thenReturn(FileResource.STORAGE_AWS);
    }

    @Test
    void testUploadedLogIsParsedProcessedAndRefreshed() throws Exception {
        var from = Instant.parse("2025-12-03T13:00:00Z");
        var to = Instant.parse("2026-02-09T00:00:00Z");
        when(processor.backfill(eq(FileResource.STORAGE_AWS), anyList()))
                .thenReturn(new BackfillResult(2, 3, 2, 1, from, to));

        mockMvc.perform(
                post("/admin/api/analytics/downloads/backfill?token=super&fileDate=2026-02-09")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fixtureBytes()))
                .andExpect(status().isOk())
                .andExpect(
                        content().json(
                                "{\"events\":2,\"downloads\":3,\"extensions\":2,\"unresolvedRecords\":1,"
                                        + "\"from\":\"2025-12-03T13:00:00Z\",\"to\":\"2026-02-09T00:00:00Z\"}",
                                true));

        verify(admins).checkAdminUser("super");

        // the fixture's two download lines reach the processor, resolved against the app's storage
        ArgumentCaptor<List<RawDownloadRecord>> records = ArgumentCaptor.captor();
        verify(processor).backfill(eq(FileResource.STORAGE_AWS), records.capture());
        assertEquals(2, records.getValue().size());
        assertEquals("VSCJAVA.VSCODE-JAVA-PACK-0.30.4.VSIX", records.getValue().get(0).vsixFilename());
        assertEquals(Instant.parse("2025-12-03T13:20:01Z"), records.getValue().get(0).time());
        // the line without its own timestamp is stamped with the fileDate parameter
        assertEquals(Instant.parse("2026-02-09T00:00:00Z"), records.getValue().get(1).time());

        verify(refresher).refresh(from, to);
    }

    @Test
    void testNothingIsRefreshedWhenNoEventsWereWritten() throws Exception {
        when(processor.backfill(eq(FileResource.STORAGE_AWS), anyList()))
                .thenReturn(new BackfillResult(0, 0, 0, 2, null, null));

        mockMvc.perform(
                post("/admin/api/analytics/downloads/backfill")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fixtureBytes()))
                .andExpect(status().isOk())
                .andExpect(
                        content().json(
                                "{\"events\":0,\"downloads\":0,\"extensions\":0,\"unresolvedRecords\":2,"
                                        + "\"from\":null,\"to\":null}",
                                true));

        verifyNoInteractions(refresher);
    }

    @Test
    void testNonAdminIsForbidden() throws Exception {
        Mockito.doThrow(new ErrorResultException("Administration role is required.", HttpStatus.FORBIDDEN))
                .when(admins).checkAdminUser();

        mockMvc.perform(
                post("/admin/api/analytics/downloads/backfill")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fixtureBytes()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(processor, refresher);
    }

    @Test
    void testInvalidFileDateIsBadRequest() throws Exception {
        mockMvc.perform(
                post("/admin/api/analytics/downloads/backfill?fileDate=02/09/2026")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fixtureBytes()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(processor, refresher);
    }

    @Test
    void testUnknownLogFormatIsBadRequest() throws Exception {
        mockMvc.perform(
                post("/admin/api/analytics/downloads/backfill?format=nginx")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fixtureBytes()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(processor, refresher);
    }

    private byte[] fixtureBytes() throws IOException {
        try (var in = DownloadLogParser.class.getResourceAsStream("cloudfront.log")) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }
}
