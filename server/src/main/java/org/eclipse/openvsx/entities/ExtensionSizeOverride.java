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

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.util.TimeUtil;

@Entity
@Table(name = "extension_size_override")
public class ExtensionSizeOverride implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionSizeOverrideSeq")
    @SequenceGenerator(
        name = "extensionSizeOverrideSeq",
        sequenceName = "extension_size_override_seq",
        allocationSize = 1
    )
    private long id;

    @ManyToOne
    @JoinColumn(name = "scope_namespace_id", nullable = false)
    private Namespace scopeNamespace;

    /** {@code null} means the override applies to the whole namespace. */
    @ManyToOne
    @JoinColumn(name = "scope_extension_id")
    private @Nullable Extension scopeExtension;

    @Column(name = "max_size", nullable = false)
    private long maxSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = TimeUtil.getCurrentUTC();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = TimeUtil.getCurrentUTC();
    }

    public long getId() {
        return id;
    }

    public Namespace getScopeNamespace() {
        return scopeNamespace;
    }

    public void setScopeNamespace(Namespace scopeNamespace) {
        this.scopeNamespace = scopeNamespace;
    }

    public @Nullable Extension getScopeExtension() {
        return scopeExtension;
    }

    public void setScopeExtension(@Nullable Extension scopeExtension) {
        this.scopeExtension = scopeExtension;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtensionSizeOverride that)) {
            return false;
        }
        return id == that.id
                && maxSize == that.maxSize
                && Objects.equals(scopeNamespace, that.scopeNamespace)
                && Objects.equals(scopeExtension, that.scopeExtension)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scopeNamespace, scopeExtension, maxSize, createdAt, updatedAt);
    }
}
