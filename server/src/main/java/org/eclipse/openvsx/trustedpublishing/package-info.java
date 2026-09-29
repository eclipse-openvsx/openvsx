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

/**
 * Trusted publishing.
 * <p>
 * Given there is a {@code USER}, who owns a (vetted, verified) {@code NAMESPACE}. Such a {@code USER} can manage
 * (create, list, delete) {@code PUBLISHER} for given {@code NAMESPACE} and (possibly non-yet-existent) {@code EXTENSION}
 * combination (identifying it by {@code $NS/$extensionName}). The extension name does not have to exist beforehand.
 * <p>
 * The {@code PUBLISHER} is a combination of {@code PROVIDER_ID}, {@code OWNER}, {@code REPO}, {@code WORKFLOW} and optionally {@code ENVIRONMENT}
 * assigned to targeted {@code NAMESPACE} and {@code EXTENSION} (as a name).
 * On creation, {@code PUBLISHER} is "resolved" (in provider specific way) and all related data is persisted.
 * <p>
 * The publishing workflow running on supported provider creates an OIDC ID token, which is then exchanged for a
 * short-lived publishing access token. The exchange along token carries the targeted {@code NAMESPACE} and {@code EXTENSION}
 * (as name). The OIDC ID token carries {@code PUBLISHER} information (extended with provider specific values).
 * First, provider is selected based on "iss" claim. The selected provider then validates fully
 * the OIDC ID token, and based on present claims creates "request data".
 * <p>
 * Finally, match is performed between looked up "persisted data" and "request data" (created from OIDC token from exchange),
 * and also {@code NAMESPACE} and {@code EXTENSION} are matched against persisted ones.
 * If match is established, a short-lived publishing access token is returned to the workflow, that publishing workflow
 * should use to publish extension in "usual way".
 */
package org.eclipse.openvsx.trustedpublishing;
