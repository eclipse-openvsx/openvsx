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

import { getIdToken, hasIdTokenSource } from './oidc';
import { AccessToken, Registry } from './registry';
import { StatusError, redactUrl } from './util';
import { TrustedPublishingOptions } from './trusted-publishing-options';

const tokens = new Map<string, Promise<string>>();

// The value each cached exchange resolved to, so a token a publish was refused with can be told from one
// another publish has already replaced. Cleared while an exchange is in flight.
const issued = new Map<string, string>();

/**
 * Whether the given options ask for trusted publishing. If the user did not decide explicitly,
 * it is used whenever the surrounding CI system can provide an OIDC ID token.
 */
export function useTrustedPublishing(options: TrustedPublishingOptions): boolean {
    return options.trustedPublishing ?? hasIdTokenSource(options);
}

/**
 * Exchanges an OIDC ID token for a short-lived access token that can publish the given extension.
 * The token is not stored, it is only valid for a few minutes.
 */
export function getTrustedPublishingToken(
    registry: Registry,
    namespace: string,
    extension: string,
    options: TrustedPublishingOptions
): Promise<string> {
    // publishing fans out over targets and package paths, but one token is enough per extension
    const key = `${namespace}.${extension}`;
    return tokens.get(key) ?? exchange(key, registry, namespace, extension, options);
}

/**
 * Exchanges the ID token again after the registry refused the one this extension was publishing with.
 * The issued token is short-lived and shared by every target platform of a release, so a slow fan-out
 * can outlive it and be refused partway through.
 *
 * `stale` is the token that was refused. If the cache no longer holds it, another target has already
 * replaced it and that exchange is awaited instead, so one expiry costs one exchange however many
 * targets hit it at once.
 */
export function refreshTrustedPublishingToken(
    registry: Registry,
    namespace: string,
    extension: string,
    options: TrustedPublishingOptions,
    stale: string
): Promise<string> {
    const key = `${namespace}.${extension}`;
    const current = tokens.get(key);
    if (current && issued.get(key) !== stale) {
        return current;
    }

    return exchange(key, registry, namespace, extension, options);
}

function exchange(
    key: string,
    registry: Registry,
    namespace: string,
    extension: string,
    options: TrustedPublishingOptions
): Promise<string> {
    // Dropped up front, so a refresh that is still in flight does not look like the stale token to the
    // next caller and start a second exchange of its own.
    issued.delete(key);
    const token = requestToken(registry, namespace, extension, options).then(value => {
        issued.set(key, value);
        return value;
    });
    tokens.set(key, token);
    return token;
}

// The registry answers 503 when it could not reach the identity provider to check the token, as opposed
// to checking it and refusing. That is worth another go: publishing runs unattended on CI, and a blip
// reaching GitHub or GitLab should not fail a release build. A refusal is never retried.
const RETRYABLE_STATUS = [502, 503, 504];
const RETRY_DELAYS_MS = [1000, 4000];

async function requestToken(
    registry: Registry,
    namespace: string,
    extension: string,
    options: TrustedPublishingOptions
): Promise<string> {
    requireSecureRegistry(registry);
    const audience = options.oidcAudience ?? await expectedAudience(registry);
    const idToken = await getIdToken(audience, options);

    let result: AccessToken;
    try {
        result = await exchangeWithRetry(registry, namespace, extension, idToken);
    } catch (err) {
        throw new Error(`${err.message}\n${registrationHint(registry, namespace, extension)}`);
    }
    if (result.error) {
        throw new Error(`${result.error}\n${registrationHint(registry, namespace, extension)}`);
    }
    if (!result.value) {
        throw new Error('The registry did not return a publishing token.');
    }

    const expires = result.expiresTimestamp ? `, expires at ${result.expiresTimestamp}` : '';
    console.log(`\ud83d\udd10  Trusted publishing token issued for ${namespace}.${extension}${expires}`);
    return result.value;
}

async function exchangeWithRetry(
    registry: Registry,
    namespace: string,
    extension: string,
    idToken: string
): Promise<AccessToken> {
    for (let attempt = 0; ; attempt++) {
        try {
            return await registry.requestTrustedPublishingToken(namespace, extension, idToken);
        } catch (err) {
            const status = (err as StatusError)?.status;
            if (attempt >= RETRY_DELAYS_MS.length || status === undefined || !RETRYABLE_STATUS.includes(status)) {
                throw err;
            }
            const delay = RETRY_DELAYS_MS[attempt];
            console.log(`The registry could not verify the token (${status}), retrying in ${delay / 1000}s`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
}

/**
 * The ID token is a bearer credential for publishing, and the registry also chooses the audience it
 * is minted for, so both only travel over https. A loopback registry is exempt, as for a browser's
 * secure contexts: nothing leaves the machine.
 */
function requireSecureRegistry(registry: Registry): void {
    const { protocol, hostname } = new URL(registry.url);
    if (protocol === 'https:' || hostname === 'localhost' || hostname === '[::1]' || /^127\./.test(hostname)) {
        return;
    }
    throw new Error(
        `Trusted publishing sends an ID token to the registry, so the registry URL must use https, `
        + `but it is ${redactUrl(registry.url)}. Use an https URL, or publish with an access token.`
    );
}

/**
 * The audience a registry validates is its own configuration, defaulting to the URL of its web UI
 * rather than the API URL this CLI talks to, so it is read from where the registry publishes it.
 * A registry that reports nothing there is asked for the registry URL.
 */
async function expectedAudience(registry: Registry): Promise<string> {
    const reported = await registry.getRegistryVersion().catch(() => undefined);
    return reported?.trustedPublishingAudience ?? registry.url;
}

function registrationHint(registry: Registry, namespace: string, extension: string): string {
    return `Check the trusted publishers registered for '${namespace}.${extension}' at `
        + `${registry.url}/user-settings/trusted-publishers.`;
}
