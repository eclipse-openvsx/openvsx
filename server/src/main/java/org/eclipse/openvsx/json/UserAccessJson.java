/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The whole of a user's admin access, as the state it should be left in - not as a delta. Role and
 * permissions travel together so one save applies both, or neither.
 */
@Schema(
    name = "UserAccess",
    description = "Request body for replacing a user's role and permissions"
)
public record UserAccessJson(
        @Schema(
            description = "The role to assign to the user, or 'none' to remove their role",
            allowableValues = {
                "admin",
                "privileged",
                "none"
            }
        ) String role,

        @Schema(
            description = "The permissions the user should hold afterwards, replacing the ones they hold now"
        ) List<String> permissions
) {
}
