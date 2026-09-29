/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceOwnershipCheckScannerTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private RepositoryService repositories;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ScannerRegistry scannerRegistry;

    /**
     * A search result containing an extension published under the "acme" namespace - the exact
     * namespace under test - simulating that the namespace is already claimed upstream.
     */
    private static final ExtensionQueryResult NAMESPACE_MATCH = searchResult(extensionOf("acme"));

    /**
     * A search result containing an extension published under "ACME" (different casing) - upstream
     * publisher ids are case-insensitive, so this should still count as a match for "acme".
     */
    private static final ExtensionQueryResult NAMESPACE_MATCH_DIFFERENT_CASE = searchResult(extensionOf("ACME"));

    /**
     * A search result that only contains extensions from other publishers - simulating a loose,
     * merely-relevant search hit that must NOT be mistaken for the "acme" namespace existing.
     */
    private static final ExtensionQueryResult UNRELATED_PUBLISHERS = searchResult(extensionOf("acme-tools"));

    /**
     * No search results at all.
     */
    private static final ExtensionQueryResult EMPTY = new ExtensionQueryResult(
            List.of(new ExtensionQueryResult.ResultItem(List.of(), List.of())));

    private static ExtensionQueryResult searchResult(ExtensionQueryResult.Extension... extensions) {
        return new ExtensionQueryResult(
                List.of(new ExtensionQueryResult.ResultItem(List.of(extensions), List.of())));
    }

    private static ExtensionQueryResult.Extension extensionOf(String publisherName) {
        var publisher = new ExtensionQueryResult.Publisher(null, null, publisherName, null, null);
        return new ExtensionQueryResult.Extension(
                null,
                null,
                null,
                null,
                publisher,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private NamespaceOwnershipCheckScanner newScanner(boolean enforced, boolean checkActiveExtensions) {
        var config = new NamespaceOwnershipCheckConfig(
                true,
                true,
                enforced,
                checkActiveExtensions,
                "http://irrelevant");
        return new NamespaceOwnershipCheckScanner(
                config,
                restTemplate,
                repositories,
                entityManager,
                scannerRegistry);
    }

    private ExtensionVersion extensionVersion() {
        var namespace = new Namespace();
        namespace.setName("acme");

        var extension = new Extension();
        extension.setName("widget");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        return extVersion;
    }

    @Test
    void startScan_throws_whenEnforcedAndUpstreamIsDown() {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenThrow(new RestClientException("whatever"));

        assertThrows(ScannerException.class, () -> newScanner(true, true).startScan(new Scanner.Command(1L, "scan-1")));
    }

    @Test
    void startScan_isClean_whenNotEnforcedAndUpstreamIsDown() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenThrow(new RestClientException("whatever"));

        var invocation = (Scanner.Invocation.Completed) newScanner(false, true)
                .startScan(new Scanner.Command(1L, "scan-1"));
        assertTrue(invocation.result().isClean());
    }

    @Test
    void startScan_isClean_whenNamespaceDoesNotExistUpstream() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(EMPTY);

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertTrue(invocation.result().isClean());
        verify(repositories, never()).isVerified(any(Namespace.class));
    }

    @Test
    void startScan_isClean_whenSearchHitsAreFromUnrelatedPublishers() throws Exception {
        // The upstream search is loose (free text), so it may return extensions that merely mention
        // the namespace name without actually being published under it - those must not count.
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(UNRELATED_PUBLISHERS);

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertTrue(invocation.result().isClean());
        verify(repositories, never()).isVerified(any(Namespace.class));
    }

    @Test
    void startScan_raisesThreat_whenNamespaceExistsUpstreamAndIsNotVerified() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(NAMESPACE_MATCH);
        when(repositories.isVerified(extVersion.getExtension().getNamespace())).thenReturn(false);

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertFalse(invocation.result().isClean());
        assertEquals(1, invocation.result().getThreats().size());
    }

    @Test
    void startScan_isClean_whenNamespaceExistsUpstreamAndIsVerified() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(NAMESPACE_MATCH);
        when(repositories.isVerified(extVersion.getExtension().getNamespace())).thenReturn(true);

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertTrue(invocation.result().isClean());
        assertEquals(0, invocation.result().getThreats().size());
    }

    @Test
    void startScan_raisesThreat_whenNamespaceExistsUpstreamWithDifferentCasing() throws Exception {
        // Upstream publisher ids are case-insensitive, so "ACME" must still be recognized as a match
        // for the "acme" namespace being published to.
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(NAMESPACE_MATCH_DIFFERENT_CASE);
        when(repositories.isVerified(extVersion.getExtension().getNamespace())).thenReturn(false);

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertFalse(invocation.result().isClean());
        assertEquals(1, invocation.result().getThreats().size());
    }

    @Test
    void startScan_isClean_isActiveAndCheckActiveExtensions() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn(NAMESPACE_MATCH);
        when(repositories.isVerified(extVersion.getExtension().getNamespace())).thenReturn(true);
        when(repositories.findActiveExtension(anyString(), anyString())).thenReturn(extVersion.getExtension());

        var invocation = (Scanner.Invocation.Completed) newScanner(true, true)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertTrue(invocation.result().isClean());
        verify(restTemplate, atLeastOnce()).postForObject(anyString(), any(), any());
    }

    @Test
    void startScan_isClean_isActiveAndNotCheckActiveExtensions() throws Exception {
        var extVersion = extensionVersion();
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(repositories.findActiveExtension(anyString(), anyString())).thenReturn(extVersion.getExtension());

        var invocation = (Scanner.Invocation.Completed) newScanner(true, false)
                .startScan(new Scanner.Command(1L, "scan-1"));

        assertTrue(invocation.result().isClean());
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void startScan_throws_whenExtensionVersionNotFound() {
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(null);

        assertThrows(ScannerException.class, () -> newScanner(true, true).startScan(new Scanner.Command(1L, "scan-1")));
    }
}
