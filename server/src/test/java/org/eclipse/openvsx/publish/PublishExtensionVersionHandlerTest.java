/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.publish;

import jakarta.persistence.EntityManager;
import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishExtensionVersionHandlerTest {

    @Mock
    PublishExtensionVersionService publishService;

    @Mock
    ExtensionVersionIntegrityService integrityService;

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    @Mock
    JobRequestScheduler scheduler;

    @Mock
    UserService users;

    @Mock
    ExtensionValidator validator;

    @Mock
    ExtensionControlService extensionControl;

    @Mock
    ExtensionScanService scanService;

    private PublishExtensionVersionHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new PublishExtensionVersionHandler(
                publishService,
                integrityService,
                entityManager,
                repositories,
                scheduler,
                users,
                validator,
                extensionControl,
                scanService
        );
        
        // Lenient: not all tests need this mock
        org.mockito.Mockito.lenient()
            .when(extensionControl.getMaliciousExtensionIds())
            .thenReturn(Collections.emptyList());
    }

    @Test
    void shouldCreateExtensionWhenNamespaceExists() {
        // Happy path: extension version gets persisted.
        var processor = org.mockito.Mockito.mock(ExtensionProcessor.class);
        when(processor.getNamespace()).thenReturn("publisher");
        when(processor.getExtensionName()).thenReturn("demo");
        when(processor.getVersion()).thenReturn("2.0.0");
        when(processor.getExtensionDependencies()).thenReturn(List.of());
        when(processor.getBundledExtensions()).thenReturn(List.of());

        var metadata = new ExtensionVersion();
        metadata.setDisplayName("Demo OK");
        metadata.setVersion("2.0.0");
        metadata.setTargetPlatform("any");
        when(processor.getMetadata()).thenReturn(metadata);

        var namespace = buildNamespace("publisher");
        var user = new org.eclipse.openvsx.entities.UserData();
        var token = new PersonalAccessToken();
        token.setUser(user);

        when(repositories.findNamespace("publisher")).thenReturn(namespace);
        when(users.hasPublishPermission(user, namespace)).thenReturn(true);
        when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
        when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
        when(validator.validateMetadata(metadata)).thenReturn(List.of());
        when(repositories.findExtension("demo", namespace)).thenReturn(null);

        var capturedExtension = ArgumentCaptor.forClass(Extension.class);

        var result = handler.createExtensionVersion(processor, token, LocalDateTime.now(), false);

        verify(entityManager).persist(capturedExtension.capture());
        verify(entityManager).persist(metadata);
        assertThat(result).isSameAs(metadata);
        assertThat(result.getPublishedWith()).isEqualTo(token);
        assertThat(result.getExtension()).isSameAs(capturedExtension.getValue());
        assertThat(result.getExtension().getNamespace()).isSameAs(namespace);
    }

    @Test
    void shouldFailWhenNamespaceDoesNotExist() {
        // When namespace doesn't exist, handler should throw an error.
        var processor = org.mockito.Mockito.mock(ExtensionProcessor.class);
        when(processor.getNamespace()).thenReturn("unknown");

        var user = new org.eclipse.openvsx.entities.UserData();
        var token = new PersonalAccessToken();
        token.setUser(user);

        when(repositories.findNamespace("unknown")).thenReturn(null);

        assertThatThrownBy(() -> handler.createExtensionVersion(processor, token, LocalDateTime.now(), false))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Unknown publisher");
    }

    private Namespace buildNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        return namespace;
    }
}

