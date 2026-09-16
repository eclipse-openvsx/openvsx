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
package org.eclipse.openvsx.cdn;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CdnPurgeServiceTest {

    private final JobRequestScheduler scheduler = Mockito.mock(JobRequestScheduler.class);

    private CdnPurgeService service(boolean enabled) {
        var config = new CdnPurgeConfig();
        if (enabled) {
            ReflectionTestUtils.setField(config, "provider", "fastly");
            ReflectionTestUtils.setField(config, "fastlyServiceId", "svc-1");
            ReflectionTestUtils.setField(config, "fastlyApiToken", "token-1");
        }
        return new CdnPurgeService(config, scheduler);
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commit() {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private CdnPurgeJobRequest enqueuedRequest() {
        var captor = ArgumentCaptor.forClass(CdnPurgeJobRequest.class);
        verify(scheduler).enqueue(captor.capture());
        return captor.getValue();
    }

    // Publishing evicts the extension JSONs, the latest version and the namespace details in one
    // transaction: three calls naming two keys, and the CDN is told once.
    @Test
    void sendsOnePurgeForEverythingOneTransactionChanged() {
        var service = service(true);
        beginTransaction();

        service.purgeExtension("foo", "bar");
        service.purgeExtension("foo", "bar");
        service.purgeNamespace("foo");
        verify(scheduler, never()).enqueue(Mockito.any(CdnPurgeJobRequest.class));

        commit();

        assertThat(enqueuedRequest().getKeys()).containsExactly("ext/foo/bar", "ns/foo");
    }

    // A purge sent inside the transaction races its commit: the CDN refetches, gets the row as it
    // was, and caches that - staler than if nothing had been purged.
    @Test
    void sendsNothingBeforeTheTransactionCommits() {
        var service = service(true);
        beginTransaction();

        service.purgeExtension("foo", "bar");

        verify(scheduler, never()).enqueue(Mockito.any(CdnPurgeJobRequest.class));
    }

    @Test
    void sendsNothingForATransactionThatRolledBack() {
        var service = service(true);
        beginTransaction();
        service.purgeExtension("foo", "bar");

        rollback();

        verify(scheduler, never()).enqueue(Mockito.any(CdnPurgeJobRequest.class));
    }

    @Test
    void sendsStraightAwayOutsideATransaction() {
        var service = service(true);

        service.purgeExtension("foo", "bar");

        assertThat(enqueuedRequest().getKeys()).containsExactly("ext/foo/bar", "ns/foo");
    }

    // A deploy, not a data change, is what makes the entry HTML stale, so nothing in this class
    // calls purgeWebui() itself - only AdminAPI#purgeWebuiCache does - but it still coalesces and
    // waits for a commit like every other key, in case it is ever called from inside one.
    @Test
    void purgesTheWebuiEntryHtmlByItsFixedKey() {
        var service = service(true);

        service.purgeWebui();

        assertThat(enqueuedRequest().getKeys()).containsExactly("webui-html");
    }

    @Test
    void sendsNothingWithoutAConfiguredProvider() {
        var service = service(false);
        beginTransaction();

        service.purgeExtension("foo", "bar");
        service.purgeNamespace("foo");
        commit();

        verify(scheduler, never()).enqueue(Mockito.any(CdnPurgeJobRequest.class));
    }

    // A second transaction in the same thread must not inherit the first one's keys.
    @Test
    void forgetsWhatTheLastTransactionCollected() {
        var service = service(true);
        beginTransaction();
        service.purgeExtension("foo", "bar");
        commit();

        beginTransaction();
        service.purgeNamespace("other");
        commit();

        var captor = ArgumentCaptor.forClass(CdnPurgeJobRequest.class);
        verify(scheduler, Mockito.times(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues().get(1).getKeys()).containsExactly("ns/other");
    }
}
