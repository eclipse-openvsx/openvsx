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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
    void reusesLastKnownMaliciousListWhenFetchFails() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        doThrow(new IOException("connection reset")).when(spy).getExtensionControlJson();

        assertThat(spy.getMaliciousExtensionIds())
                .as("a failed refresh must reuse the last successfully parsed list, not lose it")
                .containsExactly("ns.ext");
    }

    @Test
    void reusesLastKnownMaliciousListWhenJsonUnparseable() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doReturn(JsonMapper.shared().readTree("""
                {"malicious": ["ns.ext"]}
                """))
                .when(spy)
                .getExtensionControlJson();
        assertThat(spy.getMaliciousExtensionIds()).containsExactly("ns.ext");

        doThrow(new FakeJsonParseException("malformed extensions.json")).when(spy).getExtensionControlJson();

        assertThat(spy.getMaliciousExtensionIds())
                .as("an unparseable refresh must reuse the last successfully parsed list, not lose it")
                .containsExactly("ns.ext");
    }

    @Test
    void returnsEmptyListWhenFetchNeverSucceeded() throws IOException {
        var spy = spy(service);
        spy.enabled = true;
        doThrow(new IOException("connection reset")).when(spy).getExtensionControlJson();

        assertThat(spy.getMaliciousExtensionIds()).isEqualTo(Collections.emptyList());
    }

    @Test
    void reusesLastKnownMaliciousListWhenMaliciousFieldMissing() throws IOException {
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

        assertThat(spy.getMaliciousExtensionIds())
                .as("a response missing the 'malicious' field must not NPE and must reuse the last list")
                .isEqualTo(List.of("ns.ext"));
    }
}
