/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work once the surrounding transaction has committed.
 * <p>
 * For work that follows from a change rather than being part of it: dropping what a cache holds
 * about a row that just changed. Doing that inside the transaction can make things worse, which is
 * the reason this exists - a cache evicted before the commit is a cache another request can refill
 * from the row as it still is, and that stale entry then outlives the commit, staler than if nothing
 * had been evicted at all. A transaction that rolls back runs nothing: whatever it was going to
 * change did not happen.
 * <p>
 * On the calling thread, deliberately. Handing the work to an executor would take it off the request
 * path too, but an eviction now costs one pattern clear rather than thousands of guesses, so there
 * is little left to save and a background task is one more thing that can be lost or go unnoticed.
 * <p>
 * A task that throws is logged rather than raised: by the time it runs the transaction has committed,
 * so the caller's work succeeded and a failure to drop a cache entry - which costs one stale entry
 * until its TTL - is not a reason to report that work as failed.
 * <p>
 * Whatever the task needs should be read <em>before</em> it is handed over. It runs outside the
 * transaction, where an entity may be detached and its lazy associations unreadable.
 */
@Component
public class AfterCommitExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AfterCommitExecutor.class);

    public void execute(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // nothing to wait for
            run(task);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    run(task);
                }
            }
        });
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exc) {
            logger.error("Deferred task failed", exc);
        }
    }
}
