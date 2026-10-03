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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.SizeOverrideJson;
import org.eclipse.openvsx.json.SizeOverrideListJson;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;

@RestController
@RequestMapping("/admin/size-overrides")
public class ExtensionSizeOverrideAPI {

    private final Logger logger = LoggerFactory.getLogger(ExtensionSizeOverrideAPI.class);

    private final AdminService admins;
    private final LogService logs;
    private final ExtensionSizeLimitService limits;

    public ExtensionSizeOverrideAPI(AdminService admins, LogService logs, ExtensionSizeLimitService limits) {
        this.admins = admins;
        this.logs = logs;
        this.limits = limits;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SizeOverrideListJson> getSizeOverrides() {
        try {
            admins.checkAdminUser();
            var json = limits.listOverrides().stream().map(ExtensionSizeOverrideAPI::toJson).toList();
            return ResponseEntity.ok(new SizeOverrideListJson(json));
        } catch (ErrorResultException exc) {
            // Not exc.toResponseEntity(SizeOverrideListJson.class): that overload is
            // <T extends ResultJson>, and the list type is a record.
            return ResponseEntity.status(exc.getStatus()).build();
        } catch (Exception exc) {
            logger.error("failed retrieving size overrides", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
        path = "/create",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<SizeOverrideJson> createSizeOverride(@RequestBody SizeOverrideJson request) {
        try {
            var adminUser = admins.checkAdminUser();
            var created = limits.createOverride(request.getNamespace(), request.getExtension(), request.getMaxSize());
            var result = toJson(created);
            result.setSuccess(
                    "Created size override for " + scopeOf(created) + " at " + created.getMaxSize() + " bytes");
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SizeOverrideJson.class);
        } catch (Exception exc) {
            logger.error("failed creating size override", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(
        path = "/{id}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<SizeOverrideJson> updateSizeOverride(
            @PathVariable long id,
            @RequestBody SizeOverrideJson request
    ) {
        try {
            var adminUser = admins.checkAdminUser();
            var updated = limits.updateOverride(id, request.getMaxSize());
            var result = toJson(updated);
            result.setSuccess(
                    "Updated size override for " + scopeOf(updated) + " to " + updated.getMaxSize() + " bytes");
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(SizeOverrideJson.class);
        } catch (Exception exc) {
            logger.error("failed updating size override {}", id, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @MutatingOperation
    public ResponseEntity<ResultJson> deleteSizeOverride(@PathVariable long id) {
        try {
            var adminUser = admins.checkAdminUser();
            var deleted = limits.deleteOverride(id);
            var result = ResultJson.success("Deleted size override for " + scopeOf(deleted));
            logs.logAction(adminUser, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        } catch (Exception exc) {
            logger.error("failed deleting size override {}", id, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static SizeOverrideJson toJson(ExtensionSizeOverride override) {
        var json = new SizeOverrideJson();
        json.setId(override.getId());
        json.setNamespace(override.getScopeNamespace().getName());
        var extension = override.getScopeExtension();
        json.setExtension(extension == null ? null : extension.getName());
        json.setMaxSize(override.getMaxSize());
        return json;
    }

    private static String scopeOf(ExtensionSizeOverride override) {
        var extension = override.getScopeExtension();
        var namespaceName = override.getScopeNamespace().getName();
        return extension == null ? namespaceName : namespaceName + "." + extension.getName();
    }
}
