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
package org.eclipse.openvsx.consistency;

import java.util.List;

/**
 * A single, self-contained check for one kind of data inconsistency (see issue #1622: "Need a way to
 * check the database for consistency"). Implementations are picked up automatically as Spring beans by
 * {@link ConsistencyCheckService} - registering a new kind of check is exactly one new {@code @Component}
 * implementing this interface, with no other wiring needed.
 * <p>
 * Findings are always computed live from current data, never cached: a stored list of affected entities
 * would go stale the moment anything about them changes, which is exactly the kind of silent drift this
 * feature exists to catch.
 */
public interface ConsistencyCheck {

    /**
     * A stable, unique identifier for this check (e.g. {@code "extension-active-flag"}). Used as the
     * path segment in the admin API and as the key under which run history is recorded, so it must
     * never change once a check has shipped.
     */
    String getId();

    /**
     * A short, human-readable name shown in the admin UI.
     */
    String getName();

    /**
     * Explains what this check looks for and why it matters, shown in the admin UI.
     */
    String getDescription();

    /**
     * Runs the check now and returns every entity currently found inconsistent. Empty means healthy.
     */
    List<ConsistencyFinding> check();

    /**
     * Repairs the entity identified by {@code entityId} (one of {@link ConsistencyFinding#entityId()}
     * from a prior {@link #check()} call). A no-op if the entity no longer exists or is no longer
     * inconsistent (e.g. it was already fixed by something else in the meantime).
     */
    void fix(long entityId);

    /**
     * Whether the scheduled sweep (and the admin dashboard's "run now" action) should automatically fix
     * this check's findings, rather than only recording them in run history for a human to fix from the
     * dashboard. Defaults to {@code true}: a purely mechanical recomputation like
     * {@link ExtensionActiveFlagCheck} is always safe to fix unattended. Override to return
     * {@code false} only when fixing requires a judgment call a human needs to make - e.g. deciding
     * which of two conflicting records is the correct one - not merely because a check is new.
     */
    default boolean autoFixOnSchedule() {
        return true;
    }
}
