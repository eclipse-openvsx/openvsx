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
package org.eclipse.openvsx.util;

import java.util.Locale;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Normalisation of extension display names for comparing them as a reader would: casing and
 * surrounding whitespace are not visible enough to tell two names apart.
 */
public final class DisplayNameUtil {

    /**
     * Every Unicode space separator (category Zs), all of which the extension validator admits in a
     * display name. Must match the set in the expression of {@code extension_version_display_name_idx}.
     */
    public static final String SURROUNDING_WHITESPACE = "   "
            + "           "
            + "  　";

    private DisplayNameUtil() {
    }

    /**
     * The display name with surrounding whitespace removed and lower cased, {@code ""} for {@code null}.
     */
    public static String normalize(@Nullable String displayName) {
        return strip(displayName).toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the display name shows nothing, and so carries no name.
     */
    public static boolean isBlank(@Nullable String displayName) {
        return StringUtils.isBlank(displayName) || strip(displayName).isEmpty();
    }

    private static String strip(@Nullable String displayName) {
        return Objects.toString(StringUtils.strip(displayName, SURROUNDING_WHITESPACE), "");
    }
}
