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
package org.eclipse.openvsx.migration;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MigrationSchedulerTest {

    @Mock
    OrphanNamespaceMigration orphanNamespaceMigration;
    @Mock
    JobRequestScheduler scheduler;

    @Test
    void run_schedulesMigrationItemProcessingUsingTheConfiguredCron() throws Exception {
        var migrationScheduler = new MigrationScheduler(orphanNamespaceMigration, scheduler);
        ReflectionTestUtils.setField(migrationScheduler, "migrationItemsCron", "0 * * * *");
        ReflectionTestUtils.setField(migrationScheduler, "mirrorEnabled", true);

        migrationScheduler.run(new HandlerJobRequest<>(MigrationScheduler.class));

        verify(scheduler).scheduleRecurrently(eq("schedule-migration-items"), eq("0 * * * *"), any());
    }
}
