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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(
    name = "CustomerMembershipList",
    description = "Metadata of a customer member list"
)
@JsonInclude(Include.NON_NULL)
public class CustomerMembershipListJson extends ResultJson {

    public static CustomerMembershipListJson error(String message) {
        var result = new CustomerMembershipListJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "List of memberships")
    @NotNull
    private List<CustomerMembershipJson> customerMemberships;

    public List<CustomerMembershipJson> getCustomerMemberships() {
        return customerMemberships;
    }

    public void setCustomerMemberships(List<CustomerMembershipJson> customerMemberships) {
        this.customerMemberships = customerMemberships;
    }
}
