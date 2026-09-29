/*******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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
package org.eclipse.openvsx.util;

import java.util.List;

public class VersionAlias {

    public static final String LATEST = "latest";
    public static final String PRE_RELEASE = "pre-release";
    public static final String PREVIEW = "preview"; // old version alias

    public static final List<String> ALIAS_NAMES = List.of(LATEST, PRE_RELEASE, PREVIEW);

    public static boolean isAlias(String version) {
        return LATEST.equalsIgnoreCase(version) || PRE_RELEASE.equalsIgnoreCase(version);
    }
}
