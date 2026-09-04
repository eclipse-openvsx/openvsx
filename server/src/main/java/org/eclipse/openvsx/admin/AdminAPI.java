/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.admin;

import java.net.URI;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.LocalRegistryService;
import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersistedLog;
import org.eclipse.openvsx.json.AdminStatisticsJson;
import org.eclipse.openvsx.json.BulkPublisherRevokeRequestJson;
import org.eclipse.openvsx.json.BulkPublisherRevokeResponseJson;
import org.eclipse.openvsx.json.ChangeNamespaceJson;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.NamespaceMembershipListJson;
import org.eclipse.openvsx.json.PersistedLogJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.SearchIndexJson;
import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.json.StatsJson;
import org.eclipse.openvsx.json.TargetPlatformVersionJson;
import org.eclipse.openvsx.json.UserPublishInfoJson;
import org.eclipse.openvsx.json.UserRelationshipsJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.NamespaceOwnershipCheckScanner;
import org.eclipse.openvsx.search.SearchIndexStats;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.settings.SettingsService;
import org.eclipse.openvsx.util.*;

@RestController
@RequestMapping("/admin")
@ApiResponse(
    responseCode = "403",
    description = "Administration role is required",
    content = @Content()
)
public class AdminAPI {

    private final RepositoryService repositories;
    private final AdminService admins;
    private final ExtensionService extensions;
    private final SettingsService settings;
    private final LogService logs;
    private final LocalRegistryService local;
    private final SearchUtilService search;

    public AdminAPI(
            RepositoryService repositories,
            AdminService admins,
            ExtensionService extensions,
            SettingsService settings,
            LogService logs,
            LocalRegistryService local,
            SearchUtilService search
    ) {
        this.repositories = repositories;
        this.admins = admins;
        this.extensions = extensions;
        this.settings = settings;
        this.logs = logs;
        this.local = local;
        this.search = search;
    }

