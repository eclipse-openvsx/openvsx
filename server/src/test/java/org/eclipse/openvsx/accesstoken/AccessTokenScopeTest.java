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
package org.eclipse.openvsx.accesstoken;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenScopeTest {

    private static Namespace namespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        return namespace;
    }

    private static Extension extension(String namespaceName, String name) {
        var extension = new Extension();
        extension.setNamespace(namespace(namespaceName));
        extension.setName(name);
        return extension;
    }

    @Test
    void namespaceScopeMatchesNamespaceIgnoringCase() {
        var scope = new AccessTokenScope.NamespaceScoped(namespace("seniorturkmen"));

        assertThat(scope.allowsAction(new AccessTokenAction.PublishVersion("SeniorTurkmen", "ext"))).isTrue();
        assertThat(scope.allowsAction(new AccessTokenAction.PublishVersion("other", "ext"))).isFalse();
    }

    @Test
    void extensionScopeMatchesNamespaceAndExtensionIgnoringCase() {
        var scope = new AccessTokenScope.ExtensionScoped(extension("seniorturkmen", "gitblamesolo"));

        assertThat(scope.allowsAction(new AccessTokenAction.PublishVersion("SeniorTurkmen", "GitBlameSolo"))).isTrue();
        assertThat(scope.allowsAction(new AccessTokenAction.PublishVersion("seniorturkmen", "other"))).isFalse();
        assertThat(scope.allowsAction(new AccessTokenAction.PublishVersion("other", "gitblamesolo"))).isFalse();
    }
}
