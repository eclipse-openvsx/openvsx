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

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.eclipse.openvsx.entities.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long> {

    Optional<Setting> findByKey(String key);

    @Modifying
    @Query(value = """
            INSERT INTO setting (key, value, created_at, updated_at)
            VALUES (:key, CAST(:value AS jsonb), :now, :now)
            ON CONFLICT ON CONSTRAINT setting_key_unique
            DO UPDATE SET value = CAST(:value AS jsonb), updated_at = :now
            """, nativeQuery = true)
    void upsert(@Param("key") String key, @Param("value") String value, @Param("now") LocalDateTime now);
}
