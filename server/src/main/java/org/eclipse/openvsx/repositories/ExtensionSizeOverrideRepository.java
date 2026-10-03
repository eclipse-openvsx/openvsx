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

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.ExtensionSizeOverride;

public interface ExtensionSizeOverrideRepository extends Repository<ExtensionSizeOverride, Long> {

    // The LEFT JOIN is required: an implicit join through the nullable scopeExtension
    // association would be an INNER JOIN and would drop the namespace-wide rows.
    @Query("""
            SELECT o FROM ExtensionSizeOverride o
            LEFT JOIN o.scopeExtension e
            WHERE o.scopeNamespace.id = :namespaceId
              AND (e.id = :extensionId OR e IS NULL)
            """)
    List<ExtensionSizeOverride> findByScope(
            @Param("namespaceId") long namespaceId,
            @Param("extensionId")
            @Nullable Long extensionId
    );

    @Query("SELECT MAX(o.maxSize) FROM ExtensionSizeOverride o")
    @Nullable
    Long findHighestMaxSize();
}
