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
package org.eclipse.openvsx.trustedpublishing;

import org.springframework.security.oauth2.jwt.JwtException;

/**
 * JWT processing exception thrown when required claim is not present in token.
 */
public class MissingRequiredClaimException extends JwtException {
    private final String claim;

    public MissingRequiredClaimException(String claim) {
        super("Missing required claim: " + claim);
        this.claim = claim;
    }

    public String getClaim() {
        return claim;
    }
}
