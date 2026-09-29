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
package org.eclipse.openvsx.util;

import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.PersistedLog;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;

@Service
public class LogService {

    private final EntityManager entityManager;

    public LogService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void logAction(@NonNull UserData user, @NonNull ResultJson result) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(result);

        if (result.getSuccess() != null) {
            var log = new PersistedLog();
            log.setUser(user);
            log.setTimestamp(TimeUtil.getCurrentUTC());
            log.setMessage(result.getSuccess());
            entityManager.persist(log);
        }
    }
}
