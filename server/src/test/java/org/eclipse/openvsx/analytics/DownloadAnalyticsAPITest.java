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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DownloadAnalyticsAPITest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

    private final DownloadAnalyticsService service = Mockito.mock(DownloadAnalyticsService.class);
    private final RepositoryService repositories = Mockito.mock(RepositoryService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new DownloadAnalyticsAPI(
                service,
                repositories,
                Clock.fixed(NOW, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        var extension = new Extension();
        extension.setId(42L);
        Mockito.when(repositories.findActiveExtension("bar", "foo")).thenReturn(extension);
    }

    @Test
    void testResponseShape() throws Exception {
        Mockito.when(service.getSeries(any())).thenReturn(
                List.of(
                        new DownloadSeriesPoint(Instant.parse("2026-07-01T00:00:00Z"), null, 4321, false),
                        new DownloadSeriesPoint(Instant.parse("2026-07-02T00:00:00Z"), null, 10, true)));

        mockMvc.perform(get("/api/foo/bar/analytics/downloads"))
                .andExpect(status().isOk())
                .andExpect(
                        content().json(
                                "{\"points\":[{\"t\":\"2026-07-01\",\"count\":4321},{\"t\":\"2026-07-02\",\"count\":10}]}",
                                true));
    }

    @Test
    void testUnreachableAnalyticsDatabaseIsServiceUnavailable() throws Exception {
        Mockito.when(service.getSeries(any())).thenThrow(new AnalyticsUnavailableException());

        mockMvc.perform(get("/api/foo/bar/analytics/downloads"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void testSeriesIsPubliclyCacheable() throws Exception {
        Mockito.when(service.getSeries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/foo/bar/analytics/downloads"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=600, public"));
    }

    @Test
    void testWindowIsTheLast52WeeksOfDays() throws Exception {
        Mockito.when(service.getSeries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/foo/bar/analytics/downloads")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(DownloadSeriesRequest.class);
        Mockito.verify(service).getSeries(captor.capture());
        var request = captor.getValue();
        // now is 2026-07-15T10:00Z: the window ends after today (partial) and spans 364 days
        assertEquals(List.of(42L), request.extensionIds());
        assertEquals(Instant.parse("2026-07-16T00:00:00Z"), request.to());
        assertEquals(Instant.parse("2025-07-17T00:00:00Z"), request.from());
        assertEquals(DownloadSeriesInterval.DAY, request.interval());
        assertEquals(DownloadSeriesGroupBy.NONE, request.groupBy());
    }

    @Test
    void testQueryParametersDoNotChangeTheWindow() throws Exception {
        Mockito.when(service.getSeries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/foo/bar/analytics/downloads")).andExpect(status().isOk());
        mockMvc.perform(get("/api/foo/bar/analytics/downloads?from=2000-01-01&to=2026-07-01&interval=month"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(DownloadSeriesRequest.class);
        Mockito.verify(service, Mockito.times(2)).getSeries(captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }

    @Test
    void testUnknownExtensionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/foo/unknown/analytics/downloads")).andExpect(status().isNotFound());
    }
}
