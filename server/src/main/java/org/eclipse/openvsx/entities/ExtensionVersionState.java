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

/**
 * A publicly visible state an {@link ExtensionVersion} can be in, as reported by an
 * {@link ExtensionVersionChange} and by the registry changes feed.
 * <p>
 * Persisted and reported by name, so the constants of {@code ChangeEntryJson} are these names and the
 * two must be kept in step.
 */
public enum ExtensionVersionState {

    /**
     * The version was published and is publicly available.
     */
    ACTIVE,

    /**
     * The version was deactivated administratively, for instance because the publisher's contributions
     * were revoked or their publisher agreement is no longer signed. It is not publicly available, but
     * its files are still there and it can become {@link #ACTIVE} again.
     */
    INACTIVE,

    /**
     * The version is no longer available for download. Reported both for a version that was deleted,
     * keeping its metadata as a tombstone so that it can never be published again, and for one that was
     * purged, taking its metadata with it. The two differ only in what the registry keeps internally,
     * which a consumer of the feed cannot act on: either way the version is gone.
     */
    REMOVED
}
