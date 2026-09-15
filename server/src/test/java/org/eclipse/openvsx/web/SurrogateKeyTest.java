/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurrogateKeyTest {

    @Test
    void namesTheNamespace() {
        assertThat(SurrogateKey.namespace("redhat")).isEqualTo("ns/redhat");
    }

    @Test
    void namesTheExtensionAndTheNamespaceItBelongsTo() {
        assertThat(SurrogateKey.extension("redhat", "java")).isEqualTo("ext/redhat/java ns/redhat");
    }

    // Names are matched case-insensitively, so /api/RedHat/Java and /api/redhat/java are the same
    // extension: they have to carry the same key or a purge reaches only one of them.
    @Test
    void namesTheSameExtensionWhateverTheCaseItIsRequestedIn() {
        assertThat(SurrogateKey.extension("RedHat", "Java")).isEqualTo(SurrogateKey.extension("redhat", "java"));
        assertThat(SurrogateKey.namespace("REDHAT")).isEqualTo(SurrogateKey.namespace("redhat"));
    }

    // Turkish 'I' lower-cases to a dotless 'ı' under a Turkish default locale, which would give the
    // same extension two different keys depending on where the server runs.
    @Test
    void namesTheSameExtensionWhateverTheServerLocaleIs() {
        var defaultLocale = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.of("tr", "TR"));
            assertThat(SurrogateKey.extension("MICROSOFT", "VSCODE-PYLANCE"))
                    .isEqualTo("ext/microsoft/vscode-pylance ns/microsoft");
        } finally {
            java.util.Locale.setDefault(defaultLocale);
        }
    }
}
