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

import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
public interface TrustedPublisherRepository extends Repository<TrustedPublisher, Long> {

    Streamable<TrustedPublisher> findTrustedPublishersByExtension(Extension extension);

    Streamable<TrustedPublisher> findByExtension_NamespaceAndCreatedBy(Namespace namespace, UserData createdBy);

    TrustedPublisher findById(long id);

    void delete(TrustedPublisher trustedPublisher);
}
