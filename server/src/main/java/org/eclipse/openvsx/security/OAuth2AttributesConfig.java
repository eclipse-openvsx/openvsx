/******************************************************************************
 * Copyright (c) 2023 Ericsson and others
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
package org.eclipse.openvsx.security;

import java.util.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration example:
 * <pre><code>
 *ovsx:
 *  oauth2:
 *    attribute-names:
 *      [provider-name]:
 *        avatar-url: string
 *        email: string
 *        full-name: string
 *        login-name: string
 *        provider-url: string
 * </code></pre>
 */
@ConfigurationProperties(prefix = "ovsx.oauth2")
public record OAuth2AttributesConfig(Map<String, OAuth2AttributesMapping> attributeNames) {
    private static final Map<String, OAuth2AttributesMapping> DEFAULT_MAPPINGS = Map.of(
            "github",
            new OAuth2AttributesMapping("avatar_url", "email", "name", "login", "html_url"));

    public OAuth2AttributesMapping getAttributeMapping(String provider) {
        return Optional.ofNullable(attributeNames).map(a -> a.get(provider)).orElse(DEFAULT_MAPPINGS.get(provider));
    }

    public List<String> getProviders() {
        var providers = new ArrayList<>(DEFAULT_MAPPINGS.keySet());
        if (attributeNames != null) {
            providers.addAll(attributeNames.keySet());
        }

        return providers;
    }
}
