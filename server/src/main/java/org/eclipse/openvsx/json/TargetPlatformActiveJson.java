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

import io.swagger.v3.oas.annotations.media.Schema;

import static org.eclipse.openvsx.util.TargetPlatform.*;

/**
 *
 * @param targetPlatform Name of the target platform
 * @param active Whether this target platform version is active
 * @param removed Whether this target platform version has been removed (soft-deleted)
 */
@Schema(
    name = "TargetPlatformActive",
    description = "Target platform of an extension version and whether it is active"
)
public record TargetPlatformActiveJson(
        @Schema(
            description = "Name of the target platform",
            allowableValues = {
                NAME_WIN32_X64,
                NAME_WIN32_IA32,
                NAME_WIN32_ARM64,
                NAME_LINUX_X64,
                NAME_LINUX_ARM64,
                NAME_LINUX_ARMHF,
                NAME_ALPINE_X64,
                NAME_ALPINE_ARM64,
                NAME_DARWIN_X64,
                NAME_DARWIN_ARM64,
                NAME_WEB,
                NAME_UNIVERSAL
            }
        ) String targetPlatform,
        @Schema(description = "Whether this extension version for this target platform is active") boolean active,
        @Schema(
            description = "Whether this extension version for this target platform has been removed (soft-deleted)"
        ) boolean removed
) {}
