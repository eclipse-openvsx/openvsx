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
package org.eclipse.openvsx.trustedpublishing;

import java.util.LinkedHashMap;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig.GitLabInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrustedPublishingService#getTrustedPublishers}: registrations of every
 * extension are listed, but only active extensions without one can take a further registration.
 */
@ExtendWith(MockitoExtension.class)
class TrustedPublishingServiceTest {

    private static final String NAMESPACE = "foo";

    @Mock
    TrustedPublishingConfig config;

    @Mock
    RepositoryService repositories;

    @Mock
    AccessTokenService tokens;

    @Mock
    EntityManager entityManager;

    private final UserData user = new UserData();

    private final Namespace namespace = new Namespace();

    @Test
    void listsEveryRegistrationButOffersOnlyUntakenActiveExtensions() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getActiveProviders()).thenReturn(List.of("github"));
        namespace.setId(1L);
        namespace.setName(NAMESPACE);
        when(repositories.findNamespace(NAMESPACE)).thenReturn(namespace);
        when(repositories.isNamespaceOwner(user, namespace)).thenReturn(true);

        // "gone" is inactive: it keeps its registration listed, but can not take a new one
        var registered = extension(1L, "registered");
        var free = extension(2L, "free");
        var gone = extension(3L, "gone");
        when(repositories.findAllExtensionNames(namespace)).thenReturn(List.of("free", "gone", "registered"));
        when(repositories.findActiveExtensionNames(namespace)).thenReturn(List.of("free", "registered"));
        when(repositories.findExtension("registered", namespace)).thenReturn(registered);
        when(repositories.findExtension("free", namespace)).thenReturn(free);
        when(repositories.findExtension("gone", namespace)).thenReturn(gone);

        var registeredPublisher = publisher(10L, registered);
        var gonePublisher = publisher(11L, gone);
        when(repositories.findTrustedPublishersByExtension(registered))
                .thenReturn(Streamable.of(registeredPublisher));
        when(repositories.findTrustedPublishersByExtension(free)).thenReturn(Streamable.empty());
        when(repositories.findTrustedPublishersByExtension(gone)).thenReturn(Streamable.of(gonePublisher));

        // config is used in ctor, so we must set up fixture and then create the service
        TrustedPublishingService service = new TrustedPublishingService(config, repositories, tokens, entityManager);
        var result = service.getTrustedPublishers(user, NAMESPACE);

        assertThat(result.publishers()).containsExactlyInAnyOrder(registeredPublisher, gonePublisher);
        assertThat(result.registrableExtensions()).containsExactly("free");
    }

    // The status endpoint hands this list straight to the client, so an unordered map would reshuffle
    // the providers offered in the registration dialog on every restart.
    @Test
    void offersProvidersInAStableOrderGitHubFirst() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getActiveProviders()).thenReturn(List.of("github", "gitlab", "eclipse-gitlab"));
        var instances = new LinkedHashMap<String, GitLabInstance>();
        instances.put("gitlab", new GitLabInstance("GitLab", "https://gitlab.com"));
        instances.put("eclipse-gitlab", new GitLabInstance("Eclipse GitLab", "https://gitlab.eclipse.org"));
        when(config.getGitlab()).thenReturn(instances);

        var service = new TrustedPublishingService(config, repositories, tokens, entityManager);

        assertThat(service.getTrustedPublisherProviders().keySet())
                .containsExactly("github", "gitlab", "eclipse-gitlab");
    }

    private Extension extension(long id, String name) {
        var extension = new Extension();
        extension.setId(id);
        extension.setName(name);
        extension.setNamespace(namespace);
        return extension;
    }

    private TrustedPublisher publisher(long id, Extension extension) {
        var publisher = new TrustedPublisher();
        publisher.setId(id);
        publisher.setExtension(extension);
        publisher.setProvider("github");
        return publisher;
    }
}
