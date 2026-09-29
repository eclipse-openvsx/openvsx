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

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.Tier;

public interface CustomerRepository extends Repository<Customer, Long> {
    List<Customer> findAll();

    Optional<Customer> findById(long id);

    Customer findByNameIgnoreCase(String name);

    List<Customer> findByTier(Tier tier);

    int countCustomersByTier(Tier tier);

    Customer save(Customer customer);

    void delete(Customer customer);
}
