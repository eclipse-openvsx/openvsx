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

import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.entities.UserData;

/**
 * Extension point for deployments that keep the publisher agreement somewhere else, such as a remote
 * service. It answers what a user's agreement is with this provider and nothing more: whether a user may
 * publish, the block of a revoked agreement, the JSON served to clients, the HTTP status of a refusal and
 * the extension versions to reactivate after signing are all decided once, by {@link PublisherAgreementService}.
 * A deployment replaces the default ({@link SimplePublisherAgreementProvider}) with a {@code @Primary} bean of
 * this type.
 */
public interface PublisherAgreementProvider {

    /**
     * Whether this deployment requires an agreement at all. When it does not, nobody is asked to sign and
     * everyone may publish.
     */
    boolean enabled();

    /**
     * The agreement of the user, determined as that user: an implementation that asks a remote service may
     * use the user's own credentials. It reports the version the user signed as {@code SIGNED}, without judging
     * whether that version still counts, which {@link PublisherAgreementService} decides for every provider from
     * {@code ovsx.publisher-agreement.version} and {@code ovsx.publisher-agreement.allowed-versions}; a provider
     * that cannot tell the version leaves it out, and that signature then counts whatever the versions are.
     * It returns {@link AgreementStatus.State#UNKNOWN} when it cannot tell, for instance because the remote
     * service is unreachable, rather than guessing {@code NONE}.
     */
    AgreementStatus status(UserData user);

    /**
     * The same for an administrator looking at the user, so without the user's credentials. By default the
     * same as {@link #status}.
     */
    default AgreementStatus statusForAdmin(UserData user) {
        return status(user);
    }

    /**
     * Signs the current agreement on behalf of the user and returns the resulting status, which has to be
     * {@code SIGNED} and whose version is recorded: anything else counts as a failure and is not recorded. Must
     * be idempotent: signing again an agreement that is already signed returns the status as it is. A version that
     * the registry does not accept (see {@link #status}) makes the signing fail for good, so it has to be one that
     * {@code ovsx.publisher-agreement.version} or {@code ovsx.publisher-agreement.allowed-versions} lets through.
     *
     * @throws PublisherAgreementException if the user cannot sign
     */
    AgreementStatus sign(UserData user);

    /**
     * Withdraws the user's agreement, using the credentials of the admin where the implementation needs any.
     * Does nothing if there is nothing to withdraw. The block that keeps a revoked user from signing again is
     * not the provider's to keep.
     *
     * @throws PublisherAgreementException if the agreement could not be withdrawn
     */
    void withdraw(UserData user, UserData admin);

    /**
     * The login a user has to link before signing or being revoked, such as an account at the provider; none by
     * default.
     */
    default @Nullable ExternalLogin requiredLogin() {
        return null;
    }
}
