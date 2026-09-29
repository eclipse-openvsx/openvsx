/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.adapter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;

@ExtendWith(SpringExtension.class)
class VSCodeIdUpdateServiceTest {

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    VSCodeIdService idService;

    @Autowired
    VSCodeIdUpdateService updateService;

    @Test
    void testUpdateAllNoChanges() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);
        namespace1.setPublicId(namespacePublicId1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);
        extension1.setPublicId(extensionPublicId1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId2);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId2);
        extension2.setNamespace(namespace2);

        var namespaceName3 = "baz2";
        var namespacePublicId3 = UUID.randomUUID().toString();
        var extensionName3 = "foobar2";
        var extensionPublicId3 = UUID.randomUUID().toString();

        var namespace3 = new Namespace();
        namespace3.setId(5L);
        namespace3.setName(namespaceName3);
        namespace3.setPublicId(namespacePublicId3);

        var extension3 = new Extension();
        extension3.setId(6L);
        extension3.setName(extensionName3);
        extension3.setPublicId(extensionPublicId3);
        extension3.setNamespace(namespace3);

        Mockito.when(repositories.findAllPublicIds()).thenReturn(List.of(extension1, extension2, extension3));
        Mockito.doAnswer(invocation -> {
            var extension = invocation.getArgument(0, Extension.class);
            return new PublicIds(extension.getNamespace().getPublicId(), extension.getPublicId());
        }).when(idService).getUpstreamPublicIds(Mockito.any(Extension.class));

        updateService.updateAll();
        Mockito.verify(repositories, Mockito.never()).updateExtensionPublicIds(Mockito.anyMap());
        Mockito.verify(repositories, Mockito.never()).updateNamespacePublicIds(Mockito.anyMap());
    }

    @Test
    void testUpdateAllRandomNoChanges() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);
        namespace1.setPublicId(namespacePublicId1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);
        extension1.setPublicId(extensionPublicId1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId2);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId2);
        extension2.setNamespace(namespace2);

        var namespaceName3 = "baz2";
        var namespacePublicId3 = UUID.randomUUID().toString();
        var extensionName3 = "foobar2";
        var extensionPublicId3 = UUID.randomUUID().toString();

        var namespace3 = new Namespace();
        namespace3.setId(5L);
        namespace3.setName(namespaceName3);
        namespace3.setPublicId(namespacePublicId3);

        var extension3 = new Extension();
        extension3.setId(6L);
        extension3.setName(extensionName3);
        extension3.setPublicId(extensionPublicId3);
        extension3.setNamespace(namespace3);

        var upstreamPublicIds = new PublicIds(null, null);
        Mockito.when(idService.getUpstreamPublicIds(extension1)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getUpstreamPublicIds(extension2)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getUpstreamPublicIds(extension3)).thenReturn(upstreamPublicIds);
        Mockito.when(repositories.findAllPublicIds()).thenReturn(List.of(extension1, extension2, extension3));

        updateService.updateAll();
        Mockito.verify(repositories, Mockito.never()).updateExtensionPublicIds(Mockito.anyMap());
        Mockito.verify(repositories, Mockito.never()).updateNamespacePublicIds(Mockito.anyMap());
    }

    @Test
    void testUpdateAllChange() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);
        namespace1.setPublicId(namespacePublicId1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);
        extension1.setPublicId(extensionPublicId1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId2);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId2);
        extension2.setNamespace(namespace2);

        var namespaceName3 = "baz2";
        var namespacePublicId3 = UUID.randomUUID().toString();
        var extensionName3 = "foobar2";
        var extensionPublicId3 = UUID.randomUUID().toString();

        var namespace3 = new Namespace();
        namespace3.setId(5L);
        namespace3.setName(namespaceName3);
        namespace3.setPublicId(namespacePublicId3);

        var extension3 = new Extension();
        extension3.setId(6L);
        extension3.setName(extensionName3);
        extension3.setPublicId(extensionPublicId3);
        extension3.setNamespace(namespace3);

        Mockito.when(idService.getUpstreamPublicIds(extension1)).thenReturn(new PublicIds(null, null));
        Mockito.when(idService.getUpstreamPublicIds(extension2))
                .thenReturn(new PublicIds(namespacePublicId3, extensionPublicId3));
        Mockito.when(idService.getUpstreamPublicIds(extension3)).thenReturn(new PublicIds(null, null));
        Mockito.when(repositories.findAllPublicIds()).thenReturn(List.of(extension1, extension2, extension3));

        var extensionPublicId = UUID.randomUUID().toString();
        var namespacePublicId = UUID.randomUUID().toString();
        Mockito.when(idService.getRandomPublicId()).thenReturn(extensionPublicId, namespacePublicId);

        updateService.updateAll();
        Mockito.verify(repositories, Mockito.times(1)).updateExtensionPublicIds(
                Map.of(
                        extension2.getId(),
                        extensionPublicId3,
                        extension3.getId(),
                        extensionPublicId));
        Mockito.verify(repositories, Mockito.times(1)).updateNamespacePublicIds(
                Map.of(
                        namespace2.getId(),
                        namespacePublicId3,
                        namespace3.getId(),
                        namespacePublicId));
    }

    @Test
    void testUpdateRandom() {
        var namespaceName = "foo";
        var namespacePublicId = UUID.randomUUID().toString();
        var extensionName = "bar";
        var extensionPublicId = UUID.randomUUID().toString();

        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setName(namespaceName);

        var extension = new Extension();
        extension.setId(2L);
        extension.setName(extensionName);
        extension.setNamespace(namespace);

        Mockito.when(repositories.findPublicId(namespaceName, extensionName)).thenReturn(extension);
        Mockito.when(idService.getUpstreamPublicIds(extension)).thenReturn(new PublicIds(null, null));
        Mockito.when(idService.getRandomPublicId()).thenReturn(extensionPublicId, namespacePublicId);

        updateService.update(namespaceName, extensionName);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(extension.getId()).equals(extensionPublicId);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(namespace.getId()).equals(namespacePublicId);
        }));
    }

    @Test
    void testUpdateRandomExistsDb() {
        var namespaceName = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setName(namespaceName);

        var extension = new Extension();
        extension.setId(2L);
        extension.setName(extensionName);
        extension.setNamespace(namespace);

        Mockito.when(repositories.findPublicId(namespaceName, extensionName)).thenReturn(extension);
        Mockito.when(idService.getUpstreamPublicIds(extension)).thenReturn(new PublicIds(null, null));
        Mockito.when(repositories.extensionPublicIdExists(extensionPublicId1)).thenReturn(true);
        Mockito.when(repositories.namespacePublicIdExists(namespacePublicId1)).thenReturn(true);
        Mockito.when(idService.getRandomPublicId())
                .thenReturn(extensionPublicId1, extensionPublicId2, namespacePublicId1, namespacePublicId2);

        updateService.update(namespaceName, extensionName);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(extension.getId()).equals(extensionPublicId2);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(namespace.getId()).equals(namespacePublicId2);
        }));
    }

    @Test
    void testMustUpdateRandom() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId1);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId1);
        extension2.setNamespace(namespace2);

        Mockito.when(repositories.findPublicId(namespaceName1, extensionName1)).thenReturn(extension1);
        Mockito.when(repositories.findPublicId(extensionPublicId1)).thenReturn(extension2);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId1)).thenReturn(extension2);
        var upstreamPublicIds = new PublicIds(namespacePublicId1, extensionPublicId1);
        Mockito.when(idService.getUpstreamPublicIds(extension1)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getUpstreamPublicIds(extension2)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getRandomPublicId()).thenReturn(extensionPublicId2, namespacePublicId2);

        updateService.update(namespaceName1, extensionName1);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(extension1.getId()).equals(extensionPublicId1)
                    && map.get(extension2.getId()).equals(extensionPublicId2);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(namespace1.getId()).equals(namespacePublicId1)
                    && map.get(namespace2.getId()).equals(namespacePublicId2);
        }));
    }

    @Test
    void testMustUpdateRandomExists() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId1);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId1);
        extension2.setNamespace(namespace2);

        Mockito.when(repositories.findPublicId(namespaceName1, extensionName1)).thenReturn(extension1);
        Mockito.when(repositories.findPublicId(extensionPublicId1)).thenReturn(extension2);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId1)).thenReturn(extension2);
        var upstreamPublicIds = new PublicIds(namespacePublicId1, extensionPublicId1);
        Mockito.when(idService.getUpstreamPublicIds(extension1)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getUpstreamPublicIds(extension2)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getRandomPublicId())
                .thenReturn(extensionPublicId1, extensionPublicId2, namespacePublicId1, namespacePublicId2);

        updateService.update(namespaceName1, extensionName1);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(extension1.getId()).equals(extensionPublicId1)
                    && map.get(extension2.getId()).equals(extensionPublicId2);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(namespace1.getId()).equals(namespacePublicId1)
                    && map.get(namespace2.getId()).equals(namespacePublicId2);
        }));
    }

    @Test
    void testMustUpdateRandomExistsDb() {
        var namespaceName1 = "foo";
        var namespaceUuid1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionUuid1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespaceUuid1);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionUuid1);
        extension2.setNamespace(namespace2);

        var dbExtensionPublicId = UUID.randomUUID().toString();
        var dbNamespacePublicId = UUID.randomUUID().toString();
        Mockito.when(repositories.extensionPublicIdExists(dbExtensionPublicId)).thenReturn(true);
        Mockito.when(repositories.namespacePublicIdExists(dbNamespacePublicId)).thenReturn(true);
        Mockito.when(repositories.findPublicId(namespaceName1, extensionName1)).thenReturn(extension1);
        Mockito.when(repositories.findPublicId(extensionUuid1)).thenReturn(extension2);
        Mockito.when(repositories.findNamespacePublicId(namespaceUuid1)).thenReturn(extension2);
        var upstreamPublicIds = new PublicIds(namespaceUuid1, extensionUuid1);
        Mockito.when(idService.getUpstreamPublicIds(extension1)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getUpstreamPublicIds(extension2)).thenReturn(upstreamPublicIds);
        Mockito.when(idService.getRandomPublicId())
                .thenReturn(dbExtensionPublicId, extensionPublicId2, dbNamespacePublicId, namespacePublicId2);

        updateService.update(namespaceName1, extensionName1);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(extension1.getId()).equals(extensionUuid1)
                    && map.get(extension2.getId()).equals(extensionPublicId2);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 2
                    && map.get(namespace1.getId()).equals(namespaceUuid1)
                    && map.get(namespace2.getId()).equals(namespacePublicId2);
        }));
    }

    @Test
    void testUpdateNoChange() {
        var namespaceName = "foo";
        var namespacePublicId = UUID.randomUUID().toString();
        var extensionName = "bar";
        var extensionPublicId = UUID.randomUUID().toString();

        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setName(namespaceName);
        namespace.setPublicId(namespacePublicId);

        var extension = new Extension();
        extension.setId(2L);
        extension.setName(extensionName);
        extension.setNamespace(namespace);
        extension.setPublicId(extensionPublicId);

        Mockito.when(repositories.findPublicId(namespaceName, extensionName)).thenReturn(extension);
        Mockito.when(repositories.findPublicId(extensionPublicId)).thenReturn(extension);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId)).thenReturn(extension);
        Mockito.when(idService.getUpstreamPublicIds(extension))
                .thenReturn(new PublicIds(namespacePublicId, extensionPublicId));

        updateService.update(namespaceName, extensionName);
        Mockito.verify(repositories, Mockito.never()).updateExtensionPublicIds(Mockito.anyMap());
        Mockito.verify(repositories, Mockito.never()).updateNamespacePublicIds(Mockito.anyMap());
    }

    @Test
    void testUpdateUpstream() {
        var namespaceName = "foo";
        var namespacePublicId = "123-456-789";
        var extensionName = "bar";
        var extensionPublicId = "abc-def-ghi";

        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setName(namespaceName);
        namespace.setPublicId("zzz-zzz-zzz");

        var extension = new Extension();
        extension.setId(2L);
        extension.setName(extensionName);
        extension.setPublicId("000-000-000");
        extension.setNamespace(namespace);

        Mockito.when(repositories.findPublicId(namespaceName, extensionName)).thenReturn(extension);
        Mockito.when(idService.getUpstreamPublicIds(extension))
                .thenReturn(new PublicIds(namespacePublicId, extensionPublicId));

        updateService.update(namespaceName, extensionName);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(extension.getId()).equals(extensionPublicId);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 1 && map.get(namespace.getId()).equals(namespacePublicId);
        }));
    }

    @Test
    void testUpdateDuplicateRecursive() {
        var namespaceName1 = "foo";
        var namespacePublicId1 = UUID.randomUUID().toString();
        var extensionName1 = "bar";
        var extensionPublicId1 = UUID.randomUUID().toString();

        var namespace1 = new Namespace();
        namespace1.setId(1L);
        namespace1.setName(namespaceName1);

        var extension1 = new Extension();
        extension1.setId(2L);
        extension1.setName(extensionName1);
        extension1.setNamespace(namespace1);

        var namespaceName2 = "baz";
        var namespacePublicId2 = UUID.randomUUID().toString();
        var extensionName2 = "foobar";
        var extensionPublicId2 = UUID.randomUUID().toString();

        var namespace2 = new Namespace();
        namespace2.setId(3L);
        namespace2.setName(namespaceName2);
        namespace2.setPublicId(namespacePublicId1);

        var extension2 = new Extension();
        extension2.setId(4L);
        extension2.setName(extensionName2);
        extension2.setPublicId(extensionPublicId1);
        extension2.setNamespace(namespace2);

        var namespaceName3 = "baz2";
        var namespacePublicId3 = UUID.randomUUID().toString();
        var extensionName3 = "foobar2";
        var extensionPublicId3 = UUID.randomUUID().toString();

        var namespace3 = new Namespace();
        namespace3.setId(5L);
        namespace3.setName(namespaceName3);
        namespace3.setPublicId(namespacePublicId2);

        var extension3 = new Extension();
        extension3.setId(6L);
        extension3.setName(extensionName3);
        extension3.setPublicId(extensionPublicId2);
        extension3.setNamespace(namespace3);

        var namespaceName4 = "baz3";
        var namespacePublicId4 = UUID.randomUUID().toString();
        var extensionName4 = "foobar3";
        var extensionPublicId4 = UUID.randomUUID().toString();

        var namespace4 = new Namespace();
        namespace4.setId(7L);
        namespace4.setName(namespaceName4);
        namespace4.setPublicId(namespacePublicId3);

        var extension4 = new Extension();
        extension4.setId(8L);
        extension4.setName(extensionName4);
        extension4.setPublicId(extensionPublicId3);
        extension4.setNamespace(namespace4);

        Mockito.when(repositories.findPublicId(namespaceName1, extensionName1)).thenReturn(extension1);
        Mockito.when(repositories.findPublicId(extensionPublicId1)).thenReturn(extension2);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId1)).thenReturn(extension2);
        Mockito.when(repositories.findPublicId(extensionPublicId2)).thenReturn(extension3);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId2)).thenReturn(extension3);
        Mockito.when(repositories.findPublicId(extensionPublicId3)).thenReturn(extension4);
        Mockito.when(repositories.findNamespacePublicId(namespacePublicId3)).thenReturn(extension4);
        Mockito.when(idService.getUpstreamPublicIds(extension1))
                .thenReturn(new PublicIds(namespacePublicId1, extensionPublicId1));
        Mockito.when(idService.getUpstreamPublicIds(extension2))
                .thenReturn(new PublicIds(namespacePublicId2, extensionPublicId2));
        Mockito.when(idService.getUpstreamPublicIds(extension3))
                .thenReturn(new PublicIds(namespacePublicId3, extensionPublicId3));
        Mockito.when(idService.getUpstreamPublicIds(extension4))
                .thenReturn(new PublicIds(namespacePublicId4, extensionPublicId4));

        updateService.update(namespaceName1, extensionName1);
        Mockito.verify(repositories).updateExtensionPublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 4
                    && map.get(extension1.getId()).equals(extensionPublicId1)
                    && map.get(extension2.getId()).equals(extensionPublicId2)
                    && map.get(extension3.getId()).equals(extensionPublicId3)
                    && map.get(extension4.getId()).equals(extensionPublicId4);
        }));
        Mockito.verify(repositories).updateNamespacePublicIds(Mockito.argThat((Map<Long, String> map) -> {
            return map.size() == 4
                    && map.get(namespace1.getId()).equals(namespacePublicId1)
                    && map.get(namespace2.getId()).equals(namespacePublicId2)
                    && map.get(namespace3.getId()).equals(namespacePublicId3)
                    && map.get(namespace4.getId()).equals(namespacePublicId4);
        }));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        VSCodeIdUpdateService vsCodeIdUpdateService(RepositoryService repositories, VSCodeIdService service) {
            return new VSCodeIdUpdateService(repositories, service);
        }
    }
}
