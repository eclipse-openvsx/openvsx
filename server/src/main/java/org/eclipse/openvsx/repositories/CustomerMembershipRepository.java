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

import org.eclipse.openvsx.entities.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

public interface CustomerMembershipRepository extends Repository<CustomerMembership, Long> {
    Streamable<CustomerMembership> findByCustomer(Customer customer);

    CustomerMembership findByUserAndCustomer(UserData user, Customer customer);
}
