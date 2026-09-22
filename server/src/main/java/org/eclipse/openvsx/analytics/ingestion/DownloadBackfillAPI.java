/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.analytics.ContinuousAggregateRefresher;
import org.eclipse.openvsx.analytics.ingestion.aws.DownloadLogParser;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;

/**
 * Admin endpoint to backfill download analytics from an already-processed access-log file: it
 * stores the events without touching the download counters, then refreshes the aggregates over the
 * file's range. Mapped only when {@code ovsx.analytics.enabled=true}.
 */
@RestController
@ConditionalOnProperty(name = "ovsx.analytics.enabled", havingValue = "true")
public class DownloadBackfillAPI {

    private final AdminService admins;
    private final DownloadLogParser parser;
    private final DownloadIngestionProcessor processor;
    private final ContinuousAggregateRefresher refresher;
    private final StorageUtilService storageUtil;

    public DownloadBackfillAPI(
            AdminService admins,
            DownloadLogParser parser,
            DownloadIngestionProcessor processor,
            ContinuousAggregateRefresher refresher,
            StorageUtilService storageUtil
    ) {
        this.admins = admins;
        this.parser = parser;
        this.processor = processor;
        this.refresher = refresher;
        this.storageUtil = storageUtil;
    }

    @PostMapping(
        path = "/admin/api/analytics/downloads/backfill",
        consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Backfills download analytics from one access-log file without re-counting downloads (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "The file was processed; a summary is returned")
    @ApiResponse(responseCode = "400", description = "A parameter or the log format is invalid", content = @Content())
    @ApiResponse(responseCode = "403", description = "The caller is not an admin", content = @Content())
    public ResponseEntity<BackfillResultJson> backfill(
            InputStream content,
            @RequestParam(required = false)
            @Parameter(description = "Admin access token; omit to authenticate with the session") String token,
            @RequestParam(defaultValue = "cloudfront")
            @Parameter(description = "Log format: cloudfront or fastly") String format,
            @RequestParam(required = false)
            @Parameter(
                description = "UTC date to stamp records that carry no timestamp of their own, yyyy-mm-dd",
                example = "2026-02-09"
            ) String fileDate
    ) throws IOException {
        checkAdmin(token);

        // resolve filenames against the application's own storage, not a caller-supplied one
        var storageType = storageUtil.getActiveStorageType();
        var records = parser.parse(content, logFormat(format), fallbackTime(fileDate));
        var result = processor.backfill(storageType, records);
        if (result.events() > 0) {
            refresher.refresh(result.from(), result.to());
        }
        return ResponseEntity.ok(BackfillResultJson.from(result));
    }

    private void checkAdmin(@Nullable String token) {
        try {
            if (StringUtils.isNotEmpty(token)) {
                admins.checkAdminUser(token);
            } else {
                admins.checkAdminUser();
            }
        } catch (ErrorResultException e) {
            var status = e.getStatus() != null ? e.getStatus() : HttpStatus.FORBIDDEN;
            throw new ResponseStatusException(status, e.getMessage());
        }
    }

    private DownloadLogParser.Format logFormat(String format) {
        try {
            return DownloadLogParser.Format.from(format);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Instant fallbackTime(@Nullable String fileDate) {
        if (fileDate == null) {
            return Instant.now();
        }
        try {
            return LocalDate.parse(fileDate).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "fileDate must be a date in the format yyyy-mm-dd");
        }
    }

}
