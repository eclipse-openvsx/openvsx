/*******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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
package org.eclipse.openvsx.migration;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

// The type parameter is deliberately bounded by the non-specific JobRequestHandler<?> rather than
// JobRequestHandler<MigrationJobRequest<?>>: that self-referential bound (T's bound mentions this
// class parametrized with itself again) sends Jackson's generic type resolution into infinite
// recursion -- a StackOverflowError wrapped as a DatabindException -- while serializing the
// `handler` field for JobRunr's queue storage. See MigrationJobRequestTest for a standalone repro.
public class MigrationJobRequest<T extends JobRequestHandler<?>> implements JobRequest {

    // Not every MigrationJobRequest is backed by a migration_item row: GenerateKeyPairJobRequestHandler
    // reuses this same "run this handler for this entity id" shape to enqueue signature jobs directly,
    // outside the migration_item bookkeeping table entirely. 0 (no real id ever has this value; the
    // backing sequence starts at 1) marks "not applicable" for MigrationItemCleanupFilter to skip.
    private static final long NO_MIGRATION_ITEM = 0;

    private Class<T> handler;
    private long entityId;
    // The governing migration_item row's id, so MigrationItemCleanupFilter can delete exactly that
    // row once this job completes -- see that class for why deletion doesn't live in each handler.
    private long migrationItemId = NO_MIGRATION_ITEM;

    public MigrationJobRequest() {
    }

    /** For a MigrationJobRequest not backed by any migration_item row. */
    public MigrationJobRequest(Class<T> handler, long entityId) {
        this.handler = handler;
        this.entityId = entityId;
    }

    public MigrationJobRequest(Class<T> handler, long entityId, long migrationItemId) {
        this.handler = handler;
        this.entityId = entityId;
        this.migrationItemId = migrationItemId;
    }

    @Override
    public Class<T> getJobRequestHandler() {
        return handler;
    }

    public void setJobRequestHandler(Class<T> handler) {
        this.handler = handler;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public long getMigrationItemId() {
        return migrationItemId;
    }

    public void setMigrationItemId(long migrationItemId) {
        this.migrationItemId = migrationItemId;
    }
}
