/*******************************************************************************
 * Copyright (c) 2026 Precies. Software Ltd and others
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
package org.eclipse.openvsx.storage;

import java.io.IOException;

import org.eclipse.openvsx.entities.FileResource;

/**
 * Signals that a {@link FileResource}'s backing object was confirmed absent from storage (e.g. an
 * S3 {@code NoSuchKeyException}, a 404 from Azure/GCS, a missing local file) -- as opposed to some
 * other, possibly transient, storage error (network blip, throttling, permission problem). Callers
 * that can tolerate a resource's file already being gone (e.g. the size backfill migration, which
 * has nothing further to do for a resource that isn't there) can catch this specifically and treat
 * it as "no work to do" rather than as a failure worth retrying.
 */
public class FileNotFoundInStorageException extends IOException {

    public FileNotFoundInStorageException(String message) {
        super(message);
    }

    public FileNotFoundInStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
