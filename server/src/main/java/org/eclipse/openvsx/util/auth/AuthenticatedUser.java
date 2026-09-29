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

import org.jspecify.annotations.NonNull;

import org.eclipse.openvsx.entities.UserData;

/**
 * Represents user, who successfully authenticated by some means.
 * Carries {@link UserData} representing the subject.
 * The {@link AuthenticatedUser#authenticationType()} tells how user authenticated.
 * Inquire subtypes for more information.
 */
public sealed interface AuthenticatedUser permits AccessTokenAuthentication, LoggedInAuthentication {
    /**
     * The type how user authenticated.
     */
    enum AuthenticationType {
        /**
         * User logged in.
         */
        LOGGED_IN,
        /**
         * User used some endpoint where access tokens are used and token was successfully verified.
         */
        TOKEN
    }

    /**
     * The user in question, that is authenticated.
     */
    @NonNull
    UserData userData();

    /**
     * The mode user is authenticated.
     */
    @NonNull
    AuthenticationType authenticationType();
}
