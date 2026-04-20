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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * REST API for extension scan management.
 * Provides endpoints for listing and retrieving scan results.
 * Used by the admin dashboard to monitor extension validation and scanning.
 */
@RestController
@RequestMapping("/admin/scans")
@ApiResponse(
    responseCode = "403",
    description = "Administration role is required",
    content = @Content()
)
public class ScanAPI {

    private final RepositoryService repositories;
    private final AdminService admins;
    private final LogService logs;
    private final StorageUtilService storageUtil;
    private final org.eclipse.openvsx.scanning.ExtensionScanCompletionService completionService;

    public ScanAPI(
            RepositoryService repositories,
            AdminService admins,
            LogService logs,
            StorageUtilService storageUtil,
            org.eclipse.openvsx.scanning.ExtensionScanCompletionService completionService
    ) {
        this.repositories = repositories;
        this.admins = admins;
        this.logs = logs;
        this.storageUtil = storageUtil;
        this.completionService = completionService;
    }

    /**
     * Get aggregated scan counts by status and admin decisions counts.
     */
    @GetMapping(
        path = "/counts",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get scan counts")
    @ApiResponse(
        responseCode = "200",
        description = "Scan counts by status and quarantine decision",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ScanStatisticsJson.class)
        )
    )
    public ResponseEntity<ScanStatisticsJson> getScanCounts(
        @RequestParam(required = false)
        @Parameter(description = "Filter scans started on or after this date (ISO 8601 format)")
        String dateStartedFrom,
        @RequestParam(required = false)
        @Parameter(description = "Filter scans started on or before this date (ISO 8601 format)")
        String dateStartedTo,
        @RequestParam(defaultValue = "all")
        @Parameter(description = "Filter by enforcement status of threats/validations", schema = @Schema(type = "string", allowableValues = {"enforced", "notEnforced", "all"}, defaultValue = "all"))
        String enforcement,
        @RequestParam(name = "validationType", required = false)
        @Parameter(
            description = "Filter by validation type (comma-separated for multiple values, e.g., NAME SQUATTING, BLOCKLIST, SECRET)",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(type = "string", example = "NAME SQUATTING"))
        )
        List<String> validationType,
        @RequestParam(name = "threatScannerName", required = false)
        @Parameter(
            description = "Filter by threat scanner name (comma-separated for multiple values).",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(type = "string", example = "ClamAV"))
        )
        List<String> threatScannerName
    ) {
        try {
            admins.checkAdminUser();

            var stats = new ScanStatisticsJson();

            // Parse all filter parameters
            var startedFrom = parseUtcDateTime(dateStartedFrom, "dateStartedFrom");
            var startedTo = parseUtcDateTime(dateStartedTo, "dateStartedTo");
            var enforcementFilter = parseEnforcementFilter(enforcement);
            var checkTypes = parseValidationTypes(validationType);
            var scannerNames = parseScannerNames(threatScannerName);

            boolean hasDateFilter = startedFrom != null || startedTo != null;
            boolean hasEnforcementFilter = enforcementFilter != EnforcementFilter.ALL;
            boolean hasCheckTypesFilter = checkTypes != null && !checkTypes.isEmpty();
            boolean hasScannerNamesFilter = scannerNames != null && !scannerNames.isEmpty();
            boolean hasAnyFilter = hasDateFilter || hasEnforcementFilter || hasCheckTypesFilter || hasScannerNamesFilter;

            if (!hasAnyFilter) {
                // Fast path: simple DB counts when no filtering is requested
                stats.setSTARTED(repositories.countExtensionScansByStatus(ScanStatus.STARTED));
                stats.setVALIDATING(repositories.countExtensionScansByStatus(ScanStatus.VALIDATING));
                stats.setSCANNING(repositories.countExtensionScansByStatus(ScanStatus.SCANNING));
                stats.setPASSED(repositories.countExtensionScansByStatus(ScanStatus.PASSED));
                stats.setQUARANTINED(repositories.countExtensionScansByStatus(ScanStatus.QUARANTINED));
                stats.setAUTO_REJECTED(repositories.countExtensionScansByStatus(ScanStatus.REJECTED));
                stats.setERROR(repositories.countExtensionScansByStatus(ScanStatus.ERRORED));
                stats.setALLOWED((int) repositories.countAdminScanDecisions(AdminScanDecision.ALLOWED));
                stats.setBLOCKED((int) repositories.countAdminScanDecisions(AdminScanDecision.BLOCKED));
            } else {
                // Use unified count query with all filters
                Boolean enforcedOnly = switch (enforcementFilter) {
                    case ENFORCED -> true;
                    case NOT_ENFORCED -> false;
                    case ALL -> null;
                };

                // Count each status with all filters applied
                stats.setSTARTED((int) repositories.countScansForStatistics(
                    ScanStatus.STARTED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setVALIDATING((int) repositories.countScansForStatistics(
                    ScanStatus.VALIDATING, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setSCANNING((int) repositories.countScansForStatistics(
                    ScanStatus.SCANNING, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setPASSED((int) repositories.countScansForStatistics(
                    ScanStatus.PASSED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setQUARANTINED((int) repositories.countScansForStatistics(
                    ScanStatus.QUARANTINED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setAUTO_REJECTED((int) repositories.countScansForStatistics(
                    ScanStatus.REJECTED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setERROR((int) repositories.countScansForStatistics(
                    ScanStatus.ERRORED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));

                // Admin decision counts with all filters applied
                stats.setALLOWED((int) repositories.countAdminDecisionsForStatistics(
                    AdminScanDecision.ALLOWED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
                stats.setBLOCKED((int) repositories.countAdminDecisionsForStatistics(
                    AdminScanDecision.BLOCKED, startedFrom, startedTo, checkTypes, scannerNames, enforcedOnly));
            }

            // NEEDS_REVIEW = quarantined scans without a decision
            var quarantinedCount = stats.getQUARANTINED();
            var decidedCount = stats.getALLOWED() + stats.getBLOCKED();
            stats.setNEEDS_REVIEW(Math.max(0, quarantinedCount - decidedCount));

            return ResponseEntity.ok(stats);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ScanStatisticsJson.class);
        }
    }

    /**
     * Enforcement filter for Scan API /scans/counts.
     *
     * - ALL: no filtering
     * - ENFORCED: scans that have at least one enforced validation or threat
     * - NOT_ENFORCED: scans that have at least one non-enforced validation or threat
     *
     * Threat scanning enforcement will be added when threats are persisted.
     */
    private enum EnforcementFilter {
        ENFORCED,
        NOT_ENFORCED,
        ALL
    }

    private EnforcementFilter parseEnforcementFilter(String enforcement) {
        if (enforcement == null || enforcement.isBlank()) {
            return EnforcementFilter.ALL;
        }
        return switch (enforcement.trim().toLowerCase(Locale.ROOT)) {
            case "enforced" -> EnforcementFilter.ENFORCED;
            case "notenforced" -> EnforcementFilter.NOT_ENFORCED;
            case "all" -> EnforcementFilter.ALL;
            default -> throw new ErrorResultException(
                "Parameter 'enforcement' must be one of: enforced, notEnforced, all",
                HttpStatus.BAD_REQUEST
            );
        };
    }

    /**
     * Get all extension scans with filtering, sorting and pagination.
     */
    @GetMapping(
        path = "",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get all extension scans")
    @ApiResponse(
        responseCode = "200",
        description = "List of all scans",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            array = @ArraySchema(schema = @Schema(implementation = ScanResultJson.class))
        )
    )
    public ResponseEntity<ScanResultListJson> getAllScans(
        @RequestParam(required = false)
        @Parameter(
            description = "Filter by scan status (comma-separated for multiple values)",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(
                type = "string",
                allowableValues = { "STARTED", "VALIDATING", "SCANNING", "PASSED", "QUARANTINED", "AUTO REJECTED", "ERROR" },
                example = "QUARANTINED"
            ))
        )
        List<String> status,
        @RequestParam(required = false)
        @Parameter(description = "Filter by publisher name (partial matches supported)")
        String publisher,
        @RequestParam(required = false)
        @Parameter(description = "Filter by namespace (partial matches supported)")
        String namespace,
        @RequestParam(required = false)
        @Parameter(description = "Filter by display name or extension name (partial matches supported)")
        String name,
        @RequestParam(defaultValue = "10")
        @Parameter(description = "Maximal number of entries to return", schema = @Schema(type = "integer", minimum = "0", defaultValue = "10"))
        int size,
        @RequestParam(defaultValue = "0")
        @Parameter(description = "Number of entries to skip", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
        int offset,
        @RequestParam(defaultValue = "scanEndTime")
        @Parameter(description = "Field to sort by", schema = @Schema(type = "string", allowableValues = {"scanEndTime", "scanStartTime", "displayName", "publisher", "status"}, defaultValue = "scanEndTime"))
        String sortBy,
        @RequestParam(defaultValue = "desc")
        @Parameter(description = "Sort order", schema = @Schema(type = "string", allowableValues = {"asc", "desc"}, defaultValue = "desc"))
        String sortOrder,
        @RequestParam(required = false)
        @Parameter(description = "Filter scans started on or after this date (ISO 8601 format)")
        String dateStartedFrom,
        @RequestParam(required = false)
        @Parameter(description = "Filter scans started on or before this date (ISO 8601 format)")
        String dateStartedTo,
        @RequestParam(name = "validationType", required = false)
        @Parameter(
            description = "Filter by validation type (comma-separated for multiple values, e.g., NAME SQUATTING, BLOCKLIST, SECRET)",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(type = "string", example = "NAME SQUATTING"))
        )
        List<String> validationType,
        @RequestParam(required = false)
        @Parameter(
            description = "Filter by threat scanner name (comma-separated for multiple values).",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(type = "string", example = "ClamAV"))
        )
        List<String> threatScannerName,
        @RequestParam(defaultValue = "all")
        @Parameter(
            description = "Filter by enforcement status of threats/validations",
            schema = @Schema(type = "string", allowableValues = {"enforced", "notEnforced", "all"}, defaultValue = "all")
        )
        String enforcement,
        @RequestParam(name = "adminDecision", required = false)
        @Parameter(
            description = "Filter by admin decision status (comma-separated for multiple values). Use 'allowed' for scans with Allowed decision, 'blocked' for Blocked decision, 'needs-review' for scans with no decision yet.",
            style = ParameterStyle.FORM,
            explode = Explode.FALSE,
            array = @ArraySchema(schema = @Schema(type = "string", allowableValues = {"allowed", "blocked", "needs-review"}))
        )
        List<String> adminDecision
    ) {
        try {
            admins.checkAdminUser();

            if (size < 0) {
                throw new ErrorResultException("Parameter 'size' must be >= 0", HttpStatus.BAD_REQUEST);
            }
            if (offset < 0) {
                throw new ErrorResultException("Parameter 'offset' must be >= 0", HttpStatus.BAD_REQUEST);
            }

            var statusFilter = parseStatusFilter(status);
            var normalizedPublisher = normalizeSearch(publisher);
            var normalizedNamespace = normalizeSearch(namespace);
            var normalizedName = normalizeSearch(name);
            var ascending = normalizeSortOrder(sortOrder);

            var startedFrom = parseUtcDateTime(dateStartedFrom, "dateStartedFrom");
            var startedTo = parseUtcDateTime(dateStartedTo, "dateStartedTo");
            var enforcementFilter = parseEnforcementFilter(enforcement);
            var checkTypes = parseValidationTypes(validationType);
            var scannerNames = parseScannerNames(threatScannerName);
            var adminDecisionFilter = parseAdminDecisionFilter(adminDecision);

            // Convert enforcement filter to Boolean for DB query
            // null = no filter, true = enforced only, false = non-enforced only
            Boolean enforcedOnly = switch (enforcementFilter) {
                case ENFORCED -> true;
                case NOT_ENFORCED -> false;
                case ALL -> null;
            };


            var sort = createSort(sortBy, ascending);
            var pageNumber = offset / Math.max(size, 1);
            var pageable = PageRequest.of(pageNumber, size, sort);
            
            // Automatically include scans with errored check results when filtering by ERRORED status
            var includeCheckErrors = statusFilter.contains(ScanStatus.ERRORED);
            
            var page = repositories.findScansFullyFiltered(
                statusFilter.isEmpty() ? null : statusFilter,
                normalizedNamespace.isEmpty() ? null : normalizedNamespace,
                normalizedPublisher.isEmpty() ? null : normalizedPublisher,
                normalizedName.isEmpty() ? null : normalizedName,
                startedFrom,
                startedTo,
                checkTypes,
                scannerNames,
                enforcedOnly,
                adminDecisionFilter,
                includeCheckErrors,
                pageable
            );

            var result = new ScanResultListJson();
            result.setTotalSize((int) page.getTotalElements());
            result.setOffset(offset);
            result.setScans(page.getContent().stream()
                .map(this::toScanResultJson)
                .collect(Collectors.toList()));

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ScanResultListJson.class);
        }
    }

    /**
     * Parse validation type filter into a set of check types for DB query.
     * Returns null if no filter is specified.
     */
    private Set<String> parseValidationTypes(List<String> validationType) {
        if (validationType == null || validationType.isEmpty()) {
            return null;
        }
        var types = validationType.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());
        return types.isEmpty() ? null : types;
    }

    /**
     * Parse threat scanner name filter into a set of scanner names for DB query.
     * Returns null if no filter is specified.
     */
    private Set<String> parseScannerNames(List<String> scannerNames) {
        if (scannerNames == null || scannerNames.isEmpty()) {
            return null;
        }
        var names = scannerNames.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());
        return names.isEmpty() ? null : names;
    }

    /**
     * Admin decision filter values for the database query.
     */
    public record AdminDecisionFilterValues(
        boolean filterAllowed,      // Include scans with ALLOWED decision
        boolean filterBlocked,      // Include scans with BLOCKED decision
        boolean filterNeedsReview   // Include scans with no decision (needs review)
    ) {
        public boolean hasFilter() {
            return filterAllowed || filterBlocked || filterNeedsReview;
        }
    }

    /**
     * Parse admin decision filter into structured values for DB query.
     * Returns null if no filter is specified (show all).
     * 
     * API values: allowed, blocked, needs-review
     * DB values: ALLOWED, BLOCKED, (no record = needs-review)
     */
    private AdminDecisionFilterValues parseAdminDecisionFilter(List<String> adminDecision) {
        if (adminDecision == null || adminDecision.isEmpty()) {
            return null;
        }
        
        var values = adminDecision.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        
        if (values.isEmpty()) {
            return null;
        }
        
        return new AdminDecisionFilterValues(
            values.contains("allowed"),
            values.contains("blocked"),
            values.contains("needs-review")
        );
    }

    /**
     * Maps API sort field names to DB column names.
     */
    private String toDbSortField(String sortBy) {
        if (sortBy == null) {
            return "completed_at";
        }
        return switch (sortBy.toLowerCase(Locale.ROOT)) {
            case "displayname" -> "extension_display_name";
            case "publisher" -> "publisher";
            case "status" -> "status";
            case "scanstarttime" -> "started_at";
            case "scanendtime" -> "completed_at";
            default -> throw new ErrorResultException("Unsupported sortBy value: " + sortBy, HttpStatus.BAD_REQUEST);
        };
    }

    /**
     * Creates a Sort object for the given field and direction.
     * For scanEndTime, uses compound sort: completed_at (nulls first) then started_at as fallback.
     * Nulls first ensures in-progress scans (no end date) appear at the top.
     */
    private Sort createSort(String sortBy, boolean ascending) {
        var direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        var normalizedSortBy = sortBy == null ? "scanendtime" : sortBy.toLowerCase(Locale.ROOT);
        
        if ("scanendtime".equals(normalizedSortBy)) {
            var completedAtOrder = new Sort.Order(direction, "completed_at")
                .with(Sort.NullHandling.NULLS_FIRST);
            var startedAtOrder = new Sort.Order(direction, "started_at");
            return Sort.by(completedAtOrder, startedAtOrder);
        }
        
        var dbSortField = toDbSortField(sortBy);
        return Sort.by(direction, dbSortField);
    }

    /**
     * Returns distinct values that can be used to filter the scan list.
     */
    @GetMapping(
        path = "/filterOptions",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get scan filter options")
    @ApiResponse(
        responseCode = "200",
        description = "Lists of unique values usable for scan filtering",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ScanFilterOptionsJson.class)
        )
    )
    public ResponseEntity<ScanFilterOptionsJson> getScanFilterOptions() {
        try {
            admins.checkAdminUser();

            var options = new ScanFilterOptionsJson();

            options.setValidationTypes(repositories.findDistinctValidationFailureCheckTypes());

            options.setThreatScannerNames(repositories.findDistinctThreatScannerTypes());

            return ResponseEntity.ok(options);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ScanFilterOptionsJson.class);
        }
    }

    /**
     * Get a specific scan by its ID.
     * Returns detailed information about a single scan.
     */
    @GetMapping(
        path = "/{scanId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get a specific scan by ID")
    @ApiResponse(
        responseCode = "200",
        description = "Scan details",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ScanResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Scan not found",
        content = @Content()
    )
    public ResponseEntity<ScanResultJson> getScan(
        @PathVariable @Parameter(description = "Scan ID", example = "123") long scanId
    ) {
        try {
            admins.checkAdminUser();

            var scan = repositories.findExtensionScan(scanId);
            if (scan == null) {
                throw new ErrorResultException("Scan not found: " + scanId, HttpStatus.NOT_FOUND);
            }

            var result = toScanResultJson(scan);

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            throw new org.springframework.web.server.ResponseStatusException(
                exc.getStatus() != null ? (HttpStatus) exc.getStatus() : HttpStatus.BAD_REQUEST,
                exc.getMessage()
            );
        }
    }

    /**
     * Make security decisions for one or more quarantined scans.
     * Only valid for scans with QUARANTINED status.
     * Pass a single scanId for individual decisions, or multiple scanIds for bulk operations.
     * 
     * When a scan is allowed:
     * - The extension is automatically activated
     * - The scan status is updated to PASSED
     * - File decisions are created to add enforced threat files to allow list
     * 
     * When a scan is blocked:
     * - The extension remains inactive
     * - File decisions are created to add enforced threat files to block list
     */
    @PostMapping(
        path = "/decisions",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Make security decisions for quarantined scans")
    @ApiResponse(
        responseCode = "200",
        description = "Decisions processed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ScanDecisionResponseJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request or scan not in quarantined status",
        content = @Content()
    )
    public ResponseEntity<ScanDecisionResponseJson> makeScanDecisions(
        @RequestBody ScanDecisionRequest request
    ) {
        try {
            var adminUser = admins.checkAdminUser();

            if (request.getScanIds() == null || request.getScanIds().isEmpty()) {
                throw new ErrorResultException("Scan IDs are required", HttpStatus.BAD_REQUEST);
            }
            if (request.getDecision() == null || request.getDecision().isBlank()) {
                throw new ErrorResultException("Decision is required", HttpStatus.BAD_REQUEST);
            }
            
            var decisionValue = parseDecision(request.getDecision());
            
            var results = new java.util.ArrayList<ScanDecisionResultJson>();
            int successful = 0;
            int failed = 0;
            
            for (var scanIdStr : request.getScanIds()) {
                try {
                    var scanId = Long.parseLong(scanIdStr);
                    var scan = repositories.findExtensionScan(scanId);
                    
                    if (scan == null) {
                        results.add(ScanDecisionResultJson.failure(scanIdStr, "Scan not found"));
                        failed++;
                        continue;
                    }
                    
                    if (scan.getStatus() != ScanStatus.QUARANTINED) {
                        results.add(ScanDecisionResultJson.failure(scanIdStr, 
                            "Scan not in quarantined status: " + formatScanStatus(scan.getStatus())));
                        failed++;
                        continue;
                    }
                    
                    var existingDecision = repositories.findAdminScanDecisionByScanId(scanId);
                    if (existingDecision != null) {
                        results.add(ScanDecisionResultJson.failure(scanIdStr, 
                            "Decision already exists: " + existingDecision.getDecision()));
                        failed++;
                        continue;
                    }
                    
                    AdminScanDecision decision;
                    if (AdminScanDecision.ALLOWED.equals(decisionValue)) {
                        decision = AdminScanDecision.allowed(scan, adminUser);
                    } else {
                        decision = AdminScanDecision.blocked(scan, adminUser);
                    }
                    repositories.saveAdminScanDecision(decision);
                    
                    var threats = repositories.findExtensionThreats(scan).toList();
                    for (var threat : threats) {
                        if (threat.isEnforced() && threat.getFileHash() != null) {
                            createOrUpdateFileDecision(threat, scan, decisionValue, adminUser);
                        }
                    }
                    
                    // If allowed, activate the extension
                    boolean activated = false;
                    if (AdminScanDecision.ALLOWED.equals(decisionValue)) {
                        activated = completionService.adminAllowScan(scan);
                    }
                    
                    // Log the admin decision to /admin/log
                    var logMessage = formatDecisionLogMessage(scan, decisionValue, threats.size(), activated);
                    logs.logAction(adminUser, ResultJson.success(logMessage));
                    
                    results.add(ScanDecisionResultJson.success(scanIdStr));
                    successful++;
                } catch (NumberFormatException e) {
                    results.add(ScanDecisionResultJson.failure(scanIdStr, "Invalid scan ID format"));
                    failed++;
                } catch (Exception e) {
                    results.add(ScanDecisionResultJson.failure(scanIdStr, "Failed to create scan decision"));
                    failed++;
                }
            }
            
            var response = new ScanDecisionResponseJson();
            response.setProcessed(request.getScanIds().size());
            response.setSuccessful(successful);
            response.setFailed(failed);
            response.setResults(results);
            
            return ResponseEntity.ok(response);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ScanDecisionResponseJson.class);
        }
    }

    private String parseDecision(String decision) {
        var normalized = decision.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "allowed", "allow" -> AdminScanDecision.ALLOWED;
            case "blocked", "block" -> AdminScanDecision.BLOCKED;
            default -> throw new ErrorResultException(
                "Invalid decision value: " + decision + ". Must be 'allowed' or 'blocked'",
                HttpStatus.BAD_REQUEST
            );
        };
    }

    /**
     * Format a log message for a scan decision.
     */
    private String formatDecisionLogMessage(ExtensionScan scan, String decisionValue, int threatCount, boolean activated) {
        var action = AdminScanDecision.ALLOWED.equals(decisionValue) ? "Allowed" : "Blocked";
        var extensionId = String.format("%s.%s v%s", 
            scan.getNamespaceName(), 
            scan.getExtensionName(), 
            scan.getExtensionVersion());
        
        var details = new java.util.ArrayList<String>();
        if (threatCount > 0) {
            details.add(String.format("%d threat%s reviewed", threatCount, threatCount == 1 ? "" : "s"));
        }
        if (AdminScanDecision.ALLOWED.equals(decisionValue)) {
            details.add(activated ? "extension activated" : "activation failed");
        }
        
        var detailsStr = details.isEmpty() ? "" : " (" + String.join(", ", details) + ")";
        
        return String.format("%s scan #%d for extension %s%s", 
            action, scan.getId(), extensionId, detailsStr);
    }

    /**
     * Create or update a file decision for an enforced threat.
     * Maps the admin's scan decision to a file-level allow/block list entry.
     */
    private void createOrUpdateFileDecision(
            ExtensionThreat threat,
            ExtensionScan scan,
            String decisionValue,
            UserData adminUser
    ) {
        var fileHash = threat.getFileHash();
        
        // Map scan decision to file decision
        var fileDecisionValue = AdminScanDecision.ALLOWED.equals(decisionValue)
                ? FileDecision.ALLOWED
                : FileDecision.BLOCKED;
        
        var existingDecision = repositories.findFileDecisionByHash(fileHash);
        if (existingDecision != null) {
            existingDecision.setDecision(fileDecisionValue);
            existingDecision.setDecidedBy(adminUser);
            existingDecision.setDecidedAt(TimeUtil.getCurrentUTC());
            repositories.saveFileDecision(existingDecision);
            return;
        }
        
        // Create new file decision with context from threat and scan
        var fileDecision = new FileDecision();
        fileDecision.setFileHash(fileHash);
        fileDecision.setFileName(threat.getFileName());
        fileDecision.setFileType(threat.getFileExtension());
        fileDecision.setDecision(fileDecisionValue);
        fileDecision.setDecidedBy(adminUser);
        fileDecision.setDecidedAt(TimeUtil.getCurrentUTC());
        fileDecision.setNamespaceName(scan.getNamespaceName());
        fileDecision.setExtensionName(scan.getExtensionName());
        fileDecision.setDisplayName(scan.getExtensionDisplayName());
        fileDecision.setPublisher(scan.getPublisher());
        fileDecision.setVersion(scan.getExtensionVersion());
        fileDecision.setScan(scan);
        repositories.saveFileDecision(fileDecision);
    }

    /**
     * Converts an ExtensionScan entity to a ScanResultJson DTO.
     * Populates all fields including extension metadata, dates, and validation failures.
     */
    private ScanResultJson toScanResultJson(ExtensionScan scan) {
        var json = new ScanResultJson();

        json.setId(String.valueOf(scan.getId()));
        json.setNamespace(scan.getNamespaceName());
        json.setExtensionName(scan.getExtensionName());
        json.setVersion(scan.getExtensionVersion());
        json.setPublisher(scan.getPublisher());
        json.setPublisherUrl(scan.getPublisherUrl());
        if (scan.getExtensionDisplayName() != null) {
            json.setDisplayName(scan.getExtensionDisplayName());
        }
        json.setTargetPlatform(scan.getTargetPlatform());
        json.setUniversalTargetPlatform(scan.isUniversalTargetPlatform());

        var version = repositories.findVersion(
            scan.getExtensionVersion(),
            scan.getTargetPlatform(),
            scan.getExtensionName(),
            scan.getNamespaceName()
        );

        if (version != null) {
            populateExtensionMetadata(scan.getStatus(), json, version);
        } else if (json.getDisplayName() == null) {
            json.setDisplayName(scan.getExtensionName());
        }

        json.setStatus(formatScanStatus(scan.getStatus()));
        json.setDateScanStarted(TimeUtil.toUTCString(scan.getStartedAt()));

        if (scan.getCompletedAt() != null) {
            json.setDateScanEnded(TimeUtil.toUTCString(scan.getCompletedAt()));
        }

        if (scan.getStatus() == ScanStatus.QUARANTINED && scan.getCompletedAt() != null) {
            json.setDateQuarantined(TimeUtil.toUTCString(scan.getCompletedAt()));
        }

        if (scan.getStatus() == ScanStatus.REJECTED && scan.getCompletedAt() != null) {
            json.setDateRejected(TimeUtil.toUTCString(scan.getCompletedAt()));
        }

        if (scan.getErrorMessage() != null) {
            json.setErrorMessage(scan.getErrorMessage());
        }

        var validationFailures = repositories.findValidationFailures(scan).toList();
        if (!validationFailures.isEmpty()) {
            var failures = validationFailures.stream()
                .map(this::toValidationFailureJson)
                .collect(Collectors.toList());
            json.setValidationFailures(failures);
        }

        var threats = repositories.findExtensionThreats(scan).toList();
        if (!threats.isEmpty()) {
            var threatJsons = threats.stream()
                .map(this::toThreatJson)
                .collect(Collectors.toList());
            json.setThreats(threatJsons);
        }

        var adminDecision = repositories.findAdminScanDecisionByScanId(scan.getId());
        if (adminDecision != null) {
            json.setAdminDecision(toAdminDecisionJson(adminDecision));
        }

        // Include all check results for audit trail
        var checkResults = repositories.findScanCheckResultsByScanId(scan.getId());
        if (!checkResults.isEmpty()) {
            var checkResultJsons = checkResults.stream()
                .map(this::toCheckResultJson)
                .collect(Collectors.toList());
            json.setCheckResults(checkResultJsons);
        }

        return json;
    }

    /**
     * Converts a ScanCheckResult entity to a CheckResultJson DTO.
     */
    private CheckResultJson toCheckResultJson(ScanCheckResult checkResult) {
        var json = new CheckResultJson();
        json.setCheckType(checkResult.getCheckType());
        json.setCategory(checkResult.getCategory().name());
        json.setResult(checkResult.getResult().name());
        json.setStartedAt(TimeUtil.toUTCString(checkResult.getStartedAt()));
        if (checkResult.getCompletedAt() != null) {
            json.setCompletedAt(TimeUtil.toUTCString(checkResult.getCompletedAt()));
        }
        json.setDurationMs(checkResult.getDurationMs());
        json.setFilesScanned(checkResult.getFilesScanned());
        json.setFindingsCount(checkResult.getFindingsCount());
        json.setSummary(checkResult.getSummary());
        json.setErrorMessage(checkResult.getErrorMessage());
        json.setRequired(checkResult.getRequired());
        return json;
    }

    /**
     * Converts an ExtensionThreat entity to a ThreatJson DTO.
     */
    private ThreatJson toThreatJson(ExtensionThreat threat) {
        var json = new ThreatJson();
        json.setId(String.valueOf(threat.getId()));
        json.setType(threat.getType());
        json.setRuleName(threat.getRuleName());
        json.setReason(threat.getReason());
        json.setDateDetected(TimeUtil.toUTCString(threat.getDetectedAt()));
        json.setFileName(threat.getFileName());
        json.setFileHash(threat.getFileHash());
        json.setFileExtension(threat.getFileExtension());
        json.setSeverity(threat.getSeverity());
        json.setEnforcedFlag(threat.isEnforced());
        return json;
    }

    /**
     * Converts an AdminScanDecision entity to an AdminDecisionJson DTO.
     */
    private AdminDecisionJson toAdminDecisionJson(AdminScanDecision decision) {
        var json = new AdminDecisionJson();
        // Map ALLOWED/BLOCKED to Allowed/Blocked for display
        json.setDecision(AdminScanDecision.ALLOWED.equals(decision.getDecision()) ? "Allowed" : "Blocked");
        json.setDecidedBy(decision.getDecidedByName());
        json.setDateDecided(TimeUtil.toUTCString(decision.getDecidedAt()));
        return json;
    }

    /**
     * Populates extension metadata from the ExtensionVersion entity.
     * This includes display name, icon, and download URL.
     * Note: Publisher is set from the scan record, not from current namespace data,
     * to preserve the publisher name as it was at the time of scan.
     */
    private void populateExtensionMetadata(ScanStatus scanStatus, ScanResultJson json, ExtensionVersion version) {
        json.setDisplayName(version.getDisplayName());

        var isQuarantined = scanStatus == ScanStatus.QUARANTINED;
        var isErrored = scanStatus == ScanStatus.ERRORED;
        var fileTypes = isQuarantined ?
                new String[] { FileResource.ICON } :
                new String[] { FileResource.ICON, FileResource.DOWNLOAD };

        var fileUrls = storageUtil.getFileUrls(
            List.of(version),
            UrlUtil.getBaseUrl(),
            fileTypes
        );

        var files = fileUrls.get(version.getId());
        if (files != null) {
            var iconUrl = files.get(org.eclipse.openvsx.entities.FileResource.ICON);
            if (iconUrl != null) {
                json.setExtensionIcon(iconUrl);
            }

            // if the extension is quarantined or errored,
            // resolve the download location as the api endpoint will return 404.
            // TODO: this does not work when using local storage as in this case always an api url is returned
            if (isQuarantined || isErrored) {
                var downloadResource = repositories.findFileByType(version, FileResource.DOWNLOAD);
                if (downloadResource != null) {
                    var url = storageUtil.getLocation(downloadResource);
                    if (url != null) {
                        json.setDownloadUrl(url.toString());
                    }
                }
            } else {
                var downloadUrl = files.get(FileResource.DOWNLOAD);
                if (downloadUrl != null) {
                    json.setDownloadUrl(downloadUrl);
                }
            }
        }
    }

    private ValidationFailureJson toValidationFailureJson(org.eclipse.openvsx.entities.ExtensionValidationFailure failure) {
        var json = new ValidationFailureJson();
        json.setId(String.valueOf(failure.getId()));
        json.setType(failure.getCheckType());
        json.setRuleName(failure.getRuleName());
        json.setReason(failure.getValidationFailureReason());
        json.setDateDetected(TimeUtil.toUTCString(failure.getDetectedAt()));
        json.setEnforcedFlag(failure.isEnforced());
        return json;
    }

    /**
     * Formats the ScanStatus enum to the API-standard display string.
     * Maps internal enum values to the display strings defined in the API spec.
     */
    private String formatScanStatus(ScanStatus status) {
        return switch (status) {
            case STARTED -> "STARTED";
            case VALIDATING -> "VALIDATING";
            case SCANNING -> "SCANNING";
            case PASSED -> "PASSED";
            case QUARANTINED -> "QUARANTINED";
            case REJECTED -> "AUTO REJECTED";
            case ERRORED -> "ERROR";
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
                HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Parses one or multiple status filter values into a set of ScanStatus values.
     *
     * Supports:
     * - one value: status=PASSED
     * - multiple values (comma-separated with explode=false): status=PASSED,ERROR
     * - multiple values (repeated): status=PASSED&status=ERROR
     *
     */
    private Set<ScanStatus> parseStatusFilter(List<String> status) {
        if (status == null || status.isEmpty() || status.stream().allMatch(v -> v == null || v.isBlank())) {
            return Set.of();
        }

        var result = new java.util.HashSet<ScanStatus>();
        for (var raw : status) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            for (var token : raw.split(",")) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                result.addAll(parseSingleStatusFilter(token));
            }
        }
        return Set.copyOf(result);
    }

    /**
     * Parses a single status token (one string entry from the query list).
     */
    private Set<ScanStatus> parseSingleStatusFilter(String status) {
        return switch (status) {
            case "STARTED" -> Set.of(ScanStatus.STARTED);
            case "VALIDATING" -> Set.of(ScanStatus.VALIDATING);
            case "SCANNING" -> Set.of(ScanStatus.SCANNING);
            case "PASSED" -> Set.of(ScanStatus.PASSED);
            case "QUARANTINED" -> Set.of(ScanStatus.QUARANTINED);
            case "AUTO REJECTED" -> Set.of(ScanStatus.REJECTED);
            case "ERROR" -> Set.of(ScanStatus.ERRORED);
            default -> throw new ErrorResultException("Unknown status filter: " + status, HttpStatus.BAD_REQUEST);
        };
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    private boolean normalizeSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return false;
        }
        return switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new ErrorResultException("Unsupported sortOrder value: " + sortOrder, HttpStatus.BAD_REQUEST);
        };
    }

}

