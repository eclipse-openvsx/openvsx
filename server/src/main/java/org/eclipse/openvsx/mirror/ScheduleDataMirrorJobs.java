/********************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.mirror;

import java.util.Optional;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ScheduleDataMirrorJobs {

    private DataMirrorService data;
    private final JobRequestScheduler scheduler;

    public ScheduleDataMirrorJobs(Optional<DataMirrorService> dataMirrorService, JobRequestScheduler scheduler) {
        dataMirrorService.ifPresent(service -> this.data = service);
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        if (data != null) {
            scheduler.scheduleRecurrently("DataMirror", data.getSchedule(), new DataMirrorJobRequest());
        } else {
            scheduler.deleteRecurringJob("DataMirror");
        }
    }
}
