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

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work once the surrounding transaction has committed, off the request thread.
 * <p>
 * For work that follows from a change rather than being part of it - dropping what a cache holds
 * about a row that just changed, telling something outside the database about it. Two reasons not to
 * do that inline:
 * <ul>
 *     <li><b>Inside the transaction it can make things worse.</b> A cache evicted before the commit
 *     is a cache another request can refill from the row as it still is, and that stale entry then
 *     outlives the commit - staler than if nothing had been evicted at all.</li>
 *     <li><b>It is not what the caller is waiting for.</b> The response does not depend on it, so it
 *     has no business adding to the time the caller waits.</li>
 * </ul>
 * A transaction that rolls back runs nothing: whatever it was going to change did not happen.
 * <p>
 * The work is handed to an executor, so it is not retried and does not survive a restart. That
 * suits a cache - a missed eviction costs one stale entry until its TTL - and does not suit
 * anything that must happen exactly once; put that on a job instead.
 * <p>
 * Whatever the task needs must be read <em>before</em> it is handed over: it runs on another thread
 * with no persistence context, so an entity captured in the lambda may be detached and its lazy
 * associations unreadable by then.
 */
@Component
public class AfterCommitExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AfterCommitExecutor.class);

    private final Executor executor;

    public AfterCommitExecutor(@Qualifier("applicationTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    public void execute(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // nothing to wait for; still off this thread, so callers get the same behaviour either way
            submit(task);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    submit(task);
                }
            }
        });
    }

    private void submit(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException exc) {
                // nobody is waiting for this, so a failure would otherwise be swallowed by the executor
                logger.error("Deferred task failed", exc);
            }
        });
    }
}