    @GetMapping(
        path = "/report",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get the admin report for the given month and year")
    @ApiResponse(
        responseCode = "200",
        description = "The report is returned",
        content = {
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AdminStatisticsJson.class)
            ),
            @Content(mediaType = "text/csv", schema = @Schema(type = "string"))
        }
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content()
    )
    public ResponseEntity<AdminStatisticsJson> getReportJson(
            @RequestParam("token")
            @Parameter(description = "A personal access token") String tokenValue,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        try {
            var statistics = getReport(tokenValue, year, month);
            return ResponseEntity.ok(statistics.toJson());
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(AdminStatisticsJson.class);
        }
    }

    @GetMapping(
        path = "/report",
        produces = "text/csv"
    )
    @CrossOrigin
    @Operation(hidden = true)
    public ResponseEntity<String> getReportCsv(
            @RequestParam("token") String tokenValue,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        try {
            var statistics = getReport(tokenValue, year, month);
            return ResponseEntity.ok(statistics.toCsv());
        } catch (ErrorResultException exc) {
            return ResponseEntity.status(exc.getStatus()).body(exc.getMessage());
        }
    }

    private AdminStatistics getReport(String tokenValue, int year, int month) {
        admins.checkAdminUser(tokenValue);
        return admins.getAdminStatistics(year, month);
    }

    /**
     * Session-authenticated counterpart to {@code /admin/report}, for the admin dashboard.
     * <p>
     * {@code /admin/report} takes an access token as a request parameter and is therefore listed in
     * SecurityConfig's permitAll block, which is why it can't simply be reused from a logged-in
     * browser session. It stays as it is - scripts depend on it - and this serves the same data the
     * way every other endpoint under {@code /admin/} does, through the session.
     */
    @GetMapping(
        path = "/statistics",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get the admin statistics for the given month and year")
    @ApiResponse(
        responseCode = "200",
        description = "The statistics are returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AdminStatisticsJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "The year or month is invalid, or lies in the future",
        content = @Content(schema = @Schema(implementation = AdminStatisticsJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No statistics were archived for the given month",
        content = @Content()
    )
    public ResponseEntity<AdminStatisticsJson> getStatistics(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        try {
            admins.checkAdminUser();
            return ResponseEntity.ok(admins.getAdminStatistics(year, month).toJson());
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(AdminStatisticsJson.class);
        }
    }

    /**
     * The same data as CSV, on its own path rather than by content negotiation so the dashboard's
     * download can be a plain link - a browser navigation can't set an Accept header. The
     * Content-Disposition names the file, which a bare string response wouldn't.
     */
    @GetMapping(
        path = "/statistics/csv",
        produces = "text/csv"
    )
    @Operation(hidden = true, summary = "Get the admin statistics for the given month and year as CSV")
    @ApiResponse(responseCode = "200", description = "The statistics are returned as CSV")
    public ResponseEntity<String> getStatisticsCsv(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        try {
            admins.checkAdminUser();
            var csv = admins.getAdminStatistics(year, month).toCsv();
            var fileName = String.format("openvsx-statistics-%d-%02d.csv", year, month);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(csv);
        } catch (ErrorResultException exc) {
            return ResponseEntity.status(exc.getStatus()).body(exc.getMessage());
        }
    }

    @GetMapping(
        path = "/stats",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Retrieve basic registry statistics")
    @ApiResponse(
        responseCode = "200",
        description = "Statistics are returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = StatsJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = StatsJson.class))
    )
    public ResponseEntity<StatsJson> getStats() {
        try {
            admins.checkAdminUser();

            var json = new StatsJson();
            json.setUserCount(repositories.countUsers());
            json.setExtensionCount(repositories.countExtensions());
            json.setNamespaceCount(repositories.countNamespaces());
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(StatsJson.class);
        }
    }

    @GetMapping(
        path = "/user/search",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Search for users")
    @ApiResponse(
        responseCode = "200",
        description = "Matching users are returned as a paginated list in JSON format"
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned"
    )
    public ResponseEntity<Page<UserRelationshipsJson>> getUsers(
            @RequestParam(name = "query", required = false)
            @Parameter(description = "Search query for login name or full name") String query,
            Pageable pageable,
            @RequestParam(name = "role", required = false)
            @Parameter(
                description = "Filter by role",
                schema = @Schema(allowableValues = { "admin", "privileged" })
            ) String role
    ) {
        try {
            admins.checkAdminUser();
            return ResponseEntity.ok(admins.searchUsers(query, role, pageable));
        } catch (ErrorResultException exc) {
            var status = exc.getStatus() != null ? exc.getStatus() : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status);
        }
    }

    @GetMapping(
        path = "/log",
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    @Operation(hidden = true, summary = "Get the persisted activity log as plain text")
    @ApiResponse(
        responseCode = "200",
        description = "Log entries are returned as plain text"
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned"
    )
    public String getLog(
            @RequestParam(name = "period", required = false)
            @Parameter(
                description = "ISO 8601 period to filter log entries, e.g. P1D for the last day"
            ) String periodString
    ) {
        try {
            admins.checkAdminUser();

            Streamable<PersistedLog> logs;
            if (StringUtils.isEmpty(periodString)) {
                logs = repositories.findAllPersistedLogs();
            } else {
                try {
                    var period = Period.parse(periodString);
                    var now = TimeUtil.getCurrentUTC();
                    logs = repositories.findPersistedLogsAfter(now.minus(period));
                } catch (DateTimeParseException exc) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
                }
            }
            return logs.stream()
                    .map(this::toString)
                    .collect(Collectors.joining("\n")) + "\n";
        } catch (ErrorResultException exc) {
            var status = exc.getStatus() != null ? exc.getStatus() : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status);
        }
    }

    @GetMapping(
        path = "/logs",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get the persisted activity log as a paginated list")
    @ApiResponse(
        responseCode = "200",
        description = "Log entries are returned as a paginated list in JSON format"
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned"
    )
    public ResponseEntity<Page<PersistedLogJson>> getLog(
            Pageable pageable,
            @RequestParam(name = "period", required = false)
            @Parameter(
                description = "ISO 8601 period to filter log entries, e.g. P1D for the last day"
            ) String periodString
    ) {
        try {
            admins.checkAdminUser();

            if (pageable.getPageSize() > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must not exceed 1000");
            }

            Page<PersistedLog> logsPage;
            if (StringUtils.isEmpty(periodString)) {
                logsPage = repositories.findPersistedLogsPaginated(pageable);
            } else {
                try {
                    var period = Period.parse(periodString);
                    var now = TimeUtil.getCurrentUTC();
                    logsPage = repositories.findPersistedLogsAfterPaginated(now.minus(period), pageable);
                } catch (DateTimeParseException _) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
                }
            }

            return ResponseEntity.ok(logsPage.map(log -> {
                var timestamp = log.getTimestamp().minusNanos(log.getTimestamp().getNano());
                return new PersistedLogJson(timestamp.toString(), log.getUser().getLoginName(), log.getMessage());
            }));
        } catch (ErrorResultException exc) {
            var status = exc.getStatus() != null ? exc.getStatus() : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status);
        }
    }

    private String toString(PersistedLog log) {
        var timestamp = log.getTimestamp().minusNanos(log.getTimestamp().getNano());
        return timestamp + "\t" + log.getUser().getLoginName() + "\t" + log.getMessage();
    }

    @GetMapping(
        path = "/search-index",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Report the state of the search index")
    @ApiResponse(
        responseCode = "200",
        description = "The state of the search index is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SearchIndexJson.class)
        )
    )
    public ResponseEntity<SearchIndexJson> getSearchIndex() {
        try {
            admins.checkAdminUser();

            var stats = search.getIndexStats();
            var json = new SearchIndexJson();
            json.setEnabled(stats.enabled());
            json.setImplementation(stats.implementation());
            json.setIndexExists(stats.indexExists());
            json.setIndexedDocuments(stats.indexedDocuments());
            json.setActiveExtensions(stats.activeExtensions());
            json.setMaxResultWindow(stats.maxResultWindow());
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SearchIndexJson.class);
        }
    }

    @PostMapping(
        path = "/update-search-index",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Trigger a full update of the search index")
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> updateSearchIndex() {
        try {
            var adminUser = admins.checkAdminUser();

            // Only elasticsearch has an index to rebuild. Without this the call falls through to
            // SearchUtilService's default implementation, which is elasticsearch, and attempts index
            // operations against an engine this registry is not using - or reports having rebuilt the
            // database engine's index, which does not exist.
            var stats = search.getIndexStats();
            if (!SearchIndexStats.ELASTICSEARCH.equals(stats.implementation()) || !stats.enabled()) {
                throw new ErrorResultException(
                        "There is no search index to rebuild: searches are answered by '"
                                + stats.implementation() + "'.");
            }

            search.updateSearchIndex(true);

            var result = ResultJson.success("Updated search index");
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/extension/{namespaceName}/{extensionName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get an extension")
    @ApiResponse(
        responseCode = "200",
        description = "Extension data is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ExtensionJson.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content(schema = @Schema(implementation = ExtensionJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ExtensionJson.class))
    )
    public ResponseEntity<ExtensionJson> getExtension(
            @PathVariable
            @Parameter(description = "Namespace name", example = "julialang") String namespaceName,
            @PathVariable
            @Parameter(description = "Extension name", example = "language-julia") String extensionName
    ) {
        try {
            admins.checkAdminUser();
            ExtensionJson json;
            var latest = repositories.findLatestVersion(namespaceName, extensionName, null, false, false);
            if (latest != null) {
                json = local.toExtensionVersionJson(latest, null, false);
                json.setAllTargetPlatformVersions(
                        repositories.findTargetPlatformsGroupedByVersion(latest.getExtension()));
                json.setActive(latest.getExtension().isActive());
                json.setRemoved(latest.isExtensionRemoved());
                json.setNamespaceOwnershipConflict(
                        repositories.hasThreatOfType(latest, NamespaceOwnershipCheckScanner.TYPE));
            } else {
                var extension = repositories.findExtension(extensionName, namespaceName);
                if (extension == null) {
                    var error = "Extension not found: " + NamingUtil.toExtensionId(namespaceName, extensionName);
                    throw new ErrorResultException(error, HttpStatus.NOT_FOUND);
                }

                json = new ExtensionJson();
                json.setNamespace(extension.getNamespace().getName());
                json.setName(extension.getName());
                json.setAllVersions(Collections.emptyMap());
                json.setAllTargetPlatformVersions(Collections.emptyList());
                json.setDeprecated(extension.isDeprecated());
                json.setActive(extension.isActive());
            }
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ExtensionJson.class);
        }
    }

    @PostMapping(
        path = "/api/extension/{namespaceName}/{extensionName}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Delete an extension or one or multiple extension versions")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> deleteExtension(
            @PathVariable
            @Parameter(description = "Namespace name", example = "julialang") String namespaceName,
            @PathVariable
            @Parameter(description = "Extension name", example = "language-julia") String extensionName,
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue,
            @RequestBody(required = false) List<TargetPlatformVersionJson> targetVersions
    ) {
        try {
            var adminUser = admins.checkAdminUser(tokenValue);
            var targets = CollectionUtil.toArray(
                    targetVersions,
                    TargetPlatformVersionJson::toTargetPlatformVersion,
                    TargetPlatformVersion[]::new);
            var result = admins.deleteExtensionNoWait(adminUser, namespaceName, extensionName, targets);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/extension/{namespaceName}/{extensionName}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Delete an extension or one or multiple extension versions")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> deleteExtension(
            @PathVariable
            @Parameter(description = "Namespace name", example = "julialang") String namespaceName,
            @PathVariable
            @Parameter(description = "Extension name", example = "language-julia") String extensionName,
            @RequestBody List<TargetPlatformVersionJson> targetVersions
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var targets = CollectionUtil.toArray(
                    targetVersions,
                    TargetPlatformVersionJson::toTargetPlatformVersion,
                    TargetPlatformVersion[]::new);
            var result = admins.deleteExtensionNoWait(adminUser, namespaceName, extensionName, targets);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/api/extension/{namespaceName}/{extensionName}/purge",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(
        summary = "Permanently purge an extension or one or multiple extension versions",
        description = "Unlike delete, this physically removes the versions from the database and storage, "
                + "freeing their identities for republishing. Extension versions are otherwise immutable."
    )
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> purgeExtension(
            @PathVariable
            @Parameter(description = "Namespace name", example = "julialang") String namespaceName,
            @PathVariable
            @Parameter(description = "Extension name", example = "language-julia") String extensionName,
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue,
            @RequestBody(required = false) List<TargetPlatformVersionJson> targetVersions
    ) {
        try {
            var adminUser = admins.checkAdminUser(tokenValue);
            var targets = CollectionUtil.toArray(
                    targetVersions,
                    TargetPlatformVersionJson::toTargetPlatformVersion,
                    TargetPlatformVersion[]::new);
            var result = extensions.purgeExtensionNoWait(adminUser, namespaceName, extensionName, targets);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/extension/{namespaceName}/{extensionName}/purge",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Permanently purge an extension or one or multiple extension versions")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> purgeExtension(
            @PathVariable
            @Parameter(description = "Namespace name", example = "julialang") String namespaceName,
            @PathVariable
            @Parameter(description = "Extension name", example = "language-julia") String extensionName,
            @RequestBody List<TargetPlatformVersionJson> targetVersions
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var targets = CollectionUtil.toArray(
                    targetVersions,
                    TargetPlatformVersionJson::toTargetPlatformVersion,
                    TargetPlatformVersion[]::new);
            var result = extensions.purgeExtensionNoWait(adminUser, namespaceName, extensionName, targets);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/extension/{namespace}/{extension}/review/{provider}/{loginName}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(hidden = true, summary = "Delete a review for an extension by a user")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Extension not found",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "404",
        description = "Review not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> deleteReview(
            @PathVariable String namespace,
            @PathVariable String extension,
            @PathVariable String provider,
            @PathVariable String loginName
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var result = admins.deleteReview(namespace, extension, loginName, provider);
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/user/{provider}/{loginName}/role",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Update the role of a user")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> updateUserRole(
            @PathVariable String provider,
            @PathVariable String loginName,
            @RequestParam
            @Parameter(
                description = "The role to assign to the user, or 'none' to remove their role",
                schema = @Schema(allowableValues = { "admin", "privileged", "none" })
            ) String role
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            return ResponseEntity.ok(admins.updateUserRole(provider, loginName, role, adminUser));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/namespace/{namespaceName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get information about a namespace")
    @ApiResponse(
        responseCode = "200",
        description = "Namespace data is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NamespaceJson.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Namespace not found",
        content = @Content(schema = @Schema(implementation = NamespaceJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = NamespaceJson.class))
    )
    public ResponseEntity<NamespaceJson> getNamespace(
            @PathVariable
            @Parameter(description = "Namespace name", example = "mtxr") String namespaceName
    ) {
        try {
            admins.checkAdminUser();

            // Admins see all extensions of the namespace, including inactive/soft-deleted ones.
            var namespace = local.getNamespace(namespaceName, true);
            var adminNamespaceUrl = createAdminNamespaceUrl(namespace);
            namespace.setMembersUrl(UrlUtil.createApiUrl(adminNamespaceUrl, "members"));
            namespace.setRoleUrl(UrlUtil.createApiUrl(adminNamespaceUrl, "change-member"));
            // TODO: decide do we have admin API for this
            // namespace.setTrustedPublishingUrl(UrlUtil.createApiUrl(adminNamespaceUrl, "trusted-publishing"));
            return ResponseEntity.ok(namespace);
        } catch (NotFoundException exc) {
            var json = NamespaceJson.error("Namespace not found: " + namespaceName);
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NamespaceJson.class);
        }
    }

    private String createAdminNamespaceUrl(NamespaceJson namespace) {
        return UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), "admin", "namespace", namespace.getName());
    }

    @PostMapping(
        path = "/create-namespace",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Create a new namespace")
    @MutatingOperation
    @ApiResponse(
        responseCode = "201",
        description = "Namespace created, a success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> createNamespace(@RequestBody NamespaceJson namespace) {
        try {
            admins.checkAdminUser();
            var json = admins.createNamespace(namespace);
            var url = createAdminNamespaceUrl(namespace);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create(url))
                    .body(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @DeleteMapping(
        path = "/namespace/{namespaceName}"
    )
    @Operation(hidden = true, summary = "Delete a namespace")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format"
    )
    @ApiResponse(
        responseCode = "403",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> deleteNamespace(@PathVariable String namespaceName) {
        try {
            var adminUser = admins.checkAdminUser();
            return ResponseEntity.ok(admins.deleteNamespace(namespaceName, adminUser));
        } catch (NotFoundException exc) {
            var json = NamespaceJson.error("Namespace not found: " + namespaceName);
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ResultJson.class);
        }
    }

    @PostMapping(
        path = "/change-namespace",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Schedule a namespace rename")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> changeNamespace(@RequestBody ChangeNamespaceJson json) {
        try {
            admins.checkAdminUser();
            admins.changeNamespace(json);
            return ResponseEntity.ok(
                    ResultJson.success(
                            "Scheduled namespace change from '" + json.oldNamespace() + "' to '" + json.newNamespace()
                                    + "'.\nIt can take 15 minutes to a couple hours for the change to become visible."));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/api/namespace/{namespaceName}/members",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Get members for a namespace")
    @ApiResponse(
        responseCode = "200",
        description = "The namespace membership list is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NamespaceMembershipListJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = NamespaceMembershipListJson.class))
    )
    public ResponseEntity<NamespaceMembershipListJson> getNamespaceMembers(
            @PathVariable
            @Parameter(description = "Namespace name", example = "mtxr") String namespaceName,
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue
    ) {
        try {
            admins.checkAdminUser(tokenValue);
            var memberships = repositories.findMemberships(namespaceName);
            var membershipList = new NamespaceMembershipListJson();
            membershipList.setNamespaceMemberships(memberships.stream().map(NamespaceMembership::toJson).toList());
            return ResponseEntity.ok(membershipList);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NamespaceMembershipListJson.class);
        }
    }

    @GetMapping(
        path = "/namespace/{namespaceName}/members",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get members of a namespace")
    @ApiResponse(
        responseCode = "200",
        description = "The namespace membership list is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = NamespaceMembershipListJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = NamespaceMembershipListJson.class))
    )
    public ResponseEntity<NamespaceMembershipListJson> getNamespaceMembers(
            @PathVariable
            @Parameter(description = "Namespace name", example = "mtxr") String namespaceName
    ) {
        try {
            admins.checkAdminUser();
            var memberships = repositories.findMemberships(namespaceName);
            var membershipList = new NamespaceMembershipListJson();
            membershipList.setNamespaceMemberships(memberships.stream().map(NamespaceMembership::toJson).toList());
            return ResponseEntity.ok(membershipList);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(NamespaceMembershipListJson.class);
        }
    }

    @PostMapping(
        path = "/api/namespace/{namespaceName}/change-member",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Edit a member of a namespace")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> editNamespaceMember(
            @PathVariable
            @Parameter(description = "Namespace name", example = "BeardedBear") String namespaceName,
            @RequestParam("user")
            @Parameter(description = "User name") String userName,
            @RequestParam(required = false)
            @Parameter(description = "Login provider name", example = "github") String provider,
            @RequestParam
            @Parameter(
                description = "The role to assign to the user or remove the user from the namespace",
                schema = @Schema(
                    allowableValues = { NamespaceMembership.ROLE_CONTRIBUTOR, NamespaceMembership.ROLE_OWNER, "remove" }
                )
            ) String role,
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue
    ) {
        try {
            var adminUser = admins.checkAdminUser(tokenValue);
            var result = admins.editNamespaceMember(namespaceName, userName, provider, role, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/namespace/{namespaceName}/change-member",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Edit a member of a namespace")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> editNamespaceMember(
            @PathVariable
            @Parameter(description = "Namespace name", example = "BeardedBear") String namespaceName,
            @RequestParam("user")
            @Parameter(description = "User name") String userName,
            @RequestParam(required = false)
            @Parameter(description = "Login provider name", example = "github") String provider,
            @RequestParam
            @Parameter(
                description = "The role to assign to the user or remove the user from the namespace",
                schema = @Schema(
                    allowableValues = { NamespaceMembership.ROLE_CONTRIBUTOR, NamespaceMembership.ROLE_OWNER, "remove" }
                )
            ) String role
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var result = admins.editNamespaceMember(namespaceName, userName, provider, role, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/publisher/{provider}/{loginName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get publishing information for a user")
    @ApiResponse(
        responseCode = "200",
        description = "The user publish info is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = UserPublishInfoJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = UserPublishInfoJson.class))
    )
    public ResponseEntity<UserPublishInfoJson> getUserPublishInfo(
            @PathVariable
            @Parameter(description = "Login provider name", example = "github") String provider,
            @PathVariable
            @Parameter(description = "User login name") String loginName
    ) {
        try {
            admins.checkAdminUser();
            var userPublishInfo = admins.getUserPublishInfo(provider, loginName);
            return ResponseEntity.ok(userPublishInfo);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(UserPublishInfoJson.class);
        }
    }

    @PostMapping(
        path = "/publisher/{provider}/{loginName}/revoke",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Revoke all publish contributions of a user")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> revokePublisherContributions(
            @PathVariable
            @Parameter(description = "User login name") String loginName,
            @PathVariable
            @Parameter(description = "Login provider name", example = "github") String provider
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var result = admins.revokePublisherContributions(provider, loginName, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/api/publisher/bulk-revoke",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Bulk revoke publisher contributions")
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = BulkPublisherRevokeResponseJson.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<BulkPublisherRevokeResponseJson> revokeBulkPublishers(
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue,
            @RequestBody BulkPublisherRevokeRequestJson request
    ) {
        if (request.publishers().size() > 100) {
            var json = BulkPublisherRevokeResponseJson.error("Max number of revocations requested exceeded (100).");
            return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
        }
        try {
            var adminUser = admins.checkAdminUser(tokenValue);

            var resultMap = new HashMap<String, ResultJson>();
            for (var publisher : request.publishers()) {
                var key = "%s:%s".formatted(publisher.loginName(), publisher.provider());
                try {
                    var result = admins.revokePublisherContributions(
                            publisher.provider(),
                            publisher.loginName(),
                            adminUser,
                            request.reason());
                    resultMap.put(key, result);
                } catch (ErrorResultException exc) {
                    resultMap.put(key, exc.toResponseEntity().getBody());
                }
            }

            return ResponseEntity.ok(new BulkPublisherRevokeResponseJson(resultMap));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(BulkPublisherRevokeResponseJson.class);
        }
    }

    @PostMapping(
        path = "/publisher/{provider}/{loginName}/tokens/revoke",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Revoke all access tokens of a user")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ResultJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    public ResponseEntity<ResultJson> revokePublisherTokens(
            @PathVariable
            @Parameter(description = "User login name") String loginName,
            @PathVariable
            @Parameter(description = "Login provider name", example = "github") String provider
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var result = admins.revokePublisherTokens(provider, loginName, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/api/publisher/{provider}/{username}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Forget a user in response to a data-protection erasure request")
    @MutatingOperation
    @ApiResponse(
        responseCode = "200",
        description = "A success message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "An administration token is required",
        content = @Content(schema = @Schema(implementation = ResultJson.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = @Content()
    )
    public ResponseEntity<ResultJson> forgetUser(
            @PathVariable
            @Parameter(description = "Authentication provider", example = "github") String provider,
            @PathVariable
            @Parameter(description = "Provider-specific username") String username,
            @RequestParam(value = "token")
            @Parameter(description = "A personal access token") String tokenValue
    ) {
        try {
            var adminUser = admins.checkAdminUser(tokenValue);
            var result = admins.forgetUser(provider, username, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(
        path = "/publisher/{provider}/{authId}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> forgetUser(
            @PathVariable String provider,
            @PathVariable String authId
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var result = admins.forgetUser(provider, authId, adminUser);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/settings",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Get the current registry settings")
    @ApiResponse(
        responseCode = "200",
        description = "The current settings are returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SettingsJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = SettingsJson.class))
    )
    public ResponseEntity<SettingsJson> getSettings() {
        try {
            admins.checkAdminUser();
            return ResponseEntity.ok(settings.getCurrentSettings());
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SettingsJson.class);
        }
    }

    @PutMapping(
        path = "/settings",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(hidden = true, summary = "Update registry settings")
    @ApiResponse(
        responseCode = "200",
        description = "The updated settings are returned in JSON format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = SettingsJson.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "An error message is returned in JSON format",
        content = @Content(schema = @Schema(implementation = SettingsJson.class))
    )
    public ResponseEntity<SettingsJson> updateSettings(@RequestBody SettingsJson newSettings) {
        try {
            var adminUser = admins.checkAdminUser();

            var changes = settings.updateFromJson(newSettings);
            var json = settings.getCurrentSettings();
            json.setSuccess("Updated settings: " + changes);
            logs.logAction(adminUser, json);

            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SettingsJson.class);
        }
    }
}
