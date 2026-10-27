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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;

/**
 * Minimal REST surface over {@link DownloadAnalyticsService}. The bean only exists when download
 * analytics is enabled, so the path stays unmapped (404) otherwise.
 */
@RestController
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
public class DownloadAnalyticsAPI {

    /** Fixed rather than chosen by the caller, so every request for an extension hits the same cache entries. */
    private static final int WINDOW_WEEKS = 52;
    private static final int RETRY_AFTER_SECONDS = 30;

    private final DownloadAnalyticsService service;
    private final RepositoryService repositories;
    private final Clock clock;

    @Autowired
    public DownloadAnalyticsAPI(DownloadAnalyticsService service, RepositoryService repositories) {
        this(service, repositories, Clock.systemUTC());
    }

    DownloadAnalyticsAPI(
            DownloadAnalyticsService service,
            RepositoryService repositories,
            Clock clock
    ) {
        this.service = service;
        this.repositories = repositories;
        this.clock = clock;
    }

    @GetMapping(path = "/api/{namespace}/{extension}/analytics/downloads", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(summary = "Provides the download counts of an extension over time")
    @ApiResponse(
        responseCode = "200",
        description = "The daily downloads of the last 52 weeks up to and including today, as a dense, zero-filled "
                + "series in JSON format; the last point is still partial"
    )
    @ApiResponse(
        responseCode = "404",
        description = "The specified extension could not be found, or download analytics is disabled",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "503",
        description = "The analytics database is currently unreachable; the rest of the registry is unaffected",
        content = @Content()
    )
    public ResponseEntity<DownloadSeriesJson> getDownloads(
            @PathVariable
            @Parameter(description = "Extension namespace", example = "redhat") String namespace,
            @PathVariable
            @Parameter(description = "Extension name", example = "java") String extension
    ) {
        var extensionEntity = repositories.findActiveExtension(extension, namespace);
        if (extensionEntity == null) {
            throw new NotFoundException();
        }

        // `to` is exclusive, so tomorrow includes today's (still-accruing) bucket.
        var to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1);
        var request = DownloadSeriesRequest.of(
                extensionEntity.getId(),
                to.minusWeeks(WINDOW_WEEKS).atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant(),
                DownloadSeriesInterval.DAY);
        List<DownloadSeriesPoint> series;
        try {
            series = service.getSeries(request);
        } catch (AnalyticsUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS))
                    .build();
        }

        var points = series.stream()
                .map(
                        point -> new DownloadSeriesJson.DownloadSeriesPointJson(
                                LocalDate.ofInstant(point.bucketStart(), ZoneOffset.UTC).toString(),
                                point.count()))
                .toList();
        // Aggregate, non-personal data that is identical for every caller, so it is publicly
        // cacheable. Without an explicit value Spring Security defaults the response to no-store.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(new DownloadSeriesJson(points));
    }
}
