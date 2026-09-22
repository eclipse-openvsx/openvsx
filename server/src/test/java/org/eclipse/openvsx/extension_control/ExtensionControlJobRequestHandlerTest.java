/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.extension_control;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.SettingsService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExtensionControlJobRequestHandler}, focusing on the daily job feeding its
 * freshly fetched malicious-extension list into {@link ExtensionControlService}'s cache, since that
 * cache would otherwise drift for up to its own, independent TTL.
 */
@ExtendWith(MockitoExtension.class)
class ExtensionControlJobRequestHandlerTest {

    @Mock
    SettingsService settings;

    @Mock
    AdminService admin;

    @Mock
    ExtensionControlService service;

    @Mock
    RepositoryService repositories;

    ExtensionControlJobRequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExtensionControlJobRequestHandler(settings, admin, service, repositories);
    }

    @Test
    void refreshesMaliciousExtensionIdsCacheWithFreshlyFetchedList() throws Exception {
        when(service.createExtensionControlUser()).thenReturn(new UserData());
        when(service.getExtensionControlJson()).thenReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.evil", "ns.other"], "deprecated": {}}
                """));

        handler.run(new HandlerJobRequest<>());

        verify(service).refreshMaliciousExtensionIds(List.of("ns.evil", "ns.other"));
    }

    @Test
    void purgesMaliciousExtensionThatExists() throws Exception {
        var extensionControlUser = new UserData();
        when(service.createExtensionControlUser()).thenReturn(extensionControlUser);
        when(repositories.hasExtension("ns", "evil")).thenReturn(true);
        when(service.getExtensionControlJson()).thenReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.evil"], "deprecated": {}}
                """));

        handler.run(new HandlerJobRequest<>());

        verify(admin).purgeExtension(extensionControlUser, "ns", "evil");
        verify(admin, never()).purgeExtensionAndReferencingExtensions(any(), any(), any());
    }

    @Test
    void refreshesCacheBeforeAttemptingPurges() throws Exception {
        var extensionControlUser = new UserData();
        when(service.createExtensionControlUser()).thenReturn(extensionControlUser);
        when(repositories.hasExtension("ns", "evil")).thenReturn(true);
        when(service.getExtensionControlJson()).thenReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.evil"], "deprecated": {}}
                """));
        doThrow(new ErrorResultException("boom"))
                .when(admin)
                .purgeExtension(extensionControlUser, "ns", "evil");

        handler.run(new HandlerJobRequest<>());

        // A persistent purge failure (this job has retries = 0) must never keep the freshly fetched
        // list from reaching the publish-time cache.
        var order = inOrder(service, admin);
        order.verify(service).refreshMaliciousExtensionIds(List.of("ns.evil"));
        order.verify(admin).purgeExtension(extensionControlUser, "ns", "evil");
    }

    @Test
    void continuesProcessingRemainingExtensionsAfterAPurgeFails() throws Exception {
        var extensionControlUser = new UserData();
        when(service.createExtensionControlUser()).thenReturn(extensionControlUser);
        when(repositories.hasExtension("ns", "evil")).thenReturn(true);
        when(repositories.hasExtension("ns", "alsoEvil")).thenReturn(true);
        when(service.getExtensionControlJson()).thenReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.evil", "ns.alsoEvil"], "deprecated": {}}
                """));
        doThrow(new ErrorResultException("boom"))
                .when(admin)
                .purgeExtension(extensionControlUser, "ns", "evil");

        handler.run(new HandlerJobRequest<>());

        verify(admin).purgeExtension(extensionControlUser, "ns", "alsoEvil");
    }
}
