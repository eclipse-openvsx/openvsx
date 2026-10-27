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

import java.util.List;

import org.eclipse.openvsx.entities.PublisherAgreementEvent;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * The agreement kept in this database: a user's signing is the {@code SIGNED} event that
 * {@link PublisherAgreementService} records after {@link #sign}, and a later {@code REVOKED} event ends it. Whether
 * the version signed still counts is for {@link PublisherAgreementVersions}. A blank version disables agreements.
 */
public class SimplePublisherAgreementProvider implements PublisherAgreementProvider {

    private static final List<PublisherAgreementEvent.Type> SIGNING_STEPS = List.of(
            PublisherAgreementEvent.Type.SIGNED,
            PublisherAgreementEvent.Type.REVOKED);

    private final RepositoryService repositories;
    private final String version;

    public SimplePublisherAgreementProvider(RepositoryService repositories, String version) {
        this.repositories = repositories;
        this.version = version == null ? "" : version.trim();
    }

    @Override
    public boolean enabled() {
        return !version.isEmpty();
    }

    @Override
    public AgreementStatus status(UserData user) {
        var step = repositories.findLatestPublisherAgreementEvent(user.getId(), SIGNING_STEPS);
        if (step == null || step.getType() == PublisherAgreementEvent.Type.REVOKED) {
            return AgreementStatus.of(AgreementStatus.State.NONE);
        }
        return new AgreementStatus(AgreementStatus.State.SIGNED, step.getVersion(), step.getTimestamp(), false);
    }

    /** The service records the signing, which is all there is to keep. */
    @Override
    public AgreementStatus sign(UserData user) {
        return new AgreementStatus(AgreementStatus.State.SIGNED, version, TimeUtil.getCurrentUTC(), false);
    }

    /** The service's {@code REVOKED} event ends the signing. */
    @Override
    public void withdraw(UserData user, UserData admin) {
    }
}
