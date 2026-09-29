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

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.json.CustomerJson;
import org.eclipse.openvsx.json.UsageStatsJson;

@Entity
@Table(name = "usage_stats")
public class UsageStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "usageStatsSeq")
    @SequenceGenerator(name = "usageStatsSeq", sequenceName = "usage_stats_seq")
    private long id;

    @ManyToOne
    private Customer customer;

    @Column(nullable = false)
    private LocalDateTime windowStart;

    @Column(nullable = false)
    @Convert(converter = DurationSecondsConverter.class)
    private Duration duration;

    @Column(nullable = false)
    private long count = 0;

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

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public UsageStatsJson toJson() {
        return new UsageStatsJson(windowStart.toEpochSecond(ZoneOffset.UTC), duration.toSeconds(), count);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UsageStats that = (UsageStats) o;
        return id == that.id
                && Objects.equals(customer, that.customer)
                && Objects.equals(windowStart, that.windowStart)
                && Objects.equals(duration, that.duration)
                && Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customer, windowStart, duration, count);
    }

    @Override
    public String toString() {
        return "UsageStats{" +
                "customer='" + customer.getName() + '\'' +
                ", windowStart=" + windowStart +
                ", duration=" + duration +
                ", count=" + count +
                '}';
    }
}
