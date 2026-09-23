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
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    @BeforeEach
    void setUpCache() {
        // Mockito's default answer for a List-returning method is an empty list, not null; without
        // this, every test below would see a (wrong) cache hit on an empty list instead of a miss.
        Mockito.lenient().when(cache.getMaliciousExtensions()).thenReturn(null);
    }

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

    @Test
    void cacheHitUpdatesThePerInstanceFallbackWithoutFetching() throws IOException {
        // The whole point of managing this cache manually instead of via @Cacheable: a hit must still
        // seed the fallback, since @Cacheable's hit path would skip this method's body entirely and
        // leave a replica that only ever sees hits with an empty fallback forever.
        var spy = spy(service);
        spy.enabled = true;
        when(cache.getMaliciousExtensions()).thenReturn(List.of("ns.ext"));

        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        assertThat(spy.getLastKnownMaliciousExtensionIds()).containsExactly("ns.ext");
        verify(spy, never()).getExtensionControlJson();
    }

    @Test
    void fallsThroughToALiveFetchWhenTheCacheReadFails() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        when(cache.getMaliciousExtensions()).thenThrow(new RuntimeException("redis outage"));
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();

        // A flaky cache read must not prevent trying a live fetch, which might still succeed.
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");
    }

    @Test
    void refreshMaliciousExtensionIdsUpdatesFallbackAndSharedCache() {
        var maliciousExtensionIds = List.of("ns.ext");

        service.refreshMaliciousExtensionIds(maliciousExtensionIds);

        assertThat(service.getLastKnownMaliciousExtensionIds()).isEqualTo(maliciousExtensionIds);
        verify(cache).refreshMaliciousExtensions(maliciousExtensionIds);
    }

    @Test
    void refreshMaliciousExtensionIdsSurvivesSharedCacheFailure() {
        var maliciousExtensionIds = List.of("ns.ext");
        doThrow(new RuntimeException("redis outage")).when(cache).refreshMaliciousExtensions(maliciousExtensionIds);

        // The daily job (retries = 0) calls this before purging and before processing deprecated
        // extensions; a Redis outage here must not abort that work, since the per-instance fallback
        // below is already correct regardless of whether the shared write succeeded.
        service.refreshMaliciousExtensionIds(maliciousExtensionIds);

        assertThat(service.getLastKnownMaliciousExtensionIds()).isEqualTo(maliciousExtensionIds);
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

    // GHSA-fq82-m65g-jfrh: fetchExtensionControlJson() sits on the publish request path via
    // getMaliciousExtensionIds() and previously had no read timeout at all, so a stalled connection
    // parked the servlet thread forever. Drives a real request against a server that accepts the
    // connection and never responds, so removing either setter would make this test hang instead of
    // pass (bounded by the join() below rather than actually hanging the suite).
    @Test
    void appliesConnectAndReadTimeoutsToARealFetch() throws Exception {
        try (var serverSocket = new ServerSocket(0)) {
            var acceptThread = new Thread(() -> {
                try (var socket = serverSocket.accept()) {
                    socket.getInputStream().read(new byte[4096]); // read the request, never respond
                } catch (Exception ignored) {
                    // test is tearing down
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            var url = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/stalls-forever").toURL();

            var failure = new AtomicReference<Throwable>();
            var caller = new Thread(() -> {
                try {
                    service.fetchExtensionControlJson(url);
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            caller.setDaemon(true);
            caller.start();
            // The real configured read timeout is 10s; bound the wait well above that so the test
            // still passes deterministically, without letting a regression hang the suite forever.
            caller.join(15_000);

            assertThat(caller.isAlive())
                    .as("the configured read timeout must bound the fetch, not hang indefinitely")
                    .isFalse();
            assertThat(failure.get()).isInstanceOf(SocketTimeoutException.class);
        }
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

        // Must throw rather than silently returning a fallback: the cache is written only explicitly,
        // after a successful fetch, so returning a fallback here would mean inventing a value this
        // method never actually validated - and callers would have no way to tell it apart from a real
        // fetch result.
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
