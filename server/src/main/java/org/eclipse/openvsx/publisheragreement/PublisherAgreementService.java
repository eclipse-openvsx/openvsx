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

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.util.ErrorResultException;

/**
 * Extension point for deployments that require publishers to sign an agreement. The default
 * implementation ({@link NoopPublisherAgreementService}) requires nothing; a deployment replaces it
 * by providing a {@code @Primary} bean of this type.
 */
public interface PublisherAgreementService {

    /**
     * Whether the given user may publish.
     */
    boolean hasPublisherAgreement(UserData user);

    /**
     * @throws ErrorResultException if the user may not publish for lack of an agreement
     */
    void checkPublisherAgreement(UserData user);

    /**
     * Adds the user's agreement status to the JSON served to that user.
     */
    void enrichUserJson(UserJson json, UserData user);

    /**
     * Signs the agreement on behalf of the user and adds the resulting status to the JSON.
     */
    void signPublisherAgreement(UserData user, UserJson json);

    /**
     * Adds the user's agreement status to the JSON served to administrators.
     */
    void adminEnrichUserJson(UserJson json, UserData user);

    /**
     * Revokes the user's agreement, using the admin's credentials where the implementation needs any.
     * Does nothing if there is nothing to revoke.
     */
    void revokePublisherAgreement(UserData user, UserData admin);
}
