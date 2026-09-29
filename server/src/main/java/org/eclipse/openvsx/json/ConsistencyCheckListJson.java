/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ConsistencyCheckListJson extends ResultJson {

    public static ConsistencyCheckListJson error(String message) {
        var result = new ConsistencyCheckListJson();
        result.setError(message);
        return result;
    }

    private List<ConsistencyCheckJson> checks;

    public List<ConsistencyCheckJson> getChecks() {
        return checks;
    }

    public void setChecks(List<ConsistencyCheckJson> checks) {
        this.checks = checks;
    }
}
