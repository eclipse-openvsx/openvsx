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
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.json.TierJson;

@Entity
public class Tier implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "tierSeq")
    @SequenceGenerator(name = "tierSeq", sequenceName = "tier_seq")
    private long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TierType tierType = TierType.NON_FREE;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    @Convert(converter = DurationSecondsConverter.class)
    private Duration duration = Duration.ofMinutes(5);

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RefillStrategy refillStrategy = RefillStrategy.GREEDY;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TierType getTierType() {
        return tierType;
    }

    public void setTierType(TierType tierType) {
        this.tierType = tierType;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public RefillStrategy getRefillStrategy() {
        return refillStrategy;
    }

    public void setRefillStrategy(RefillStrategy refillStrategy) {
        this.refillStrategy = refillStrategy;
    }

    public TierJson toJson() {
        var json = new TierJson();
        json.setName(name);
        json.setDescription(description);
        json.setTierType(tierType.name());
        json.setCapacity(capacity);
        json.setDuration(duration.toSeconds());
        json.setRefillStrategy(refillStrategy.name());
        return json;
    }

    public Tier updateFromJson(TierJson json) {
        setName(json.getName());
        setDescription(json.getDescription());
        setTierType(TierType.valueOf(json.getTierType()));
        setCapacity(json.getCapacity());
        setDuration(Duration.ofSeconds(json.getDuration()));
        setRefillStrategy(RefillStrategy.valueOf(json.getRefillStrategy()));
        return this;
    }

    public static Tier fromJson(TierJson json) {
        var tier = new Tier();
        tier.setName(json.getName());
        tier.setDescription(json.getDescription());
        tier.setTierType(TierType.valueOf(json.getTierType()));
        tier.setCapacity(json.getCapacity());
        tier.setDuration(Duration.ofSeconds(json.getDuration()));
        tier.setRefillStrategy(RefillStrategy.valueOf(json.getRefillStrategy()));
        return tier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tier that = (Tier) o;
        return id == that.id
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(tierType, that.tierType)
                && Objects.equals(capacity, that.capacity)
                && Objects.equals(duration, that.duration)
                && Objects.equals(refillStrategy, that.refillStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, tierType, capacity, duration, refillStrategy);
    }

    @Override
    public String toString() {
        return "Tier{" +
                "name='" + name + "'" +
                ", description='" + description + "'" +
                ", tierType=" + tierType +
                ", capacity=" + capacity +
                ", duration=" + duration +
                ", refillStrategy=" + refillStrategy +
                '}';
    }
}
