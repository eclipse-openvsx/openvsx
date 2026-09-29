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
package org.eclipse.openvsx.trustedpublishing.github;

import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;

/**
 * GitHub provider for <a href="https://github.com/">GitHub Public Instance</a>.
 */
public class GitHubTrustedPublishingProvider extends GitHubTrustedPublishingProviderSupport {
    public static final String PROVIDER_ID = "github";
    public static final String PROVIDER_URL = "https://github.com";
    private static final String OIDC_ISSUER = "https://token.actions.githubusercontent.com";
    private static final String API_RESOLVE_REQUEST = "https://api.github.com/repos/{owner}/{repo}";

    public GitHubTrustedPublishingProvider(TrustedPublishingConfig config) {
        super(config, PROVIDER_ID, "GitHub", PROVIDER_URL, OIDC_ISSUER, API_RESOLVE_REQUEST);
    }
}
