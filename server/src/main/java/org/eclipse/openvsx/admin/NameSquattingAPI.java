/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.admin;

import java.util.List;

import org.eclipse.openvsx.json.NameSquattingActionRequest;
import org.eclipse.openvsx.json.NameSquattingActionResponseJson;
import org.eclipse.openvsx.json.NameSquattingCountsJson;
import org.eclipse.openvsx.json.NameSquattingFlagListJson;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * REST API for moderating extensions that failed the name squatting publisher check.
 * <p>
 * The check runs at publish time and behaves differently depending on whether it is enforced. When
 * it is enforced, publication is blocked and no extension version is ever created, so the finding
 * is only a record of a rejection. When it is not enforced, the finding is recorded for monitoring
 * and the extension goes live - and it is those extensions an administrator needs to act on, by
 * either clearing the check as a false positive or deactivating an extension that turns out to be
 * malicious.
 * <p>
 * Findings are grouped per extension because both actions apply to the extension as a whole.
 */
@RestController
@Validated
@RequestMapping("/admin/name-squatting")
@ApiResponse(
    responseCode = "403",
    description = "Administration role is required",
    content = @Content()
)
public class NameSquattingAPI {

    public static final String NAME_SQUATTING_CHECK_TYPE = SimilarityCheckService.CHECK_TYPE;

    /** The flagged extension is live: it exists and has at least one active version. */
    public static final String NAME_SQUATTING_STATE_PUBLISHED = "PUBLISHED";

    /** The flagged extension exists but all of its versions have been deactivated. */
    public static final String NAME_SQUATTING_STATE_DEACTIVATED = "DEACTIVATED";

    /** Publication was blocked by the name squatting check, so the extension was never created. */
    public static final String NAME_SQUATTING_STATE_REJECTED = "REJECTED";
    
    private final AdminService admins;

    public NameSquattingAPI(AdminService admins) {
        this.admins = admins;
    }

    /**
     * List the extensions flagged by the name squatting check, one entry per extension, most
     * recently flagged first.
     */
    @GetMapping(
        path = "",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get extensions flagged by the name squatting check")
    @ApiResponse(
        responseCode = "200",
        description = "Paginated list of flagged extensions",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingFlagListJson.class)
        )
    )
    public ResponseEntity<NameSquattingFlagListJson> getFlaggedExtensions(
            @RequestParam(required = false)
            @Parameter(description = "Filter by publisher name (partial matches supported)") String publisher,
            @RequestParam(required = false)
            @Parameter(description = "Filter by namespace (partial matches supported)") String namespace,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter by display name or extension name (partial matches supported)"
            ) String name,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter by what became of the extension (comma-separated for multiple values)",
                style = ParameterStyle.FORM,
                explode = Explode.FALSE,
                array = @ArraySchema(
                    schema = @Schema(
                        type = "string",
                        allowableValues = { "PUBLISHED", "DEACTIVATED", "REJECTED" },
                        example = "PUBLISHED"
                    )
                )
            ) List<String> state,
            @RequestParam(required = false)
            @Parameter(
                description = "Only include findings detected on or after this date (ISO 8601 format)"
            ) String dateDetectedFrom,
            @RequestParam(required = false)
            @Parameter(
                description = "Only include findings detected on or before this date (ISO 8601 format)"
            ) String dateDetectedTo,
            @RequestParam(defaultValue = "10")
            @Min(value = 0, message = "parameter must not be negative")
            @Max(value = 100, message = "parameter must not be larger than 100")
            @Parameter(
                description = "Maximal number of entries to return",
                schema = @Schema(type = "integer", minimum = "0", maximum = "100", defaultValue = "10")
            ) int size,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "parameter must not be negative")
            @Parameter(
                description = "Number of entries to skip",
                schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")
            ) int offset,
            @RequestParam(defaultValue = "desc")
            @Parameter(
                description = "Order by the most recent detection",
                schema = @Schema(type = "string", allowableValues = { "asc", "desc" }, defaultValue = "desc")
            ) String sortOrder
    ) {
        try {
            admins.checkAdminUser();

            var result = admins.getNameSquattingFlags(
                    publisher,
                    namespace,
                    name,
                    state,
                    dateDetectedFrom,
                    dateDetectedTo,
                    size,
                    offset,
                    sortOrder);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NameSquattingFlagListJson.class);
        }
    }

    /**
     * Get the number of flagged extensions, broken down by what became of them.
     */
    @GetMapping(
        path = "/counts",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get counts of extensions flagged by the name squatting check")
    @ApiResponse(
        responseCode = "200",
        description = "Counts of flagged extensions per state",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingCountsJson.class)
        )
    )
    public ResponseEntity<NameSquattingCountsJson> getCounts(
            @RequestParam(required = false)
            @Parameter(description = "Filter by publisher name (partial matches supported)") String publisher,
            @RequestParam(required = false)
            @Parameter(description = "Filter by namespace (partial matches supported)") String namespace,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter by display name or extension name (partial matches supported)"
            ) String name,
            @RequestParam(required = false)
            @Parameter(
                description = "Only count findings detected on or after this date (ISO 8601 format)"
            ) String dateDetectedFrom,
            @RequestParam(required = false)
            @Parameter(
                description = "Only count findings detected on or before this date (ISO 8601 format)"
            ) String dateDetectedTo
    ) {
        try {
            admins.checkAdminUser();

            var counts = admins
                    .getNameSquattingCounts(publisher, namespace, name, dateDetectedFrom, dateDetectedTo);
            return ResponseEntity.ok(counts);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NameSquattingCountsJson.class);
        }
    }

    /**
     * Clear the name squatting findings recorded for one or more extensions, for use when an
     * administrator judges the match to be a false positive.
     * <p>
     * This removes the failure records, so the extension no longer shows up as flagged. The audit
     * record of the check having run is kept in the scan check results, and the action itself is
     * written to the admin log.
     */
    @PostMapping(
        path = "/clear",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Clear name squatting findings as a false positive")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "Findings cleared",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingActionResponseJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "No extensions were named in the request",
        content = @Content()
    )
    public ResponseEntity<NameSquattingActionResponseJson> clearFindings(
            @RequestBody NameSquattingActionRequest request
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            return ResponseEntity.ok(admins.clearNameSquattingFindings(adminUser, request));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NameSquattingActionResponseJson.class);
        }
    }

    /**
     * Soft-delete one or more flagged extensions, for use when the match turns out to be a real
     * attempt at squatting a name.
     * <p>
     * Every active version is deactivated, which makes the extension unavailable while keeping its
     * records and reserving its version identities. Extensions whose publication was blocked by the
     * check were never created and cannot be deleted.
     */
    @PostMapping(
        path = "/delete",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Soft-delete extensions flagged for name squatting")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "Extensions deactivated",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingActionResponseJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "No extensions were named in the request",
        content = @Content()
    )
    public ResponseEntity<NameSquattingActionResponseJson> deleteExtensions(
            @RequestBody NameSquattingActionRequest request
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            return ResponseEntity.ok(admins.deleteNameSquattingExtensions(adminUser, request));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NameSquattingActionResponseJson.class);
        }
    }
}
