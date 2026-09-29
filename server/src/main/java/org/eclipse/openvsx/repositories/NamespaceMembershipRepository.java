/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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

import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;

public interface NamespaceMembershipRepository extends Repository<NamespaceMembership, Long> {

    NamespaceMembership findByUserAndNamespace(UserData user, Namespace namespace);

    Streamable<NamespaceMembership> findByNamespaceAndRoleIgnoreCase(Namespace namespace, String role);

    Streamable<NamespaceMembership> findByNamespace(Namespace namespace);

    Streamable<NamespaceMembership> findByUserOrderByNamespaceName(UserData user);

    NamespaceMembership findFirstByNamespaceNameIgnoreCase(String namespaceName);

}
