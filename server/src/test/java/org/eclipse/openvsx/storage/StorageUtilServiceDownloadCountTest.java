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
package org.eclipse.openvsx.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.search.SearchUtilService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Request-path download counting against a real database, where concurrent downloads of the same
 * extension can interleave.
 */
@SpringBootTest
class StorageUtilServiceDownloadCountTest extends AbstractPostgresContainerTest {

    @Autowired
    StorageUtilService storageUtilService;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    SearchUtilService search;

    private final List<Object> seededEntities = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        inTransaction(() -> {
            seededEntities.reversed().forEach(entity -> entityManager.remove(entityManager.merge(entity)));
            return null;
        });
        seededEntities.clear();
    }

    @Test
    void increaseDownloadCount_doesNotLoseAConcurrentDownload() throws InterruptedException {
        var resource = seedDownload("dlcount", "dlcount.ext-1.0.0.vsix");
        var extensionId = resource.getExtension().getExtension().getId();

        var staleLoaded = new CountDownLatch(1);
        var otherCommitted = new CountDownLatch(1);
        var staleFailure = new AtomicReference<Throwable>();
        var stale = new Thread(() -> {
            try {
                inTransaction(() -> {
                    // loads the counter before the concurrent download below commits its increase
                    var managed = entityManager.find(FileResource.class, resource.getId());
                    managed.getExtension().getExtension().getDownloadCount();
                    staleLoaded.countDown();
                    await(otherCommitted);
                    storageUtilService.increaseDownloadCount(managed);
                    return null;
                });
            } catch (Throwable t) {
                staleFailure.set(t);
            }
        });
        stale.start();
        staleLoaded.await();

        storageUtilService.increaseDownloadCount(resource);
        otherCommitted.countDown();
        stale.join();

        assertThat(staleFailure.get()).isNull();
        assertThat(inTransaction(() -> entityManager.find(Extension.class, extensionId).getDownloadCount()))
                .isEqualTo(2);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private FileResource seedDownload(String namespaceName, String vsixFilename) {
        return inTransaction(() -> {
            var namespace = new Namespace();
            namespace.setName(namespaceName);
            entityManager.persist(namespace);

            var extension = new Extension();
            extension.setName(namespaceName + "-ext");
            extension.setNamespace(namespace);
            extension.setActive(true);
            entityManager.persist(extension);

            var publisher = new UserData();
            publisher.setLoginName(namespaceName + "-publisher");
            entityManager.persist(publisher);

            var extVersion = new ExtensionVersion();
            extVersion.setVersion("1.0.0");
            extVersion.setTargetPlatform("universal");
            extVersion.setExtension(extension);
            extVersion.setActive(true);
            extVersion.setPublishedBy(publisher);
            entityManager.persist(extVersion);

            var resource = new FileResource();
            resource.setName(vsixFilename);
            resource.setType(FileResource.DOWNLOAD);
            resource.setStorageType(FileResource.STORAGE_LOCAL);
            resource.setExtension(extVersion);
            entityManager.persist(resource);

            seededEntities.addAll(List.of(namespace, extension, publisher, extVersion, resource));
            return resource;
        });
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
