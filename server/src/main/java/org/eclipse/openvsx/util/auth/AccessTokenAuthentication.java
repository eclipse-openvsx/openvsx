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
package org.eclipse.openvsx.util.auth;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;

/**
 * Represents user who presented a valid access token.
 *
 * @param tokenId the token that authenticated this request, recorded on whatever it publishes as
 *                best-effort provenance. Kept as an id rather than the entity: nothing here should hold
 *                a credential open, and the reference is allowed to decay when the token row goes.
 * @param claims  for a trusted publishing token, the OIDC identity the provider asserted at the exchange,
 *                which the publish copies onto the version. Null for any other kind of token.
 */
public record AccessTokenAuthentication(
        UserData userData,
        PersonalAccessTokenType type,
        long tokenId,
        @Nullable Map<String, String> claims
) implements AuthenticatedUser {
    @Override
    public @NonNull AuthenticationType authenticationType() {
        return AuthenticationType.TOKEN;
    }
}
