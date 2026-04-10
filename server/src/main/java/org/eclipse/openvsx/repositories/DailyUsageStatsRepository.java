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
package org.eclipse.openvsx.repositories;

import com.azure.core.annotation.QueryParam;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.DailyUsageStats;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DailyUsageStatsRepository extends Repository<DailyUsageStats, Long> {
    DailyUsageStats findDailyUsageStatsByCustomerAndDate(Customer customer, LocalDate date);

    DailyUsageStats save(DailyUsageStats dailyUsageStats);

    @Query(value = """
        SELECT date_trunc ('day', windowStart) FROM UsageStats where customer = :customer
        GROUP BY date_trunc ('day', windowStart)
        HAVING date_trunc ('day', windowStart) NOT IN (SELECT date from DailyUsageStats where customer = :customer)
        ORDER BY date_trunc ('day', windowStart)
        """
    )
    List<LocalDateTime> findUnprocessedDays(@QueryParam("customer") Customer customer);
}
