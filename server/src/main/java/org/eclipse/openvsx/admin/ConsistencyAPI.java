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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.consistency.ConsistencyCheckService;
import org.eclipse.openvsx.consistency.ConsistencyCheckSummary;
import org.eclipse.openvsx.consistency.ConsistencyFinding;
import org.eclipse.openvsx.json.ConsistencyCheckJson;
import org.eclipse.openvsx.json.ConsistencyCheckListJson;
import org.eclipse.openvsx.json.ConsistencyFindingJson;
import org.eclipse.openvsx.json.ConsistencyFindingListJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;

/**
 * Admin dashboard endpoints for the data consistency checks (see #1622): a live overview of every
 * registered {@link org.eclipse.openvsx.consistency.ConsistencyCheck}, its findings, and actions to fix
 * them - one at a time or all at once. There is no "run now" action here: findings are always
 * recomputed live, and the scheduled sweep that auto-fixes what it can runs independently of this page.
 */
@RestController
@RequestMapping("/admin/consistency")
@ApiResponse(
    responseCode = "403",
    description = "Administration role is required",
    content = @Content()
)
public class ConsistencyAPI {

    private final AdminService admins;
    private final ConsistencyCheckService service;

    public ConsistencyAPI(AdminService admins, ConsistencyCheckService service) {
        this.admins = admins;
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an overview of every registered consistency check")
    public ResponseEntity<ConsistencyCheckListJson> listChecks() {
        try {
            admins.checkAdminUser();
            var json = new ConsistencyCheckListJson();
            json.setChecks(
                    service.listSummaries().stream()
                            .map(ConsistencyAPI::toJson)
                            .toList());
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ConsistencyCheckListJson.class);
        }
    }

    @GetMapping(path = "/{checkId}/findings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current findings of one consistency check")
    public ResponseEntity<ConsistencyFindingListJson> findings(@PathVariable String checkId) {
        try {
            admins.checkAdminUser();
            var json = new ConsistencyFindingListJson();
            json.setFindings(
                    service.findings(checkId).stream()
                            .map(ConsistencyAPI::toJson)
                            .toList());
            return ResponseEntity.ok(json);
        } catch (NotFoundException exc) {
            var json = ConsistencyFindingListJson.error("Unknown consistency check: " + checkId);
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ConsistencyFindingListJson.class);
        }
    }

    @PostMapping(path = "/{checkId}/fix", produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    @Operation(summary = "Fix every current finding of one consistency check")
    public ResponseEntity<ResultJson> fixAll(@PathVariable String checkId) {
        try {
            admins.checkAdminUser();
            var fixed = service.fixAll(checkId);
            return ResponseEntity.ok(ResultJson.success("Fixed " + fixed + " finding(s) for check '" + checkId + "'."));
        } catch (NotFoundException exc) {
            return new ResponseEntity<>(
                    ResultJson.error("Unknown consistency check: " + checkId),
                    HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @PostMapping(path = "/{checkId}/fix/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    @Operation(summary = "Fix a single finding of one consistency check")
    public ResponseEntity<ResultJson> fixOne(@PathVariable String checkId, @PathVariable long entityId) {
        try {
            admins.checkAdminUser();
            service.fixOne(checkId, entityId);
            return ResponseEntity.ok(ResultJson.success("Fixed entity " + entityId + " for check '" + checkId + "'."));
        } catch (NotFoundException exc) {
            return new ResponseEntity<>(
                    ResultJson.error("Unknown consistency check: " + checkId),
                    HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    private static ConsistencyCheckJson toJson(ConsistencyCheckSummary summary) {
        var json = new ConsistencyCheckJson();
        json.setId(summary.id());
        json.setName(summary.name());
        json.setDescription(summary.description());
        json.setCurrentFindingsCount(summary.currentFindingsCount());
        return json;
    }

    private static ConsistencyFindingJson toJson(ConsistencyFinding finding) {
        var json = new ConsistencyFindingJson();
        json.setEntityId(finding.entityId());
        json.setLabel(finding.label());
        json.setDetail(finding.detail());
        return json;
    }
}
