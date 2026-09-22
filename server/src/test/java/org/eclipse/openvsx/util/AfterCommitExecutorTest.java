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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor = new AfterCommitExecutor();

    private final AtomicInteger ran = new AtomicInteger();

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void complete(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(status));
    }

    // The point of the class: a cache evicted before the commit can be refilled from the row as it
    // still is, and that entry then outlives the commit.
    @Test
    void waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(ran::incrementAndGet);
        assertThat(ran).hasValue(0);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(ran).hasValue(1);
    }

    @Test
    void runsNothingForATransactionThatRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(ran::incrementAndGet);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(ran).hasValue(0);
    }

    @Test
    void runsStraightAwayWithoutATransaction() {
        executor.execute(ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    // Nobody is waiting for the result, so a failure would otherwise vanish into the executor.
    @Test
    void survivesATaskThatThrows() {
        assertThatCode(() -> executor.execute(() -> {
            throw new IllegalStateException("nope");
        })).doesNotThrowAnyException();
    }
}
