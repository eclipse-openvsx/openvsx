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
package org.eclipse.openvsx.scanning;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.Streamable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.repositories.ScannerJobRepository;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code recoverOnStartup()} fires independently in every instance's own JVM on
 * {@code ApplicationReadyEvent}. With multiple pods (e.g. mid rolling-update) several can start within
 * the same window and each would otherwise run the whole recovery pass redundantly - not just noisy
 * duplicate logging, but genuinely duplicate work for the parts that are not idempotent across
 * instances (re-enqueueing the same stuck job twice submits it for scanning twice). These tests cover
 * both halves of the fix: the advisory lock's actual mutual-exclusion semantics against real Postgres
 * connections, and that {@code recoverOnStartup()} correctly skips/proceeds based on it.
 * <p>
 * The service under test is constructed by hand from plain Mockito mocks ({@link #newService}) rather
 * than autowired, and the tests exercise the advisory lock through {@link #dataSource} directly - the
 * only real Spring bean this needs is a {@code DataSource}. A bare {@code @SpringBootTest} would still
 * boot the entire {@code RegistryApplication} regardless (every Spring Data JPA repository, JobRunr,
 * the web layer, and more), so this uses a minimal context that imports just that.
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest(
    classes = ExtensionScanJobRecoveryServiceTest.DataSourceOnlyTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ExtensionScanJobRecoveryServiceTest extends AbstractPostgresContainerTest {

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataSourceAutoConfiguration.class)
    static class DataSourceOnlyTestConfig {
    }

    @Autowired
    DataSource dataSource;

    @Mock
    ScannerJobRepository scanJobRepository;
    @Mock
    ScannerRegistry scannerRegistry;
    @Mock
    RepositoryService repositories;
    @Mock
    ExtensionScanPersistenceService persistenceService;
    @Mock
    ExtensionScanService scanService;
    @Mock
    ExtensionScanCompletionService completionService;
    @Mock
    PublishCheckRunner publishCheckRunner;
    @Mock
    PublishExtensionVersionService publishService;
    @Mock
    ExtensionService extensionService;
    @Mock
    JobRequestScheduler jobScheduler;
    @Mock
    DSLContext dsl;
    @Mock
    PlatformTransactionManager transactionManager;

    private ExtensionScanJobRecoveryService newService(DSLContext dslContext) {
        return new ExtensionScanJobRecoveryService(
                scanJobRepository,
                scannerRegistry,
                repositories,
                persistenceService,
                scanService,
                completionService,
                publishCheckRunner,
                publishService,
                extensionService,
                jobScheduler,
                dslContext,
                transactionManager);
    }

    // Proves the actual Postgres mechanism recoverOnStartup() relies on: two separate connections
    // (standing in for two pods) contend for the same key, the second fails while the first's
    // transaction is still open, and the lock becomes available again the instant the first commits -
    // with no explicit unlock call anywhere.
    @Test
    void tryAcquireRecoveryLock_isMutuallyExclusiveAcrossConnectionsAndReleasedOnCommit() throws Exception {
        try (var conn1 = dataSource.getConnection(); var conn2 = dataSource.getConnection()) {
            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            var service1 = newService(DSL.using(conn1, SQLDialect.POSTGRES));
            var service2 = newService(DSL.using(conn2, SQLDialect.POSTGRES));

            assertThat(service1.tryAcquireRecoveryLock())
                    .as("first connection acquires the lock")
                    .isTrue();
            assertThat(service2.tryAcquireRecoveryLock())
                    .as("second connection must not acquire it while the first transaction is open")
                    .isFalse();

            conn1.commit();

            assertThat(service2.tryAcquireRecoveryLock())
                    .as("lock is released automatically once the holder's transaction commits")
                    .isTrue();

            conn2.commit();
        }
    }

    @Test
    void recoverOnStartup_skipsRecoveryWhenAnotherInstanceHoldsTheLock() {
        var service = Mockito.spy(newService(dsl));
        when(scanService.isEnabled()).thenReturn(true);
        doReturn(false).when(service).tryAcquireRecoveryLock();

        service.recoverOnStartup();

        verifyNoInteractions(scanJobRepository, repositories, persistenceService, completionService);
    }

    @Test
    void recoverOnStartup_runsRecoveryWhenLockIsAcquired() {
        var service = Mockito.spy(newService(dsl));
        when(scanService.isEnabled()).thenReturn(true);
        doReturn(true).when(service).tryAcquireRecoveryLock();
        // Nothing to recover in any state - just proving the recovery pass actually ran.
        when(repositories.findExtensionScansByStatus(ArgumentMatchers.any())).thenReturn(Streamable.empty());

        service.recoverOnStartup();

        // recoverPendingJobs() is the first thing a successful acquisition leads to.
        verify(scanJobRepository).findByStatusIn(ArgumentMatchers.anyList());
    }

    @Test
    void watchdog_doesNotFailAJobThatWasClaimedWhileReEnqueueFailed() throws Exception {
        var scanner = Mockito.mock(Scanner.class);
        when(scannerRegistry.getScanner("argus")).thenReturn(scanner);
        var stale = new ScannerJob();
        stale.setId(1);
        stale.setScanId("scan");
        stale.setScannerType("argus");
        stale.setStatus(ScannerJob.JobStatus.QUEUED);
        stale.setCreatedAt(TimeUtil.getCurrentUTC().minusMinutes(10));
        when(
                scanJobRepository.findByStatusAndCreatedAtBeforeAndRecoveryInProgressFalse(
                        ArgumentMatchers.eq(ScannerJob.JobStatus.QUEUED),
                        ArgumentMatchers.any()))
                .thenReturn(List.of(stale));
        doThrow(new RuntimeException("queue down")).when(jobScheduler).enqueue(ArgumentMatchers.any(JobRequest.class));
        // a worker picked the job up in the meantime
        var claimed = new ScannerJob();
        claimed.setId(1);
        claimed.setScanId("scan");
        claimed.setStatus(ScannerJob.JobStatus.PROCESSING);
        when(scanJobRepository.findById(1L)).thenReturn(Optional.of(claimed));

        newService(dsl).run(null);

        assertThat(claimed.getStatus()).isEqualTo(ScannerJob.JobStatus.PROCESSING);
        verify(scanJobRepository, never()).trySave(claimed);
        verifyNoInteractions(completionService);
    }

    private ScannerJob stuckQueuedJob() {
        var scanner = Mockito.mock(Scanner.class);
        when(scannerRegistry.getScanner("argus")).thenReturn(scanner);
        var job = new ScannerJob();
        job.setId(1);
        job.setScanId("scan");
        job.setScannerType("argus");
        job.setStatus(ScannerJob.JobStatus.QUEUED);
        job.setCreatedAt(TimeUtil.getCurrentUTC().minusMinutes(10));
        when(
                scanJobRepository.findByStatusAndCreatedAtBeforeAndRecoveryInProgressFalse(
                        ArgumentMatchers.eq(ScannerJob.JobStatus.QUEUED),
                        ArgumentMatchers.any()))
                .thenReturn(List.of(job));
        return job;
    }

    @Test
    void watchdog_commitsTheRecoveryFlagOnlyAfterTheEnqueue() throws Exception {
        var job = stuckQueuedJob();

        newService(dsl).run(null);

        var inOrder = Mockito.inOrder(transactionManager, scanJobRepository, jobScheduler);
        inOrder.verify(transactionManager).getTransaction(ArgumentMatchers.any());
        inOrder.verify(scanJobRepository).saveAndFlush(job);
        inOrder.verify(jobScheduler).enqueue(ArgumentMatchers.any(JobRequest.class));
        inOrder.verify(transactionManager).commit(ArgumentMatchers.any());
        assertThat(job.isRecoveryInProgress()).isTrue();
    }

    @Test
    void watchdog_rollsTheRecoveryFlagBackWhenTheEnqueueFails() throws Exception {
        stuckQueuedJob();
        doThrow(new RuntimeException("queue down")).when(jobScheduler).enqueue(ArgumentMatchers.any(JobRequest.class));

        newService(dsl).run(null);

        verify(transactionManager).rollback(ArgumentMatchers.any());
        verify(transactionManager, never()).commit(ArgumentMatchers.any());
    }

    @Test
    void watchdog_skipsAJobThatChangedConcurrently() throws Exception {
        var job = stuckQueuedJob();
        when(scanJobRepository.saveAndFlush(job))
                .thenThrow(new ObjectOptimisticLockingFailureException(ScannerJob.class, 1L));

        newService(dsl).run(null);

        verify(jobScheduler, never()).enqueue(ArgumentMatchers.any(JobRequest.class));
        verify(scanJobRepository, never()).findById(ArgumentMatchers.anyLong());
        verifyNoInteractions(completionService);
    }

    @Test
    void recoverOnStartup_doesNotEvenCheckTheLockWhenScanningIsDisabled() {
        var service = Mockito.spy(newService(dsl));
        when(scanService.isEnabled()).thenReturn(false);

        service.recoverOnStartup();

        verify(service, never()).tryAcquireRecoveryLock();
    }
}
