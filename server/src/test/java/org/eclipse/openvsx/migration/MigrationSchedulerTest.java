package org.eclipse.openvsx.migration;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.mirror.MirrorConfig;

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
        var mirrorConfig = new MirrorConfig();
        ReflectionTestUtils.setField(mirrorConfig, "enabled", true);
        var migrationScheduler = new MigrationScheduler(orphanNamespaceMigration, scheduler, mirrorConfig);
        ReflectionTestUtils.setField(migrationScheduler, "migrationItemsCron", "0 * * * *");

        migrationScheduler.run(new HandlerJobRequest<>(MigrationScheduler.class));

        verify(scheduler).scheduleRecurrently(eq("schedule-migration-items"), eq("0 * * * *"), any());
    }
}
