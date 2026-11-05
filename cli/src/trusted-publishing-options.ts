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

export interface TrustedPublishingOptions {
    /**
     * Obtain a short-lived publishing token by exchanging an OIDC ID token. If unset, trusted
     * publishing is used whenever an ID token source is detected and no access token is available.
     */
    trustedPublishing?: boolean;
    /**
     * The OIDC ID token to exchange. Only needed for CI systems that expose the token directly,
     * such as GitLab CI; on GitHub Actions the token is requested from the workflow runtime.
     */
    idToken?: string;
    /**
     * The audience to request for the OIDC ID token. Defaults to the registry URL and must match
     * the audience the registry expects.
     */
    oidcAudience?: string;
    /**
     * The registration code shown when the trusted publisher of a private repository was registered.
     * Only the first publish needs it: it completes the registration, and the code is used up.
     */
    registrationCode?: string;
}
