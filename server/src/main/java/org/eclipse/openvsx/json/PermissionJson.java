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

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "Permission",
    description = "An admin permission that can be granted to a user"
)
public record PermissionJson(
        @Schema(description = "The permission name, as accepted when granting it") String value,
        @Schema(description = "A human-readable name for the permission") String label
) {}
