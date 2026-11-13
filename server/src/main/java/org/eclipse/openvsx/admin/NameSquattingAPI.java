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
package org.eclipse.openvsx.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.admin.NameSquattingService.NamespaceSort;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.json.NameSquattingJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;

/**
 * The name squatting admin dashboard: extensions whose names match or resemble an extension published
 * earlier in another namespace, namespaces whose names resemble each other, and the pairs admins allowed.
 * Deleting a look-alike goes through the admin extension delete endpoint.
 */
@RestController
@Validated
@RequestMapping("/admin/name-squatting")
@ApiResponse(
    responseCode = "403",
    description = "The manage_extensions permission is required",
    content = @Content()
)
public class NameSquattingAPI {

    private final AdminService admins;
    private final NameSquattingService nameSquatting;

    public NameSquattingAPI(AdminService admins, NameSquattingService nameSquatting) {
        this.admins = admins;
        this.nameSquatting = nameSquatting;
    }

    @GetMapping(path = "/collisions", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(
        summary = "List look-alikes, grouped under the extension each resembles that was published first",
        description = "Looks up again the extensions the NAME_SIMILARITY publish check flagged, the most recently "
                + "flagged first, then those sharing a display name with another namespace's extension. Allowed "
                + "pairs and pairs within one namespace are left out."
    )
    @ApiResponse(
        responseCode = "503",
        description = "A lookup ran longer than ovsx.scanning.name-similarity.statement-timeout and was stopped",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "200",
        description = "A page of groups",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingJson.Collisions.class)
        )
    )
    public ResponseEntity<NameSquattingJson.Collisions> getCollisions(
            @RequestParam(defaultValue = "0")
            @Parameter(description = "The nextOffset of the previous page, 0 for the first")
            @Min(value = 0, message = "parameter must not be negative") int offset,
            @RequestParam(defaultValue = "25")
            @Parameter(description = "How many candidates to look up per batch")
            @Min(value = 1, message = "parameter must be positive")
            @Max(value = 100, message = "parameter must not be larger than 100") int size
    ) {
        admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        try {
            return ResponseEntity.ok(nameSquatting.findCollisions(offset, size));
        } catch (ErrorResultException exc) {
            throw toResponseStatus(exc);
        }
    }

    @PostMapping(
        path = "/allowlist",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @MutatingOperation
    @Operation(summary = "Allow a look-alike alongside an extension it resembles")
    @ApiResponse(
        responseCode = "200",
        description = "The pair is allowed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "The two extensions are the same or no longer resemble each other, or the reason is too long",
        content = @Content()
    )
    @ApiResponse(responseCode = "404", description = "An extension was not found", content = @Content())
    public ResponseEntity<ResultJson> allow(@RequestBody NameSquattingJson.AllowRequest request) {
        var admin = admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        try {
            return ResponseEntity.ok(nameSquatting.allow(request, admin));
        } catch (ErrorResultException exc) {
            throw toResponseStatus(exc);
        }
    }

    @GetMapping(path = "/allowlist", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(summary = "List the allowed look-alikes, the most recently allowed first, with their allowed pairs")
    @ApiResponse(
        responseCode = "200",
        description = "A page of allowed look-alikes",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingJson.Allowlist.class)
        )
    )
    public ResponseEntity<NameSquattingJson.Allowlist> getAllowlist(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "parameter must not be negative") int page,
            @RequestParam(defaultValue = "25")
            @Min(value = 1, message = "parameter must be positive")
            @Max(value = 100, message = "parameter must not be larger than 100") int size
    ) {
        admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        return ResponseEntity.ok(nameSquatting.findAllowlist(page, size));
    }

    @DeleteMapping(path = "/allowlist/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @MutatingOperation
    @Operation(
        summary = "Remove an allowed pair of extensions or of namespaces, so it is reported again while the two still resemble each other"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The allow is removed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(responseCode = "404", description = "The allowed pair was not found", content = @Content())
    public ResponseEntity<ResultJson> removeAllow(
            @PathVariable
            @Parameter(description = "Allowed pair ID") long id
    ) {
        var admin = admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        try {
            return ResponseEntity.ok(nameSquatting.removeAllow(id, admin));
        } catch (ErrorResultException exc) {
            throw toResponseStatus(exc);
        }
    }

    @GetMapping(path = "/namespaces", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(
        summary = "List pairs of namespaces whose names resemble each other",
        description = "Names resemble each other when, ignoring case, they are equal up to 4 characters, within one "
                + "edit up to 8 and within two for longer ones, or only differ in characters other than letters and "
                + "digits. The namespace with more downloads is the target. Allowed pairs and pairs without an "
                + "active extension on either side are left out. The pairs are looked up again at most once a "
                + "minute, while allows apply at once."
    )
    @ApiResponse(
        responseCode = "503",
        description = "The lookup ran longer than ovsx.scanning.name-similarity.statement-timeout and was stopped",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "200",
        description = "A page of pairs",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingJson.NamespacePairs.class)
        )
    )
    public ResponseEntity<NameSquattingJson.NamespacePairs> getNamespacePairs(
            @RequestParam(defaultValue = "true")
            @Parameter(
                description = "Whether to list only the pairs whose target counts as popular"
            ) boolean popularOnly,
            @RequestParam(defaultValue = "downloads")
            @Parameter(
                description = "downloads: the most downloaded target first; newest: the look-alike that published first most recently first"
            )
            @Pattern(regexp = "downloads|newest", message = "parameter must be downloads or newest") String sort,
            @RequestParam(defaultValue = "0")
            @Parameter(description = "How many pairs to skip, 0 for the first page")
            @Min(value = 0, message = "parameter must not be negative") int offset,
            @RequestParam(defaultValue = "25")
            @Parameter(description = "How many pairs to return")
            @Min(value = 1, message = "parameter must be positive")
            @Max(value = 100, message = "parameter must not be larger than 100") int size
    ) {
        admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        var order = sort.equals("newest") ? NamespaceSort.NEWEST : NamespaceSort.DOWNLOADS;
        try {
            return ResponseEntity.ok(nameSquatting.findNamespacePairs(popularOnly, order, offset, size));
        } catch (ErrorResultException exc) {
            throw toResponseStatus(exc);
        }
    }

    @PostMapping(
        path = "/namespaces/allowlist",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @MutatingOperation
    @Operation(
        summary = "Allow a namespace alongside a namespace it resembles",
        description = "The pair is no longer listed, whichever way round it was allowed. Allowing a pair already "
                + "allowed succeeds without change."
    )
    @ApiResponse(
        responseCode = "200",
        description = "The pair is allowed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "The two namespaces are the same or do not resemble each other, or the reason is too long",
        content = @Content()
    )
    @ApiResponse(responseCode = "404", description = "A namespace was not found", content = @Content())
    public ResponseEntity<ResultJson> allowNamespaces(@RequestBody NameSquattingJson.NamespaceAllowRequest request) {
        var admin = admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        try {
            return ResponseEntity.ok(nameSquatting.allowNamespaces(request, admin));
        } catch (ErrorResultException exc) {
            throw toResponseStatus(exc);
        }
    }

    @GetMapping(path = "/namespaces/allowlist", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin
    @Operation(summary = "List the allowed pairs of namespaces, the most recently allowed first")
    @ApiResponse(
        responseCode = "200",
        description = "A page of allowed pairs",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NameSquattingJson.NamespaceAllowlist.class)
        )
    )
    public ResponseEntity<NameSquattingJson.NamespaceAllowlist> getNamespaceAllowlist(
            @RequestParam(defaultValue = "0")
            @Parameter(description = "The page, 0 for the first")
            @Min(value = 0, message = "parameter must not be negative") int page,
            @RequestParam(defaultValue = "25")
            @Parameter(description = "How many pairs per page")
            @Min(value = 1, message = "parameter must be positive")
            @Max(value = 100, message = "parameter must not be larger than 100") int size
    ) {
        admins.checkPermission(Permission.MANAGE_EXTENSIONS);
        return ResponseEntity.ok(nameSquatting.findNamespaceAllowlist(page, size));
    }

    private static ResponseStatusException toResponseStatus(ErrorResultException exc) {
        var status = exc.getStatus() != null ? exc.getStatus() : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, exc.getMessage());
    }
}
