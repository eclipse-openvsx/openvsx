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

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.PersistedLog;
import org.eclipse.openvsx.entities.UserData;

public interface PersistedLogRepository extends Repository<PersistedLog, Long> {

    Streamable<PersistedLog> findByOrderByTimestampAsc();

    long countByUser(UserData user);

    Streamable<PersistedLog> findByTimestampAfterOrderByTimestampAsc(LocalDateTime dateTime);

    Page<PersistedLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<PersistedLog> findByTimestampAfterOrderByTimestampDesc(LocalDateTime dateTime, Pageable pageable);
}
