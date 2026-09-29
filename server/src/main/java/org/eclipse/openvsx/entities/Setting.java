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
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Persistent entity for storing key-value settings scoped to any entity
 * (user, organization, system, etc.).
 *
 * DDL (PostgreSQL):
 *
 *   CREATE TABLE settings (
 *     id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
 *     entity_type VARCHAR(50) NOT NULL,
 *     entity_id   UUID        NOT NULL,
 *     key         VARCHAR(255) NOT NULL,
 *     value       JSONB,
 *     created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     CONSTRAINT uq_settings UNIQUE (entity_type, entity_id, key)
 *   );
 */
@Entity
public class Setting {
    @Id
    @GeneratedValue(generator = "settingSeq")
    @SequenceGenerator(name = "settingSeq", sequenceName = "setting_seq", allocationSize = 1)
    private long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    /**
     * Value stored as JSONB so it can hold any scalar or nested structure:
     *   "dark"               → String
     *   42                   → Number
     *   true                 → Boolean
     *   {"r":255,"g":0}      → Object
     *   ["en","fr"]          → Array
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    private String value;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Lifecycle hooks
    // -------------------------------------------------------------------------

    @PrePersist
    private void onCreate() {
        var now = TimeUtil.getCurrentUTC();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = TimeUtil.getCurrentUTC();
    }

    // -------------------------------------------------------------------------
    // Getters  (immutable after construction — use service layer to update)
    // -------------------------------------------------------------------------

    public long getId() {
        return id;
    }
    public String getKey() {
        return key;
    }
    public String getValue() {
        return value;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** The only mutable field — values change but keys stay stable. */
    public void setValue(String value) {
        this.value = value;
    }

    // -------------------------------------------------------------------------
    // Equality — based on natural key, not surrogate PK
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Setting other)) {
            return false;
        }
        return key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key);
    }

    @Override
    public String toString() {
        return "Setting{key='%s'}".formatted(key);
    }
}
