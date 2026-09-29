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

import org.springframework.data.repository.Repository;

import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.entities.TierType;

public interface TierRepository extends Repository<Tier, Long> {
    List<Tier> findAllByOrderByIdAsc();

    Tier findByNameIgnoreCase(String name);

    List<Tier> findByTierType(TierType tierType);

    List<Tier> findByTierTypeAndIdNot(TierType tierType, long id);

    Tier save(Tier tier);

    void delete(Tier tier);
}
