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
package org.eclipse.openvsx.ratelimit.config;

import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

public class UsageStatsProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String jobSchedule = "0 */10 * * * *";

    @NotBlank
    private String dailyJobSchedule = "0 10 2 * * *";

    public String getJobSchedule() {
        return jobSchedule;
    }

    public void setJobSchedule(String jobSchedule) {
        this.jobSchedule = jobSchedule;
    }

    public String getDailyJobSchedule() {
        return dailyJobSchedule;
    }

    public void setDailyJobSchedule(String dailyJobSchedule) {
        this.dailyJobSchedule = dailyJobSchedule;
    }
}