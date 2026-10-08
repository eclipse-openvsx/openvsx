/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * REST API for file decision management (allow/block lists).
 * Provides endpoints for managing file-level security decisions.
 */
@RestController
@Validated
@RequestMapping("/admin/scans")
@ApiResponse(
    responseCode = "403",
    description = "Administration role is required",
    content = @Content()
)
public class FileDecisionAPI {

    private final RepositoryService repositories;
    private final AdminService admins;

    public FileDecisionAPI(RepositoryService repositories, AdminService admins) {
        this.repositories = repositories;
        this.admins = admins;
    }

    @GetMapping(
        path = "/files",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get files with admin decisions")
    @ApiResponse(
        responseCode = "200",
        description = "List of file decisions",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = FileDecisionListJson.class)
        )
    )
    public ResponseEntity<FileDecisionListJson> getFiles(
            @RequestParam(required = false)
            @Parameter(
                description = "Filter by admin decision type",
                schema = @Schema(type = "string", allowableValues = { "allowed", "blocked" })
            ) String decision,
            @RequestParam(required = false)
            @Parameter(description = "Filter by publisher name") String publisher,
            @RequestParam(required = false)
            @Parameter(description = "Filter by namespace") String namespace,
            @RequestParam(required = false)
            @Parameter(description = "Filter by display name, extension name, or file name") String name,
            @RequestParam(defaultValue = "18")
            @Min(value = 0, message = "parameter must not be negative")
            @Max(value = 100, message = "parameter must not be larger than 100")
            @Parameter(
                description = "Maximum number of entries to return",
                schema = @Schema(type = "integer", minimum = "0", maximum = "100", defaultValue = "18")
            ) int size,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "parameter must not be negative")
            @Parameter(
                description = "Number of entries to skip",
                schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")
            ) int offset,
            @RequestParam(defaultValue = "dateDecided")
            @Parameter(
                description = "Field to sort by",
                schema = @Schema(
                    type = "string",
                    allowableValues = { "dateDecided", "fileName", "publisher", "namespace" },
                    defaultValue = "dateDecided"
                )
            ) String sortBy,
            @RequestParam(defaultValue = "desc")
            @Parameter(
                description = "Sort order",
                schema = @Schema(type = "string", allowableValues = { "asc", "desc" }, defaultValue = "desc")
            ) String sortOrder,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter files decided on or after this date (ISO 8601 format)"
            ) String dateDecidedFrom,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter files decided on or before this date (ISO 8601 format)"
            ) String dateDecidedTo
    ) {
        try {
            admins.checkPermission(Permission.MANAGE_SCANS);

            var decidedFrom = parseUtcDateTime(dateDecidedFrom, "dateDecidedFrom");
            var decidedTo = parseUtcDateTime(dateDecidedTo, "dateDecidedTo");
            var dbSortField = toFileSortField(sortBy);
            var ascending = normalizeSortOrder(sortOrder);

            var sort = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, dbSortField);
            var pageNumber = offset / Math.max(size, 1);
            var pageable = PageRequest.of(pageNumber, size, sort);

            var page = repositories.findFileDecisionsFiltered(
                    decision,
                    publisher,
                    namespace,
                    name,
                    decidedFrom,
                    decidedTo,
                    pageable);

            var result = new FileDecisionListJson();
            result.setTotalSize((int) page.getTotalElements());
            result.setOffset(offset);
            result.setFiles(
                    page.getContent().stream()
                            .map(this::toFileDecisionJson)
                            .collect(Collectors.toList()));

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(FileDecisionListJson.class);
        }
    }

    @GetMapping(
        path = "/files/{fileId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get specific file decision")
    @ApiResponse(
        responseCode = "200",
        description = "File decision details",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = FileDecisionJson.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "File decision not found",
        content = @Content()
    )
    public ResponseEntity<FileDecisionJson> getFileDecision(
            @PathVariable
            @Parameter(description = "File decision ID", example = "123") long fileId
    ) {
        try {
            admins.checkPermission(Permission.MANAGE_SCANS);

            var decision = repositories.findFileDecision(fileId);
            if (decision == null) {
                throw new ErrorResultException("File decision not found: " + fileId, HttpStatus.NOT_FOUND);
            }

            var result = toFileDecisionJson(decision);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(FileDecisionJson.class);
        }
    }

    @PostMapping(
        path = "/files/decisions",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Create or update file decisions")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "Decisions processed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = FileDecisionResponseJson.Create.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content()
    )
    public ResponseEntity<FileDecisionResponseJson.Create> makeFileDecisions(
            @RequestBody FileDecisionRequest.Create request
    ) {
        try {
            var adminUser = admins.checkPermission(Permission.MANAGE_SCANS);

            if (request.fileHashes() == null || request.fileHashes().isEmpty()) {
                throw new ErrorResultException("File hashes are required", HttpStatus.BAD_REQUEST);
            }
            if (request.decision() == null || request.decision().isBlank()) {
                throw new ErrorResultException("Decision is required", HttpStatus.BAD_REQUEST);
            }

            var decisionValue = parseFileDecision(request.decision());

            var results = new java.util.ArrayList<FileDecisionResultJson.Create>();
            int successful = 0;
            int failed = 0;

            for (var fileHash : request.fileHashes()) {
                try {
                    if (fileHash == null || fileHash.isBlank()) {
                        results.add(FileDecisionResultJson.Create.failure(fileHash, "Empty file hash"));
                        failed++;
                        continue;
                    }

                    var existingDecision = repositories.findFileDecisionByHash(fileHash);
                    if (existingDecision != null) {
                        existingDecision.setDecision(decisionValue);
                        existingDecision.setDecidedBy(adminUser);
                        existingDecision.setDecidedAt(TimeUtil.getCurrentUTC());
                        repositories.saveFileDecision(existingDecision);
                    } else {
                        FileDecision newDecision;
                        if (FileDecision.ALLOWED.equals(decisionValue)) {
                            newDecision = FileDecision.allowed(fileHash, adminUser);
                        } else {
                            newDecision = FileDecision.blocked(fileHash, adminUser);
                        }
                        repositories.saveFileDecision(newDecision);
                    }

                    results.add(FileDecisionResultJson.Create.success(fileHash));
                    successful++;
                } catch (Exception e) {
                    results.add(FileDecisionResultJson.Create.failure(fileHash, e.getMessage()));
                    failed++;
                }
            }

            var response = new FileDecisionResponseJson.Create();
            response.setProcessed(request.fileHashes().size());
            response.setSuccessful(successful);
            response.setFailed(failed);
            response.setResults(results);

            return ResponseEntity.ok(response);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(FileDecisionResponseJson.Create.class);
        }
    }

    @DeleteMapping(
        path = "/files/decisions",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Remove file decisions")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "Deletions processed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = FileDecisionResponseJson.Delete.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content()
    )
    public ResponseEntity<FileDecisionResponseJson.Delete> deleteFileDecisions(
            @RequestBody FileDecisionRequest.Delete request
    ) {
        try {
            admins.checkPermission(Permission.MANAGE_SCANS);

            if (request.fileIds() == null || request.fileIds().isEmpty()) {
                throw new ErrorResultException("File IDs are required", HttpStatus.BAD_REQUEST);
            }

            var results = new java.util.ArrayList<FileDecisionResultJson.Delete>();
            int successful = 0;
            int failed = 0;

            for (var fileId : request.fileIds()) {
                try {
                    if (fileId == null) {
                        results.add(FileDecisionResultJson.Delete.failure(null, "File ID is null"));
                        failed++;
                        continue;
                    }

                    var decision = repositories.findFileDecision(fileId);
                    if (decision == null) {
                        results.add(FileDecisionResultJson.Delete.failure(fileId, "File decision not found"));
                        failed++;
                        continue;
                    }

                    repositories.deleteFileDecision(fileId);
                    results.add(FileDecisionResultJson.Delete.success(fileId));
                    successful++;
                } catch (Exception e) {
                    results.add(FileDecisionResultJson.Delete.failure(fileId, e.getMessage()));
                    failed++;
                }
            }

            var response = new FileDecisionResponseJson.Delete();
            response.setProcessed(request.fileIds().size());
            response.setSuccessful(successful);
            response.setFailed(failed);
            response.setResults(results);

            return ResponseEntity.ok(response);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(FileDecisionResponseJson.Delete.class);
        }
    }

    @GetMapping(
        path = "/files/counts",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get file decision counts")
    @ApiResponse(
        responseCode = "200",
        description = "File decision counts",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = FileDecisionCountsJson.class)
        )
    )
    public ResponseEntity<FileDecisionCountsJson> getFileDecisionCounts(
            @RequestParam(required = false)
            @Parameter(
                description = "Filter files decided on or after this date (ISO 8601 format)"
            ) String dateDecidedFrom,
            @RequestParam(required = false)
            @Parameter(
                description = "Filter files decided on or before this date (ISO 8601 format)"
            ) String dateDecidedTo
    ) {
        try {
            admins.checkPermission(Permission.MANAGE_SCANS);

            var decidedFrom = parseUtcDateTime(dateDecidedFrom, "dateDecidedFrom");
            var decidedTo = parseUtcDateTime(dateDecidedTo, "dateDecidedTo");

            var counts = new FileDecisionCountsJson();

            if (decidedFrom == null && decidedTo == null) {
                counts.setAllowed((int) repositories.countFileDecisions(FileDecision.ALLOWED));
                counts.setBlocked((int) repositories.countFileDecisions(FileDecision.BLOCKED));
            } else {
                counts.setAllowed(
                        (int) repositories.countFileDecisionsByDateRange(FileDecision.ALLOWED, decidedFrom, decidedTo));
                counts.setBlocked(
                        (int) repositories.countFileDecisionsByDateRange(FileDecision.BLOCKED, decidedFrom, decidedTo));
            }
            counts.setTotal(counts.getAllowed() + counts.getBlocked());

            return ResponseEntity.ok(counts);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(FileDecisionCountsJson.class);
        }
    }

    private String parseFileDecision(String decision) {
        var normalized = decision.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "allowed" -> FileDecision.ALLOWED;
            case "blocked" -> FileDecision.BLOCKED;
            default -> throw new ErrorResultException(
                    "Invalid decision value: " + decision + ". Must be 'allowed' or 'blocked'",
                    HttpStatus.BAD_REQUEST);
        };
    }

    private String toFileSortField(String sortBy) {
        if (sortBy == null) {
            return "decided_at";
        }
        return switch (sortBy.toLowerCase(Locale.ROOT)) {
            case "datedecided" -> "decided_at";
            case "filename" -> "file_name";
            case "publisher" -> "publisher";
            case "namespace" -> "namespace_name";
            default -> throw new ErrorResultException("Unsupported sortBy value: " + sortBy, HttpStatus.BAD_REQUEST);
        };
    }

    private boolean normalizeSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return false;
        }
        return switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default ->
                throw new ErrorResultException("Unsupported sortOrder value: " + sortOrder, HttpStatus.BAD_REQUEST);
        };
    }

    private LocalDateTime parseUtcDateTime(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TimeUtil.fromUTCString(raw);
        } catch (Exception e) {
            throw new ErrorResultException(
                    "Invalid ISO date-time for parameter '" + paramName + "': " + raw,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private FileDecisionJson toFileDecisionJson(FileDecision decision) {
        var json = new FileDecisionJson();
        json.setId(String.valueOf(decision.getId()));
        json.setFileHash(decision.getFileHash());
        json.setFileName(decision.getFileName());
        json.setFileType(decision.getFileType());

        json.setDecision(FileDecision.ALLOWED.equals(decision.getDecision()) ? "allowed" : "blocked");
        json.setDecidedBy(decision.getDecidedByName());
        json.setDateDecided(TimeUtil.toUTCString(decision.getDecidedAt()));

        json.setDisplayName(decision.getDisplayName());
        json.setNamespace(decision.getNamespaceName());
        json.setExtensionName(decision.getExtensionName());
        json.setPublisher(decision.getPublisher());
        json.setVersion(decision.getVersion());

        if (decision.getScan() != null) {
            json.setScanId(String.valueOf(decision.getScan().getId()));
        }

        return json;
    }
}
