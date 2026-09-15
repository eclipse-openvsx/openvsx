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
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;

/**
 * Minimal REST surface over {@link DownloadAnalyticsService}. The bean only exists when download
 * analytics is enabled, so the path stays unmapped (404) otherwise.
 */
@RestController
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
public class DownloadAnalyticsAPI {

    private static final int MAX_RANGE_YEARS = 5;

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
        description = "The dense, zero-filled download series is returned in JSON format; the last point may still be partial"
    )
    @ApiResponse(
        responseCode = "400",
        description = "A query parameter is invalid",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "404",
        description = "The specified extension could not be found, or download analytics is disabled",
        content = @Content()
    )
    public ResponseEntity<DownloadSeriesJson> getDownloads(
            @PathVariable
            @Parameter(description = "Extension namespace", example = "redhat") String namespace,
            @PathVariable
            @Parameter(description = "Extension name", example = "java") String extension,
            @RequestParam(required = false)
            @Parameter(
                description = "UTC start date (inclusive), defaults to 30 days before 'to' whatever the interval",
                example = "2026-06-16"
            ) String from,
            @RequestParam(required = false)
            @Parameter(
                description = "UTC end date (exclusive), defaults to tomorrow",
                example = "2026-07-16"
            ) String to,
            @RequestParam(defaultValue = "day")
            @Parameter(
                description = "Bucket interval",
                schema = @Schema(type = "string", allowableValues = { "day", "week", "month" }, defaultValue = "day")
            ) String interval
    ) {
        var extensionEntity = repositories.findActiveExtension(extension, namespace);
        if (extensionEntity == null) {
            throw new NotFoundException();
        }

        var request = buildRequest(extensionEntity.getId(), from, to, interval);
        var points = service.getSeries(request).stream()
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

    private DownloadSeriesRequest buildRequest(long extensionId, String from, String to, String interval) {
        DownloadSeriesInterval seriesInterval;
        try {
            seriesInterval = DownloadSeriesInterval.fromValue(interval);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        var today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        var toDate = parseDate(to, "to", today.plusDays(1));
        var fromDate = parseDate(from, "from", toDate.minusDays(30));
        if (!fromDate.isBefore(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be before 'to'");
        }
        if (fromDate.plusYears(MAX_RANGE_YEARS).isBefore(toDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "the requested range must not exceed " + MAX_RANGE_YEARS + " years");
        }

        return DownloadSeriesRequest.of(
                extensionId,
                fromDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                toDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                seriesInterval);
    }

    private LocalDate parseDate(String value, String name, LocalDate defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "parameter '" + name + "' must be a date in the format yyyy-mm-dd");
        }
    }
}
