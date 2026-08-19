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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.TimeUtil;

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

    private static final String CHECK_TYPE = SimilarityCheckService.CHECK_TYPE;

    /** The extension is live: it exists and has at least one active version. */
    private static final String STATE_PUBLISHED = "PUBLISHED";

    /** The extension exists but all of its versions have been deactivated. */
    private static final String STATE_DEACTIVATED = "DEACTIVATED";

    /** Publication was blocked by the check, so the extension was never created. */
    private static final String STATE_REJECTED = "REJECTED";

    private final RepositoryService repositories;
    private final AdminService admins;
    private final LogService logs;

    public NameSquattingAPI(RepositoryService repositories, AdminService admins, LogService logs) {
        this.repositories = repositories;
        this.admins = admins;
        this.logs = logs;
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

            var normalizedPublisher = normalizeSearch(publisher);
            var normalizedNamespace = normalizeSearch(namespace);
            var normalizedName = normalizeSearch(name);
            var stateFilter = parseStateFilter(state);
            var detectedFrom = parseUtcDateTime(dateDetectedFrom, "dateDetectedFrom");
            var detectedTo = parseUtcDateTime(dateDetectedTo, "dateDetectedTo");
            var ascending = normalizeSortOrder(sortOrder);

            var totalSize = repositories.countFlaggedExtensions(
                    CHECK_TYPE,
                    normalizedNamespace,
                    normalizedPublisher,
                    normalizedName,
                    detectedFrom,
                    detectedTo,
                    stateFilter);

            var keys = size == 0
                    ? List.<String>of()
                    : repositories.findFlaggedExtensionKeys(
                            CHECK_TYPE,
                            normalizedNamespace,
                            normalizedPublisher,
                            normalizedName,
                            detectedFrom,
                            detectedTo,
                            stateFilter,
                            ascending,
                            size,
                            offset);

            var flags = new ArrayList<NameSquattingFlagJson>();
            for (var key : keys) {
                var flag = toFlagJson(key, detectedFrom, detectedTo);
                if (flag != null) {
                    flags.add(flag);
                }
            }

            var result = new NameSquattingFlagListJson();
            result.setOffset(offset);
            result.setTotalSize((int) totalSize);
            result.setFlags(flags);

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

            var normalizedPublisher = normalizeSearch(publisher);
            var normalizedNamespace = normalizeSearch(namespace);
            var normalizedName = normalizeSearch(name);
            var detectedFrom = parseUtcDateTime(dateDetectedFrom, "dateDetectedFrom");
            var detectedTo = parseUtcDateTime(dateDetectedTo, "dateDetectedTo");

            var counts = new NameSquattingCountsJson();
            counts.setTotal(
                    countWithState(normalizedNamespace, normalizedPublisher, normalizedName,
                            detectedFrom, detectedTo, null));
            counts.setPublished(
                    countWithState(normalizedNamespace, normalizedPublisher, normalizedName,
                            detectedFrom, detectedTo,
                            new ExtensionStateFilter(true, false, false)));
            counts.setDeactivated(
                    countWithState(normalizedNamespace, normalizedPublisher, normalizedName,
                            detectedFrom, detectedTo,
                            new ExtensionStateFilter(false, true, false)));
            counts.setRejected(
                    countWithState(normalizedNamespace, normalizedPublisher, normalizedName,
                            detectedFrom, detectedTo,
                            new ExtensionStateFilter(false, false, true)));

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
            var targets = requireTargets(request);

            var results = new ArrayList<NameSquattingActionResultJson>();
            for (var target : targets) {
                results.add(clearFindings(adminUser, target));
            }

            return ResponseEntity.ok(toActionResponse(results));
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
            var targets = requireTargets(request);

            var results = new ArrayList<NameSquattingActionResultJson>();
            for (var target : targets) {
                results.add(deleteExtension(adminUser, target));
            }

            return ResponseEntity.ok(toActionResponse(results));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NameSquattingActionResponseJson.class);
        }
    }

    private NameSquattingActionResultJson clearFindings(UserData adminUser, NameSquattingTargetJson target) {
        var namespaceName = target.getNamespace();
        var extensionName = target.getExtension();
        try {
            var cleared = repositories.deleteValidationFailures(CHECK_TYPE, namespaceName, extensionName);
            if (cleared == 0) {
                return NameSquattingActionResultJson.failure(
                        namespaceName,
                        extensionName,
                        "No name squatting findings are recorded for this extension");
            }

            var message = String.format(
                    "Cleared %d name squatting finding%s for extension %s.%s as a false positive",
                    cleared,
                    cleared == 1 ? "" : "s",
                    namespaceName,
                    extensionName);
            logs.logAction(adminUser, ResultJson.success(message));

            return NameSquattingActionResultJson.success(namespaceName, extensionName, message);
        } catch (ErrorResultException exc) {
            return NameSquattingActionResultJson.failure(namespaceName, extensionName, exc.getMessage());
        }
    }

    private NameSquattingActionResultJson deleteExtension(UserData adminUser, NameSquattingTargetJson target) {
        var namespaceName = target.getNamespace();
        var extensionName = target.getExtension();

        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            return NameSquattingActionResultJson.failure(
                    namespaceName,
                    extensionName,
                    "Extension does not exist, its publication was blocked by the check");
        }

        var targetVersions = activeTargetVersions(extension);
        if (targetVersions.length == 0) {
            return NameSquattingActionResultJson.failure(
                    namespaceName,
                    extensionName,
                    "Extension has no active versions left to deactivate");
        }

        try {
            var result = admins.deleteExtensionNoWait(
                    adminUser,
                    extension.getNamespace().getName(),
                    extension.getName(),
                    targetVersions);
            if (result != null && result.getError() != null) {
                return NameSquattingActionResultJson.failure(namespaceName, extensionName, result.getError());
            }

            var message = String.format(
                    "Deactivated %d version%s of extension %s.%s flagged for name squatting",
                    targetVersions.length,
                    targetVersions.length == 1 ? "" : "s",
                    extension.getNamespace().getName(),
                    extension.getName());
            logs.logAction(adminUser, ResultJson.success(message));

            return NameSquattingActionResultJson.success(namespaceName, extensionName, message);
        } catch (ErrorResultException exc) {
            return NameSquattingActionResultJson.failure(namespaceName, extensionName, exc.getMessage());
        }
    }

    /**
     * Build the response row for one {@code <namespace>/<extension>} key, or null when its findings
     * were cleared between listing the keys and reading them back.
     */
    private @Nullable NameSquattingFlagJson toFlagJson(
            String key,
            @Nullable LocalDateTime detectedFrom,
            @Nullable LocalDateTime detectedTo
    ) {
        var separator = key.indexOf('/');
        if (separator < 0) {
            return null;
        }
        var namespaceName = key.substring(0, separator);
        var extensionName = key.substring(separator + 1);

        var failures = repositories.findValidationFailures(
                CHECK_TYPE,
                namespaceName,
                extensionName,
                detectedFrom,
                detectedTo);
        if (failures.isEmpty()) {
            return null;
        }

        // Failures come back newest first, so the first one carries the most recent metadata.
        var latestScan = failures.getFirst().getScan();
        var extension = repositories.findExtension(extensionName, namespaceName);

        var json = new NameSquattingFlagJson();
        json.setNamespace(latestScan.getNamespaceName());
        json.setExtensionName(latestScan.getExtensionName());
        json.setDisplayName(
                latestScan.getExtensionDisplayName() != null
                        ? latestScan.getExtensionDisplayName()
                        : latestScan.getExtensionName());
        json.setPublisher(latestScan.getPublisher());
        json.setPublisherUrl(latestScan.getPublisherUrl());
        json.setFindingCount(failures.size());
        json.setDateLastDetected(TimeUtil.toUTCString(failures.getFirst().getDetectedAt()));
        json.setDateFirstDetected(TimeUtil.toUTCString(failures.getLast().getDetectedAt()));
        json.setFindings(failures.stream().map(this::toFindingJson).toList());

        if (extension == null) {
            json.setState(STATE_REJECTED);
            json.setActiveVersionCount(0);
        } else {
            var activeVersions = (int) repositories.findActiveVersions(extension).stream().count();
            json.setActiveVersionCount(activeVersions);
            json.setState(extension.isActive() && activeVersions > 0 ? STATE_PUBLISHED : STATE_DEACTIVATED);
        }

        return json;
    }

    private NameSquattingFindingJson toFindingJson(ExtensionValidationFailure failure) {
        var scan = failure.getScan();
        var json = new NameSquattingFindingJson();
        json.setId(String.valueOf(failure.getId()));
        json.setScanId(String.valueOf(scan.getId()));
        json.setVersion(scan.getExtensionVersion());
        json.setTargetPlatform(scan.getTargetPlatform());
        json.setScanStatus(formatScanStatus(scan.getStatus()));
        json.setRuleName(failure.getRuleName());
        json.setReason(failure.getValidationFailureReason());
        json.setDateDetected(TimeUtil.toUTCString(failure.getDetectedAt()));
        json.setEnforcedFlag(failure.isEnforced());
        return json;
    }

    private TargetPlatformVersion[] activeTargetVersions(Extension extension) {
        return repositories.findActiveVersions(extension).stream()
                .map(version -> new TargetPlatformVersion(version.getTargetPlatform(), version.getVersion()))
                .distinct()
                .toArray(TargetPlatformVersion[]::new);
    }

    private int countWithState(
            @Nullable String namespace,
            @Nullable String publisher,
            @Nullable String name,
            @Nullable LocalDateTime detectedFrom,
            @Nullable LocalDateTime detectedTo,
            @Nullable ExtensionStateFilter stateFilter
    ) {
        return (int) repositories.countFlaggedExtensions(
                CHECK_TYPE,
                namespace,
                publisher,
                name,
                detectedFrom,
                detectedTo,
                stateFilter);
    }

    private List<NameSquattingTargetJson> requireTargets(NameSquattingActionRequest request) {
        var targets = request.getTargets();
        if (targets == null || targets.isEmpty()) {
            throw new ErrorResultException("At least one extension is required", HttpStatus.BAD_REQUEST);
        }
        for (var target : targets) {
            if (target == null
                    || target.getNamespace() == null || target.getNamespace().isBlank()
                    || target.getExtension() == null || target.getExtension().isBlank()) {
                throw new ErrorResultException(
                        "Each extension must have a namespace and an extension name",
                        HttpStatus.BAD_REQUEST);
            }
        }
        return targets;
    }

    private NameSquattingActionResponseJson toActionResponse(List<NameSquattingActionResultJson> results) {
        var successful = (int) results.stream().filter(NameSquattingActionResultJson::isSuccess).count();

        var response = new NameSquattingActionResponseJson();
        response.setProcessed(results.size());
        response.setSuccessful(successful);
        response.setFailed(results.size() - successful);
        response.setResults(results);
        return response;
    }

    /**
     * Which extensions to include, by what became of them after the check ran. All false means no
     * filtering; see {@code NameSquattingFlagJson.state} for what each state means.
     */
    public record ExtensionStateFilter(
            boolean filterPublished,
            boolean filterDeactivated,
            boolean filterRejected
    ) {
        public boolean hasFilter() {
            return filterPublished || filterDeactivated || filterRejected;
        }
    }

    private @Nullable ExtensionStateFilter parseStateFilter(@Nullable List<String> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }

        var published = false;
        var deactivated = false;
        var rejected = false;
        for (var raw : state) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (var token : raw.split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                switch (token.trim().toUpperCase(Locale.ROOT)) {
                    case STATE_PUBLISHED -> published = true;
                    case STATE_DEACTIVATED -> deactivated = true;
                    case STATE_REJECTED -> rejected = true;
                    default -> throw new ErrorResultException(
                            "Unknown state filter: " + token.trim(),
                            HttpStatus.BAD_REQUEST);
                }
            }
        }

        var filter = new ExtensionStateFilter(published, deactivated, rejected);
        return filter.hasFilter() ? filter : null;
    }

    private @Nullable String normalizeSearch(@Nullable String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private boolean normalizeSortOrder(@Nullable String sortOrder) {
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

    private @Nullable LocalDateTime parseUtcDateTime(@Nullable String raw, String paramName) {
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

    /**
     * Formats the scan status the same way the scan API does, so the admin dashboard shows one
     * vocabulary across both views.
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
}
