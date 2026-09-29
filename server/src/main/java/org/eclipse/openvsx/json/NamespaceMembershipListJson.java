/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "NamespaceMembershipList",
    description = "Metadata of a namespace member list"
)
@JsonInclude(Include.NON_NULL)
public class NamespaceMembershipListJson extends ResultJson {

    public static NamespaceMembershipListJson error(String message) {
        var result = new NamespaceMembershipListJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "List of memberships")
    @NotNull
    private List<NamespaceMembershipJson> namespaceMemberships;

    public List<NamespaceMembershipJson> getNamespaceMemberships() {
        return namespaceMemberships;
    }

    public void setNamespaceMemberships(List<NamespaceMembershipJson> namespaceMemberships) {
        this.namespaceMemberships = namespaceMemberships;
    }
}
