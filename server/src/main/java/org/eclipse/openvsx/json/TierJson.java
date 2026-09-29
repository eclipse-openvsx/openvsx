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
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TierJson extends ResultJson {
    @NotNull
    private String name;

    private String description;

    @NotNull
    private String tierType;

    private int capacity;

    private long duration;

    @NotNull
    private String refillStrategy;

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

    public String getTierType() {
        return tierType;
    }

    public void setTierType(String tierType) {
        this.tierType = tierType;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getRefillStrategy() {
        return refillStrategy;
    }

    public void setRefillStrategy(String refillStrategy) {
        this.refillStrategy = refillStrategy;
    }

    public static TierJson error(String message) {
        var json = new TierJson();
        json.setError(message);
        return json;
    }
}
