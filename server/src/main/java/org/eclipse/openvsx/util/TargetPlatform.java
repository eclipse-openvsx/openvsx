/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.util;

import java.util.List;

public class TargetPlatform {

    public static final String NAME_UNIVERSAL = "universal";
    public static final String NAME_WIN32_X64 = "win32-x64";
    public static final String NAME_WIN32_IA32 = "win32-ia32";
    public static final String NAME_WIN32_ARM64 = "win32-arm64";
    public static final String NAME_LINUX_X64 = "linux-x64";
    public static final String NAME_LINUX_ARM64 = "linux-arm64";
    public static final String NAME_LINUX_ARMHF = "linux-armhf";
    public static final String NAME_ALPINE_X64 = "alpine-x64";
    public static final String NAME_ALPINE_ARM64 = "alpine-arm64";
    public static final String NAME_DARWIN_X64 = "darwin-x64";
    public static final String NAME_DARWIN_ARM64 = "darwin-arm64";
    public static final String NAME_WEB = "web";

    public static final String NAMES_PATH_PARAM_REGEX = NAME_WIN32_X64 + "|" + NAME_WIN32_IA32 + "|" + NAME_WIN32_ARM64
            + "|" +
            NAME_LINUX_X64 + "|" + NAME_LINUX_ARM64 + "|" + NAME_LINUX_ARMHF + "|" +
            NAME_ALPINE_X64 + "|" + NAME_ALPINE_ARM64 + "|" +
            NAME_DARWIN_X64 + "|" + NAME_DARWIN_ARM64 + "|" +
            NAME_WEB + "|" + NAME_UNIVERSAL;

    /**
     * Any known platform name, or nothing at all: unlike a path segment, a query parameter can be absent
     * or empty, and neither is a request for an unknown platform. For validating the parameter forms of
     * {@code targetPlatform}, whose {@code allowableValues} the path-segment regex above cannot enforce.
     */
    public static final String NAMES_PARAM_REGEX = "(" + NAMES_PATH_PARAM_REGEX + ")?";

    public static final List<String> TARGET_PLATFORM_NAMES = List.of(
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
            NAME_UNIVERSAL);

    private TargetPlatform() {
    }

    public static boolean isValid(String targetPlatform) {
        return targetPlatform != null && TARGET_PLATFORM_NAMES.contains(targetPlatform);
    }

    public static boolean isUniversal(String targetPlatform) {
        return NAME_UNIVERSAL.equals(targetPlatform);
    }
}
