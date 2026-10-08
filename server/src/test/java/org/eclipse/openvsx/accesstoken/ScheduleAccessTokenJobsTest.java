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
package org.eclipse.openvsx.accesstoken;

import java.time.LocalDateTime;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScheduleAccessTokenJobsTest {

    private final AccessTokenConfig config = mock(AccessTokenConfig.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final ScheduleAccessTokenJobs jobs = new ScheduleAccessTokenJobs(config, scheduler);

    @Test
    void delaysOneShotJobsByMigrationDelay() {
        ReflectionTestUtils.setField(jobs, "delay", 300L);
        org.mockito.Mockito.when(config.isTokenExpiryEnabled()).thenReturn(true);

        var before = TimeUtil.getCurrentUTC();
        jobs.scheduleJobs(null);
        var after = TimeUtil.getCurrentUTC();

        verify(scheduler, never()).enqueue(any(JobRequest.class));

        var when = ArgumentCaptor.forClass(LocalDateTime.class);
        var request = ArgumentCaptor.forClass(JobRequest.class);
        verify(scheduler, org.mockito.Mockito.times(2)).schedule(when.capture(), request.capture());

        assertThat(request.getAllValues())
                .extracting(r -> (Object) ((HandlerJobRequest<?>) r).getJobRequestHandler())
                .containsExactly(
                        UpgradePersonalAccessTokenHandler.class,
                        LegacyPersonalAccessTokenExpirationHandler.class);
        assertThat(when.getAllValues()).allSatisfy(
                t -> assertThat(t)
                        .isBetween(before.plusSeconds(300), after.plusSeconds(300)));
    }
}
