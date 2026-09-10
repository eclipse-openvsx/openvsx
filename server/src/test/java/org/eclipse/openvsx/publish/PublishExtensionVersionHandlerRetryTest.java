/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.io.IOException;
import java.util.concurrent.Executor;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TempFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What {@code publishAsync} does when the work it runs after the request has been answered fails.
 * <p>
 * Built on a real proxy rather than a bare handler, because that is where the behaviour lives: both
 * {@code @Async} and {@code @Retryable} are advice, and #1529 moved {@code @Retryable} onto a private
 * method where no proxy could ever apply it, which turned the retry off without changing a line of the
 * logic. Nothing failed as a result - it just quietly stopped happening. These tests are here so that it
 * cannot happen again unnoticed.
 * <p>
 * The executor is synchronous so an {@code @Async} call runs on the calling thread and the assertions do
 * not have to wait for anything.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PublishExtensionVersionHandlerRetryTest.TestConfig.class)
@MockitoBean(
    types = {
        PublishExtensionVersionService.class,
        ExtensionVersionIntegrityService.class,
        EntityManager.class,
        RepositoryService.class,
        JobRequestScheduler.class,
        UserService.class,
        ExtensionValidator.class,
        ExtensionControlService.class,
        ExtensionScanService.class,
        ExtensionService.class
    }
)
class PublishExtensionVersionHandlerRetryTest {

    /** One attempt plus the three retries {@code @Retryable} allows by default. */
    private static final int ATTEMPTS = 4;

    @Autowired
    PublishExtensionVersionHandler handler;

    @Autowired
    PublishExtensionVersionService publishService;

    @Autowired
    ExtensionScanService scanService;

    @Autowired
    ExtensionService extensionService;

    private static TempFile givenExtensionFile() throws IOException {
        var namespace = new Namespace();
        namespace.setName("foo");

        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);

        var download = new FileResource();
        download.setExtension(extVersion);

        var extensionFile = new TempFile("test", ".vsix");
        extensionFile.setResource(download);
        return extensionFile;
    }

    // A storage hiccup is the case the retry was put there for, and the reason doPublish clears the
    // version's file resources before it starts: an attempt has to be able to follow a half-done one.
    @Test
    void retriesAFailedPublish() throws IOException {
        doThrow(new RuntimeException("storage is having a moment")).when(publishService).storeResource(any());

        try (var extensionFile = givenExtensionFile()) {
            handler.publishAsync(extensionFile, extensionService);
        }

        verify(publishService, times(ATTEMPTS)).storeResource(any());
    }

    // An OutOfMemoryError is what #1450 hit, and retrying it means allocating the same package three more
    // times on a JVM that has just said it has no room. Errors are left to fail on the first attempt.
    @Test
    void doesNotRetryAnError() throws IOException {
        doThrow(new OutOfMemoryError("Java heap space")).when(publishService).storeResource(any());

        try (var extensionFile = givenExtensionFile()) {
            handler.publishAsync(extensionFile, extensionService);
        }

        verify(publishService, times(1)).storeResource(any());
    }

    // The point of #1450: an operator who finds a version stuck at active = false has, until this is
    // recorded, no way to learn what stopped it short of correlating the row against a stack trace.
    @Test
    void recordsWhyThePublishDidNotFinish() throws IOException {
        doThrow(new OutOfMemoryError("Java heap space")).when(publishService).storeResource(any());

        try (var extensionFile = givenExtensionFile()) {
            handler.publishAsync(extensionFile, extensionService);
        }

        verify(publishService)
                .recordPublishError(any(), eq("java.lang.OutOfMemoryError: Java heap space"));
    }

    // The type alone when there is no message, rather than a bare "null" appended to it.
    @Test
    void recordsTheFailureTypeWhenItCarriesNoMessage() throws IOException {
        doThrow(new IllegalStateException()).when(publishService).storeResource(any());

        try (var extensionFile = givenExtensionFile()) {
            handler.publishAsync(extensionFile, extensionService);
        }

        verify(publishService, times(ATTEMPTS))
                .recordPublishError(any(), argThat("java.lang.IllegalStateException"::equals));
    }

    // The Error used to walk past a catch of Exception, so the scan of a version that died this way was
    // left sitting in its pre-publish state with nothing saying why.
    @Test
    void recordsAnErrorAgainstTheScan() throws IOException {
        var scan = new ExtensionScan();
        doThrow(new OutOfMemoryError("Java heap space")).when(publishService).storeResource(any());

        try (var extensionFile = givenExtensionFile()) {
            handler.publishAsync(extensionFile, extensionService, scan);
        }

        verify(scanService).markScanAsErrored(eq(scan), contains("OutOfMemoryError"));
    }

    @Configuration
    @EnableAsync
    @EnableResilientMethods
    static class TestConfig {

        /** Runs an {@code @Async} method on the calling thread, so the tests need no waiting. */
        @Bean
        Executor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        PublishingConfig publishingConfig() {
            return new PublishingConfig();
        }

        @Bean
        PublishExtensionVersionHandler publishExtensionVersionHandler(
                PublishingConfig publishingConfig,
                PublishExtensionVersionService publishService,
                ExtensionVersionIntegrityService integrityService,
                EntityManager entityManager,
                RepositoryService repositories,
                JobRequestScheduler scheduler,
                UserService users,
                ExtensionValidator validator,
                ExtensionControlService extensionControl,
                ExtensionScanService scanService
        ) {
            return new PublishExtensionVersionHandler(
                    publishingConfig,
                    publishService,
                    integrityService,
                    entityManager,
                    repositories,
                    scheduler,
                    users,
                    validator,
                    extensionControl,
                    scanService);
        }
    }
}
