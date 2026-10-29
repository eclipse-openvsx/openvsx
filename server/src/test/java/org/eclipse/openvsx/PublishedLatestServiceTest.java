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
package org.eclipse.openvsx;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublishedLatestServiceTest {

    private final RepositoryService repositories = mock(RepositoryService.class);
    private final UpstreamRegistryService upstream = mock(UpstreamRegistryService.class);
    private final PublishedLatestService service = new PublishedLatestService(repositories, upstream);

    @Test
    void localVersionTakesPrecedenceOverUpstream() {
        var version = new ExtensionVersion();
        version.setVersion("1.0.0");
        when(repositories.findExtensionVersion("foo", "bar", null, "latest")).thenReturn(version);
        assertThat(service.findVersion("foo", "bar")).isEqualTo("1.0.0");
        verifyNoInteractions(upstream);
    }

    @Test
    void usesUpstreamWhenNoLocalVersionIsPublic() {
        var json = new ExtensionJson();
        json.setVersion("2.0.0");
        when(upstream.isValid()).thenReturn(true);
        when(upstream.getExtension("foo", "bar", null)).thenReturn(json);
        assertThat(service.findVersion("foo", "bar")).isEqualTo("2.0.0");
    }

    @Test
    void upstreamFallbackMatchesThePublicRegistryApi() {
        var local = mock(LocalRegistryService.class);
        var publicApi = new RegistryAPI(local, upstream, mock(UserService.class));
        var remote = new ExtensionJson();
        remote.setVersion("2.0.0");
        when(local.getExtension("foo", "bar", null)).thenThrow(new NotFoundException());
        when(upstream.isValid()).thenReturn(true);
        when(upstream.getExtension("foo", "bar", null)).thenReturn(remote);

        assertThat(service.findVersion("foo", "bar"))
                .isEqualTo(publicApi.getExtension("foo", "bar").getBody().getVersion());
    }

    @Test
    void switchesBetweenLocalAndUpstreamAsAvailabilityChanges() {
        var version = new ExtensionVersion();
        version.setVersion("1.0.0");
        var remote = new ExtensionJson();
        remote.setVersion("2.0.0");
        when(repositories.findExtensionVersion("foo", "bar", null, "latest"))
                .thenReturn(version, null, version);
        when(upstream.isValid()).thenReturn(true);
        when(upstream.getExtension("foo", "bar", null)).thenReturn(remote);
        assertThat(service.findVersion("foo", "bar")).isEqualTo("1.0.0");
        assertThat(service.findVersion("foo", "bar")).isEqualTo("2.0.0");
        assertThat(service.findVersion("foo", "bar")).isEqualTo("1.0.0");
    }

    @Test
    void returnsNoVersionWhenNeitherRegistryHasOne() {
        when(upstream.isValid()).thenReturn(true);
        when(upstream.getExtension("foo", "bar", null)).thenThrow(new NotFoundException());
        assertThat(service.findVersion("foo", "bar")).isNull();
    }

    @Test
    void skipsUnconfiguredUpstream() {
        assertThat(service.findVersion("foo", "bar")).isNull();
        verify(upstream, never()).getExtension(anyString(), anyString(), any());
    }
}
