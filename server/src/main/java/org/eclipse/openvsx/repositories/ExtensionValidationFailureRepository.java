/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;

/**
 * Repository for accessing ExtensionValidationFailure entities.
 */
public interface ExtensionValidationFailureRepository extends Repository<ExtensionValidationFailure, Long> {

    /** Save a new or update an existing validation failure */
    ExtensionValidationFailure save(ExtensionValidationFailure failure);

    /** Find all validation failures for a specific scan */
    Streamable<ExtensionValidationFailure> findByScan(ExtensionScan scan);

    /**
     * Returns a sorted list of distinct check types.
     */
    @Query("select distinct f.checkType from ExtensionValidationFailure f order by f.checkType")
    List<String> findDistinctCheckTypes();
}
