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

import org.eclipse.openvsx.analytics.DownloadEvent;

/**
 * Storage for download analytics: one interface for writing events and reading series.
 */
public interface DownloadAnalyticsRepository {

    /**
     * Persists the given events atomically: either all of them are stored or none are.
     * Implementations live in their own database and therefore cannot join the caller's registry
     * transaction, so the extension download counter and the download ingestion entry commit
     * independently of these events.
     */
    void save(List<DownloadEvent> events);
}
