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

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TempFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisplayNameConflictCheckServiceTest {

    @Mock
    DisplayNameConflictCheckConfig config;

    @Mock
    RepositoryService repositories;

    @Mock
    TempFile extensionFile;

    DisplayNameConflictCheckService check;

    UserData user;

    @BeforeEach
    void setUp() {
        check = new DisplayNameConflictCheckService(config, repositories);
        user = new UserData();
        // Lenient: only the tests that reach the conflict lookup read the publisher's namespaces.
        lenient().when(repositories.findMemberships(any(UserData.class))).thenReturn(Streamable.empty());
    }

    @Test
    void shouldDelegatePolicyToConfig() {
        when(config.isEnabled()).thenReturn(true);
        when(config.isEnforced()).thenReturn(false);
        when(config.isRequired()).thenReturn(true);

        assertThat(check.getCheckType()).isEqualTo("DISPLAY_NAME_CONFLICT");
        assertThat(check.isEnabled()).isTrue();
        assertThat(check.isEnforced()).isFalse();
        assertThat(check.isRequired()).isTrue();
    }

    @Test
    void shouldFailNewExtensionUsingTheDisplayNameOfAnotherExtension() {
        when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                .thenReturn(buildExtension("otherpublisher", "other-demo"));

        var result = check.check(context("Demo OK"));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).singleElement().satisfies(
                failure -> assertThat(failure.reason())
                        .contains("Display name 'Demo OK' is already used by")
                        .contains("otherpublisher.other-demo"));
    }

    @Test
    void shouldPassNewExtensionWithADisplayNameNobodyHolds() {
        var result = check.check(context("Demo OK"));

        assertThat(result.passed()).isTrue();
        verify(repositories).findActiveExtensionByDisplayName(eq("Demo OK"), any());
    }

    @Test
    void shouldNotTreatTheOwnNamespacesOfThePublisherAsAConflict() {
        var membership = new NamespaceMembership();
        membership.setUser(user);
        membership.setNamespace(buildNamespace("other-namespace-of-the-publisher"));
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        when(repositories.findMemberships(user)).thenReturn(Streamable.of(membership));

        check.check(context("Demo OK"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(repositories).findActiveExtensionByDisplayName(eq("Demo OK"), excluded.capture());
        assertThat(excluded.getValue()).containsExactlyInAnyOrder("publisher", "other-namespace-of-the-publisher");
    }

    @Test
    void shouldSkipAVersionKeepingTheNameTheExtensionAlreadyShows() {
        // Extensions that shared a display name before the check existed must still be able to publish.
        when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                .thenReturn(buildVersionShowing("Demo OK"));

        var result = check.check(context("Demo OK"));

        assertThat(result.passed()).isTrue();
        verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
    }

    @Test
    void shouldFailAVersionRenamingAnExistingExtensionOntoTheDisplayNameOfAnother() {
        when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                .thenReturn(buildVersionShowing("Some Name Of Its Own"));
        when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                .thenReturn(buildExtension("otherpublisher", "other-demo"));

        var result = check.check(context("Demo OK"));

        assertThat(result.passed()).isFalse();
    }

    @Test
    void shouldNotTreatACasingOrWhitespaceOnlyDifferenceAsARename() {
        when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                .thenReturn(buildVersionShowing("Demo OK"));

        var result = check.check(context("  demo ok  "));

        assertThat(result.passed()).isTrue();
        verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { " ", " ", " ", " ", " ", " ", " ", "　" })
    void shouldNotTreatUnicodeSpacePaddingAsARename(String space) {
        when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                .thenReturn(buildVersionShowing("Demo OK"));

        var result = check.check(context(space + "Demo OK" + space));

        assertThat(result.passed()).isTrue();
        verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
    }

    @Test
    void shouldPassAPackageDeclaringNoDisplayName() {
        // The scan's label falls back to the extension name; that fallback must not be checked.
        var context = context(null);
        context.scan().setExtensionDisplayName("demo");

        var result = check.check(context);

        assertThat(result.passed()).isTrue();
        verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
    }

    @Test
    void shouldPassADisplayNameOfOnlyUnicodeSpaces() {
        var result = check.check(context(" 　 "));

        assertThat(result.passed()).isTrue();
        verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
    }

    private PublishCheck.Context context(String declaredDisplayName) {
        var scan = new ExtensionScan();
        scan.setNamespaceName("publisher");
        scan.setExtensionName("demo");
        scan.setDeclaredDisplayName(declaredDisplayName);
        scan.setExtensionDisplayName(declaredDisplayName);
        return new PublishCheck.Context(scan, extensionFile, user);
    }

    private ExtensionVersion buildVersionShowing(String displayName) {
        var extVersion = new ExtensionVersion();
        extVersion.setDisplayName(displayName);
        return extVersion;
    }

    private Extension buildExtension(String namespaceName, String extensionName) {
        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(buildNamespace(namespaceName));
        return extension;
    }

    private Namespace buildNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        return namespace;
    }
}
