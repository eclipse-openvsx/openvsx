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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoopPublisherAgreementServiceTest {

    private final NoopPublisherAgreementService service = new NoopPublisherAgreementService();
    private final UserData user = new UserData();

    @Test
    void everyoneMayPublish() {
        assertThat(service.hasPublisherAgreement(user)).isTrue();
        assertThatCode(() -> service.checkPublisherAgreement(user)).doesNotThrowAnyException();
    }

    @Test
    void leavesTheUserJsonUntouched() {
        var json = new UserJson();

        service.enrichUserJson(json, user);
        service.adminEnrichUserJson(json, user);

        assertThat(json.getPublisherAgreement()).isNull();
    }

    @Test
    void revokingDoesNothing() {
        assertThatCode(() -> service.revokePublisherAgreement(user, new UserData())).doesNotThrowAnyException();
    }

    @Test
    void signingIsNotSupported() {
        assertThatThrownBy(() -> service.signPublisherAgreement(user, new UserJson()))
                .isInstanceOfSatisfying(
                        ErrorResultException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
