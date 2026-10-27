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
package org.eclipse.openvsx.publisheragreement;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * What a {@link PublisherAgreementProvider} knows about the agreement of a user.
 *
 * @param state the state of the agreement
 * @param version the version signed, if any: a semantic version such as {@code 1.1}, whose minor and patch numbers
 *        may be left out, because the service judges it. A signed status without a version always counts.
 * @param timestamp when it was signed, if known
 * @param loginLinked whether the user has linked the {@link PublisherAgreementProvider#requiredLogin() login}
 *        the provider needs to sign or withdraw on their behalf
 */
public record AgreementStatus(
        State state,
        @Nullable String version,
        @Nullable LocalDateTime timestamp,
        boolean loginLinked
) {

    public enum State {
        /** The user has not signed. */
        NONE,
        /** The user signed a version; the service decides whether that version still counts. */
        SIGNED,
        /** The provider itself knows that what the user signed no longer counts. */
        OUTDATED,
        /** The provider could not tell. */
        UNKNOWN
    }

    public static AgreementStatus of(State state) {
        return new AgreementStatus(state, null, null, false);
    }
}
