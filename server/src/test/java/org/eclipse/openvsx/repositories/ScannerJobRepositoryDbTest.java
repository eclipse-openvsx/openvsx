/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.ScannerJob;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not {@code @Transactional}: every repository call commits on its own, so the loaded jobs are detached
 * and stale the way they are in the scanner handlers.
 */
@SpringBootTest
class ScannerJobRepositoryDbTest extends AbstractPostgresContainerTest {

    @Autowired
    ScannerJobRepository repo;

    // Same mocks as AccessTokenConcurrentWriteTest, so the cached application context is reused.
    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedJobs() {
        createdIds.forEach(id -> jdbc.update("DELETE FROM scan_job WHERE id = ?", id));
    }

    private ScannerJob newJob() {
        var now = TimeUtil.getCurrentUTC();
        var job = new ScannerJob();
        job.setScanId(UUID.randomUUID().toString());
        job.setScannerType("argus");
        job.setExtensionVersionId(1);
        job.setStatus(ScannerJob.JobStatus.PROCESSING);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        var saved = repo.save(job);
        createdIds.add(saved.getId());
        return saved;
    }

    @Test
    void staleSave_cannotOverwriteAVerdictRecordedMeanwhile() {
        long id = newJob().getId();
        var watchdogView = repo.findById(id).orElseThrow();
        var handlerView = repo.findById(id).orElseThrow();

        watchdogView.setStatus(ScannerJob.JobStatus.FAILED);
        assertThat(repo.trySave(watchdogView)).isTrue();

        handlerView.setStatus(ScannerJob.JobStatus.COMPLETE);
        assertThat(repo.trySave(handlerView)).isFalse();

        assertThat(repo.findById(id).orElseThrow().getStatus()).isEqualTo(ScannerJob.JobStatus.FAILED);
    }

    @Test
    void claimForProcessing_invalidatesJobsLoadedBeforeIt() {
        var job = newJob();
        job.setStatus(ScannerJob.JobStatus.QUEUED);
        repo.save(job);
        var stale = repo.findById(job.getId()).orElseThrow();

        assertThat(repo.claimForProcessing(job.getId(), TimeUtil.getCurrentUTC())).isEqualTo(1);

        stale.setStatus(ScannerJob.JobStatus.FAILED);
        assertThat(repo.trySave(stale)).isFalse();
        assertThat(repo.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(ScannerJob.JobStatus.PROCESSING);
    }
}
