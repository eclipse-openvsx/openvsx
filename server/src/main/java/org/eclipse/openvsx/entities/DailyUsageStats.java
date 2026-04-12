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
package org.eclipse.openvsx.entities;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "daily_usage_stats")
public class DailyUsageStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "dailyUsageStatsSeq")
    @SequenceGenerator(name = "dailyUsageStatsSeq", sequenceName = "daily_usage_stats_seq", allocationSize = 1)
    private long id;

    @ManyToOne
    private Customer customer;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private long totalRequests = 0;

    @Column(name = "p95_requests", nullable = false)
    private long p95Requests = 0;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getP95Requests() {
        return p95Requests;
    }

    public void setP95Requests(long p95Requests) {
        this.p95Requests = p95Requests;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyUsageStats that = (DailyUsageStats) o;
        return id == that.id
            && Objects.equals(customer, that.customer)
            && Objects.equals(date, that.date)
            && Objects.equals(totalRequests, that.totalRequests)
            && Objects.equals(p95Requests, that.p95Requests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customer, date, totalRequests, p95Requests);
    }

    @Override
    public String toString() {
        return "UsageStats{" +
                "customer='" + customer.getName() + '\'' +
                ", date=" + date +
                ", totalRequests=" + totalRequests +
                ", p95Requests=" + p95Requests +
                '}';
    }
}
