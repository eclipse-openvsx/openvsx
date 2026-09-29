/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Namespace;

public interface NamespaceRepository extends Repository<Namespace, Long> {

    Namespace findByNameIgnoreCase(String name);

    /**
     * Returns a list of namespaces whose name or displayName matches the given displayName case-insensitive.
     *
     * @param displayName the displayName to check for existing conflicts
     * @param excludeNamespace the namespace to exclude
     * @return a list of namespaces or an empty list of there are no conflicts
     */
    @Query(
        "from Namespace n where (lower(n.displayName) = lower(:displayName) or lower(n.name) = lower(:displayName)) and n <> :namespace"
    )
    List<Namespace> findConflictingNamespaces(String displayName, @Param("namespace") Namespace excludeNamespace);

    // Publish takes this lock before creating a new extension. A SELECT ... FOR UPDATE on an
    // extension that does not exist yet locks nothing — there is no row to lock and PostgreSQL has no
    // gap locking — so two publishes racing for the first version of a new extension would both miss
    // it and both insert, colliding on the unique_extension index. The namespace does exist at that
    // point, so it is the row they can serialize on.
    // FOR NO KEY UPDATE is deliberately weaker than the FOR UPDATE taken on the extension row: it
    // conflicts with itself, which is all the mutual exclusion needed here, while still allowing the
    // FOR KEY SHARE locks that rows referencing this namespace take when they are inserted.
    @Query(
        value = "select n.* from namespace n where n.id = :id for no key update of n",
        nativeQuery = true
    )
    Namespace findByIdForUpdate(@Param("id") long id);

    @Query("from Namespace n where not exists (from NamespaceMembership nm where nm.namespace = n)")
    Streamable<Namespace> findOrphans();

    long count();
}
