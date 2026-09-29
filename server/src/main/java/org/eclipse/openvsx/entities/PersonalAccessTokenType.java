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
package org.eclipse.openvsx.entities;

/**
 * The type of the personal access token.
 */
public enum PersonalAccessTokenType {
    /**
     * Long-lived personal access token (classic).
     */
    LLT("at_", false, false, true),
    /**
     * One time usable general personal access token.
     * Legacy: was used until 1.2.0; but is not anymore.
     */
    @Deprecated
    OTT("ot_", true, true, false),
    /**
     * Trusted publishing issued access token. Short-lived and scoped to a single extension, but usable
     * more than once within its lifetime: a release commonly publishes one version per target platform,
     * and those are separate publish requests that share the one token the exchange issued.
     */
    TPT("tp_", false, true, false);

    private final String tokenMarker;
    private final boolean oneTime;
    private final boolean ephemeral;
    private final boolean notify;

    PersonalAccessTokenType(String tokenMarker, boolean oneTime, boolean ephemeral, boolean notify) {
        this.tokenMarker = tokenMarker;
        this.oneTime = oneTime;
        this.ephemeral = ephemeral;
        this.notify = notify;
    }

    /**
     * Marks which kind of token a value is, so that a leaked one says what it can do at a glance and
     * secret scanning can tell them apart. It follows the deployment's own token prefix, which is where
     * the registry names itself.
     */
    public String getTokenMarker() {
        return tokenMarker;
    }

    /**
     * Whether using the token consumes it, so that it authenticates exactly one request.
     */
    public boolean isOneTime() {
        return oneTime;
    }

    /**
     * Whether the token is issued to a machine rather than managed by a user: it is deleted instead of
     * being left as a deactivated row once it can no longer be used, and its owner is never offered a
     * link to revoke it, because nothing shows it to them in the first place.
     * <p>
     * Every one-time token is ephemeral, but not every ephemeral one is single-use - a trusted
     * publishing token may publish several times before it expires.
     */
    public boolean isEphemeral() {
        return ephemeral;
    }

    public boolean isNotify() {
        return notify;
    }
}
