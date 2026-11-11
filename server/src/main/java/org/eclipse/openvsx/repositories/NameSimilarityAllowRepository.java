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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.NameSimilarityAllow;

public interface NameSimilarityAllowRepository extends Repository<NameSimilarityAllow, Long> {

    /** The allow for the pair of extensions, whichever way round it was stored. */
    @Query("""
            SELECT a FROM NameSimilarityAllow a
            WHERE (a.extensionId = :first AND a.otherExtensionId = :second)
               OR (a.extensionId = :second AND a.otherExtensionId = :first)
            """)
    Optional<NameSimilarityAllow> findPair(@Param("first") long first, @Param("second") long second);

    /** Every allow of extensions that has one of the given extensions on either side. */
    @Query("""
            SELECT a FROM NameSimilarityAllow a
            WHERE a.extensionId IN :extensionIds OR a.otherExtensionId IN :extensionIds
            """)
    List<NameSimilarityAllow> findInvolving(@Param("extensionIds") Collection<Long> extensionIds);

    /** The extensions that were allowed as look-alikes, the most recently allowed first. */
    @Query("""
            SELECT a.extensionId FROM NameSimilarityAllow a
            WHERE a.extensionId IS NOT NULL
            GROUP BY a.extensionId
            ORDER BY MAX(a.decidedAt) DESC, a.extensionId
            """)
    List<Long> findAllowedExtensionIds(Pageable pageable);

    /** The allow for the pair of namespaces, whichever way round it was stored. */
    @Query("""
            SELECT a FROM NameSimilarityAllow a
            WHERE (a.namespaceId = :first AND a.otherNamespaceId = :second)
               OR (a.namespaceId = :second AND a.otherNamespaceId = :first)
            """)
    Optional<NameSimilarityAllow> findNamespacePair(@Param("first") long first, @Param("second") long second);

    List<NameSimilarityAllow> findByExtensionIdInOrderByDecidedAtDesc(Collection<Long> extensionIds);

    Optional<NameSimilarityAllow> findById(long id);

    NameSimilarityAllow save(NameSimilarityAllow allow);

    void delete(NameSimilarityAllow allow);
}
