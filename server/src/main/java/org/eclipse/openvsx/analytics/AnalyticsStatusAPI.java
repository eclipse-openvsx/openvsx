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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.json.AnalyticsStatusJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

/**
 * Whether download analytics works, for the admin dashboard. Mapped whether or not analytics is
 * enabled, so that the page can say it is off.
 */
@RestController
public class AnalyticsStatusAPI {

    private final AdminService admins;
    private final RepositoryService repositories;
    private final ObjectProvider<AnalyticsAvailability> availability;

    public AnalyticsStatusAPI(
            AdminService admins,
            RepositoryService repositories,
            ObjectProvider<AnalyticsAvailability> availability
    ) {
        this.admins = admins;
        this.repositories = repositories;
        this.availability = availability;
    }

    @GetMapping(path = "/admin/analytics/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(hidden = true, summary = "The state of download analytics")
    @ApiResponse(
        responseCode = "200",
        description = "The status is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AnalyticsStatusJson.class)
        )
    )
    public ResponseEntity<AnalyticsStatusJson> getStatus() {
        try {
            admins.checkPermission(Permission.VIEW_REPORTS);

            var json = new AnalyticsStatusJson();
            var tracker = availability.getIfAvailable();
            json.setEnabled(tracker != null);
            if (tracker != null) {
                var status = tracker.status();
                json.setAvailable(status.available());
                json.setSince(status.since().toString());
                if (status.lastProbe() != null) {
                    json.setLastProbe(status.lastProbe().toString());
                }
                json.setLastError(status.lastError());
            }
            json.setPendingFiles(repositories.countPendingAnalyticsDownloadIngestions());
            json.setFailedFiles(repositories.countFailedDownloadIngestions());
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(AnalyticsStatusJson.class);
        }
    }
}
