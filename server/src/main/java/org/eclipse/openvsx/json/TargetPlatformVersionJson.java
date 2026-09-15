/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.json;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.eclipse.openvsx.util.TargetPlatform.*;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_ALPINE_ARM64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_ALPINE_X64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_DARWIN_ARM64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_DARWIN_X64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_LINUX_ARM64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_LINUX_ARMHF;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_LINUX_X64;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_UNIVERSAL;
import static org.eclipse.openvsx.util.TargetPlatform.NAME_WEB;

/**
 *
 * @param targetPlatform Name of the target platform
 * @param version Version of the extension
 */
@Schema(
    name = "TargetPlatformVersion",
    description = "Combination of target platform and version of an extension"
)
public record TargetPlatformVersionJson(
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
        @NotNull
        @Schema(description = "Version of the extension") String version
) {
    public TargetPlatformVersion toTargetPlatformVersion() {
        return new TargetPlatformVersion(targetPlatform, version);
    }
}
