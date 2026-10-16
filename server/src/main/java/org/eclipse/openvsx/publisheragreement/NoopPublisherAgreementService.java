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

import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.util.ErrorResultException;

public class NoopPublisherAgreementService implements PublisherAgreementService {

    @Override
    public boolean hasPublisherAgreement(UserData user) {
        return true;
    }

    @Override
    public void checkPublisherAgreement(UserData user) {
    }

    @Override
    public void enrichUserJson(UserJson json, UserData user) {
    }

    @Override
    public void signPublisherAgreement(UserData user, UserJson json) {
        throw new ErrorResultException("Publisher agreements are not supported.", HttpStatus.NOT_FOUND);
    }

    @Override
    public void adminEnrichUserJson(UserJson json, UserData user) {
    }

    @Override
    public void revokePublisherAgreement(UserData user, UserData admin) {
    }
}
