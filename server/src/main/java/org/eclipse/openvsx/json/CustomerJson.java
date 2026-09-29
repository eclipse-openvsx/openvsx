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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerJson extends ResultJson {
    @NotNull
    private String name;

    private TierJson tier;

    @NotNull
    private String state;

    private List<String> cidrBlocks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TierJson getTier() {
        return tier;
    }

    public void setTier(TierJson tier) {
        this.tier = tier;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<String> getCidrBlocks() {
        return cidrBlocks;
    }

    public void setCidrBlocks(List<String> cidrBlocks) {
        this.cidrBlocks = cidrBlocks;
    }

    public static CustomerJson error(String message) {
        var json = new CustomerJson();
        json.setError(message);
        return json;
    }
}
