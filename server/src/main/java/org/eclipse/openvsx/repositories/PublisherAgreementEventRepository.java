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
package org.eclipse.openvsx.repositories;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.PublisherAgreementEvent;

public interface PublisherAgreementEventRepository extends Repository<PublisherAgreementEvent, Long> {

    Streamable<PublisherAgreementEvent> findByUserDataIdOrderByIdDesc(long userDataId);

    Optional<PublisherAgreementEvent> findFirstByUserDataIdAndTypeInOrderByIdDesc(
            long userDataId,
            Collection<PublisherAgreementEvent.Type> types
    );

    PublisherAgreementEvent save(PublisherAgreementEvent event);
}
