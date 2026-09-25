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

import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;

public interface ExtensionVersionChangeRepository extends Repository<ExtensionVersionChange, Long> {

    ExtensionVersionChange save(ExtensionVersionChange change);

    Streamable<ExtensionVersionChange> findByExtensionVersionOrderByChangedAtAsc(ExtensionVersion extVersion);

    /**
     * The entry most recently appended for the given version. The id breaks the tie between transitions
     * that share an instant, matching the order the feed itself uses.
     */
    Optional<ExtensionVersionChange> findFirstByExtensionVersionOrderByChangedAtDescIdDesc(
            ExtensionVersion extVersion
    );

    /**
     * The entry most recently appended for the given version under the given namespace specifically. A
     * version that has been renamed can have entries under more than one namespace, each with its own
     * independent latest state - see {@link org.eclipse.openvsx.repositories.RepositoryService#wasReportedAsAvailable}.
     */
    Optional<ExtensionVersionChange> findFirstByExtensionVersionAndNamespaceOrderByChangedAtDescIdDesc(
            ExtensionVersion extVersion,
            String namespace
    );
}
