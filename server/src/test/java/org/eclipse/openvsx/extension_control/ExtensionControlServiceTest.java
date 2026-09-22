/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.extension_control;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.ExtensionId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExtensionControlService#updateExtension}, focusing on how the replacement is
 * resolved (never pointing at a missing/inactive extension) and on cache/search invalidation when the
 * replacement changes.
 */
@ExtendWith(MockitoExtension.class)
class ExtensionControlServiceTest {

    private static final String NAMESPACE = "n";

    @Mock
    JobRequestScheduler scheduler;

    @Mock
    RepositoryService repositories;

    @Mock
    EntityManager entityManager;

    @Mock
    SearchUtilService search;

    @Mock
    CacheService cache;

    @InjectMocks
    ExtensionControlService service;

    private long idSequence = 0;

    private Extension extension(String name, boolean deprecated, boolean active) {
        var namespace = new Namespace();
        namespace.setName(NAMESPACE);
        var extension = new Extension();
        extension.setId(++idSequence);
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setDeprecated(deprecated);
        extension.setActive(active);
        return extension;
    }

    private void mockExtension(Extension extension) {
        when(repositories.findExtension(extension.getName(), NAMESPACE)).thenReturn(extension);
    }

    private void verifyCachesEvicted(Extension extension) {
        verify(cache).evictNamespaceDetails(extension);
        verify(cache).evictLatestExtensionVersion(extension);
        verify(cache).evictExtensionJsons(extension);
        verify(search).updateSearchEntry(extension);
    }

    @Test
    void doesNotPointAtInactiveReplacement() {
        var extension = extension("ext", true, true);
        var replacement = extension("replacement", false, false); // inactive
        mockExtension(extension);
        mockExtension(replacement);

        service.updateExtension(
                new ExtensionId(NAMESPACE, "ext"),
                true,
                new ExtensionId(NAMESPACE, "replacement"),
                true);

        assertThat(extension.getReplacement())
                .as("an inactive replacement must not be set")
                .isNull();
    }

    @Test
    void pointsAtActiveReplacement() {
        var extension = extension("ext", true, true);
        var replacement = extension("replacement", false, true); // active
        mockExtension(extension);
        mockExtension(replacement);

        service.updateExtension(
                new ExtensionId(NAMESPACE, "ext"),
                true,
                new ExtensionId(NAMESPACE, "replacement"),
                true);

        assertThat(extension.getReplacement()).isSameAs(replacement);
        // The replacement changed (null -> replacement) so caches must be evicted even though the
        // deprecated flag did not change.
        verifyCachesEvicted(extension);
    }

    @Test
    void evictsCachesWhenReplacementClearedWhileDeprecationUnchanged() {
        var previousReplacement = extension("old-replacement", false, true);
        var extension = extension("ext", true, true); // already deprecated
        extension.setReplacement(previousReplacement);
        var replacement = extension("replacement", false, false); // now inactive -> must be cleared
        mockExtension(extension);
        mockExtension(replacement);

        service.updateExtension(
                new ExtensionId(NAMESPACE, "ext"),
                true,
                new ExtensionId(NAMESPACE, "replacement"),
                true);

        assertThat(extension.getReplacement())
                .as("clearing an inactive replacement must null it out")
                .isNull();
        verifyCachesEvicted(extension);
    }

    @Test
    void doesNotEvictCachesWhenNothingChanged() {
        var replacement = extension("replacement", false, true);
        var extension = extension("ext", true, true); // already deprecated
        extension.setReplacement(replacement);
        mockExtension(extension);
        mockExtension(replacement);

        service.updateExtension(
                new ExtensionId(NAMESPACE, "ext"),
                true,
                new ExtensionId(NAMESPACE, "replacement"),
                true);

        assertThat(extension.getReplacement()).isSameAs(replacement);
        verify(cache, never()).evictExtensionJsons(extension);
        verify(search, never()).updateSearchEntry(extension);
    }

    /** JacksonException's constructors are protected; a trivial subclass makes one throwable from a test. */
    private static final class FakeJsonParseException extends tools.jackson.core.JacksonException {
        FakeJsonParseException(String message) {
            super(message);
        }
    }

    @Test
    void retriesOnceOnTransientIOExceptionThenSucceeds() throws IOException {
        var spy = spy(service);
        var goodResponse = JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """);
        doThrow(new IOException("connection reset"))
                .doReturn(goodResponse)
                .when(spy)
                .fetchExtensionControlJson(any());

        assertThat(spy.getExtensionControlJson()).isEqualTo(goodResponse);
        verify(spy, times(2)).fetchExtensionControlJson(any());
    }

    @Test
    void givesUpAfterMaxAttemptsOnRepeatedIOException() throws IOException {
        var spy = spy(service);
        var failure = new IOException("connection reset");
        doThrow(failure).when(spy).fetchExtensionControlJson(any());

        var thrown = assertThrows(IOException.class, spy::getExtensionControlJson);
        assertThat(thrown).isSameAs(failure);
        verify(spy, times(2)).fetchExtensionControlJson(any());
    }

    @Test
    void doesNotRetryOnJacksonException() throws IOException {
        var spy = spy(service);
        doThrow(new FakeJsonParseException("malformed extensions.json"))
                .when(spy)
                .fetchExtensionControlJson(any());

        // A malformed body would fail identically on an immediate retry against the same URL, so this
        // must not spend a second attempt on it.
        assertThrows(FakeJsonParseException.class, spy::getExtensionControlJson);
        verify(spy, times(1)).fetchExtensionControlJson(any());
    }

    @Test
    void throwsAndPreservesLastKnownListWhenFetchFails() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        doThrow(new IOException("connection reset")).when(spy).getExtensionControlJson();

        // Must throw rather than silently returning a fallback: this method is @Cacheable, and a
        // fallback value returned here would get written into the shared (Redis-backed) cache for the
        // full TTL, poisoning every other replica's malicious-extension check, not just this call.
        assertThrows(IOException.class, spy::getMaliciousExtensionIds);
        assertThat(spy.getLastKnownMaliciousExtensionIds())
                .as("the per-instance fallback must still hold the last successfully parsed list")
                .containsExactly("ns.ext");
    }

    @Test
    void throwsAndPreservesLastKnownListWhenJsonUnparseable() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        doThrow(new FakeJsonParseException("malformed extensions.json")).when(spy).getExtensionControlJson();

        assertThrows(FakeJsonParseException.class, spy::getMaliciousExtensionIds);
        assertThat(spy.getLastKnownMaliciousExtensionIds())
                .as("the per-instance fallback must still hold the last successfully parsed list")
                .containsExactly("ns.ext");
    }

    @Test
    void throwsAndFallsBackToEmptyListWhenFetchNeverSucceeded() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doThrow(new IOException("connection reset")).when(spy).getExtensionControlJson();

        assertThrows(IOException.class, spy::getMaliciousExtensionIds);
        assertThat(spy.getLastKnownMaliciousExtensionIds()).isEqualTo(Collections.emptyList());
    }

    @Test
    void throwsAndPreservesLastKnownListWhenMaliciousFieldMissing() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        doReturn(JsonMapper.shared().readTree("""
                {"deprecated": {}}
                """))
                .when(spy)
                .getExtensionControlJson();

        assertThrows(IOException.class, spy::getMaliciousExtensionIds);
        assertThat(spy.getLastKnownMaliciousExtensionIds())
                .as("a response missing the 'malicious' field must not NPE and must preserve the last list")
                .isEqualTo(List.of("ns.ext"));
    }
}
