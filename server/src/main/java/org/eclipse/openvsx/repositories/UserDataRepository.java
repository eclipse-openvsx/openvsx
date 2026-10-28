/******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.UserData;

public interface UserDataRepository extends Repository<UserData, Long> {

    UserData findByProviderAndLoginName(String provider, String loginName);

    List<UserData> findByIdIn(Collection<Long> ids);

    // The steps of one user's publisher agreement take this lock to serialize against each other. FOR NO
    // KEY UPDATE conflicts with itself, which is all the mutual exclusion needed, while still allowing the
    // FOR KEY SHARE that rows referencing the user (tokens, memberships, the agreement events) take on insert.
    @Query(
        value = "select u.id from user_data u where u.id = :id for no key update of u",
        nativeQuery = true
    )
    Long lockById(@Param("id") long id);

    long count();

}
