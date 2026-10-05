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

import { request } from './request';
import { TrustedPublishingOptions } from './trusted-publishing-options';
import { configuredTimeout, statusError } from './util';

/**
 * Whether an OIDC ID token can be obtained without user interaction.
 */
export function hasIdTokenSource(options: TrustedPublishingOptions): boolean {
    return Boolean(options.idToken) || isGitHubActionsIdTokenAvailable();
}

/**
 * Obtains an OIDC ID token for the given audience from the surrounding CI system.
 */
export async function getIdToken(audience: string, options: TrustedPublishingOptions): Promise<string> {
    // CI systems such as GitLab CI provide the ID token directly as an environment variable
    if (options.idToken) {
        return options.idToken;
    }
    if (isGitHubActionsIdTokenAvailable()) {
        return getGitHubActionsIdToken(audience);
    }
    throw new Error('No OIDC ID token available for trusted publishing.\n'
        + "On GitHub Actions, grant the job the 'id-token: write' permission.\n"
        + 'On other CI systems, pass the ID token via the --idToken argument '
        + 'or the OVSX_ID_TOKEN environment variable.');
}

function isGitHubActionsIdTokenAvailable(): boolean {
    return Boolean(process.env.ACTIONS_ID_TOKEN_REQUEST_URL && process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN);
}

async function getGitHubActionsIdToken(audience: string): Promise<string> {
    // the request URL already carries an api-version query parameter, so keep its query intact
    const url = new URL(process.env.ACTIONS_ID_TOKEN_REQUEST_URL!);
    url.searchParams.set('audience', audience);
    const response = await getJson<GitHubIdTokenResponse>(url, {
        'Authorization': `Bearer ${process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN}`,
        'Accept': 'application/json'
    });
    if (!response.value) {
        throw new Error('GitHub Actions did not return an OIDC ID token.');
    }
    return response.value;
}

/**
 * Minimal JSON GET that is not bound to the registry: the request must not carry any registry
 * credentials, as it is sent to the CI system's token service.
 */
async function getJson<T>(url: URL, headers: Record<string, string>): Promise<T> {
    // OVSX_TIMEOUT covers this request too. Read here rather than taken from the registry's
    // options, since this one carries none of the registry's configuration - only the clock is
    // shared, and having two of those to explain would be worse.
    const response = await request(url, { method: 'GET', headers, timeout: configuredTimeout() });
    const json = await response.text();
    if (!response.ok) {
        throw statusError(response);
    }
    return JSON.parse(json);
}

interface GitHubIdTokenResponse {
    count?: number;
    value?: string;
}
