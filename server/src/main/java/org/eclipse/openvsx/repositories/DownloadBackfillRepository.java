/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import org.eclipse.openvsx.entities.DownloadBackfill;

public interface DownloadBackfillRepository extends Repository<DownloadBackfill, Long> {

    @Query("select count(b) > 0 from DownloadBackfill b where b.storageType = ?1 and b.fileName = ?2")
    boolean existsByStorageTypeAndFileName(String storageType, String fileName);
}
