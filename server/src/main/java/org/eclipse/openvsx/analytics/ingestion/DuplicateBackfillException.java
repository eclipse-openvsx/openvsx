/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion;

/**
 * Thrown by {@link DownloadIngestionProcessor#backfill} when the named file was already
 * backfilled, so the caller does not write a second set of download events for it.
 */
public class DuplicateBackfillException extends RuntimeException {

    public DuplicateBackfillException(String fileName) {
        super("file '" + fileName + "' was already backfilled");
    }
}
