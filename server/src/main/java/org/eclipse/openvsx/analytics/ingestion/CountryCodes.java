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
package org.eclipse.openvsx.analytics.ingestion;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Normalizes log-provided country values (ISO codes or English country names, as emitted by
 * Fastly's {@code geo_country}) to two-letter ISO 3166-1 codes. Unknown values map to null.
 */
final class CountryCodes {

    private static final Set<String> ISO_CODES = Set.of(Locale.getISOCountries());

    private static final Map<String, String> NAME_TO_CODE = ISO_CODES.stream().collect(
            Collectors.toMap(
                    code -> Locale.of("", code).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ENGLISH),
                    code -> code,
                    (first, second) -> first));

    private CountryCodes() {
    }

    public static @Nullable String toIsoCode(@Nullable String country) {
        if (StringUtils.isBlank(country)) {
            return null;
        }

        var trimmed = country.trim();
        if (trimmed.length() == 2) {
            var code = trimmed.toUpperCase(Locale.ENGLISH);
            return ISO_CODES.contains(code) ? code : null;
        }

        return NAME_TO_CODE.get(trimmed.toLowerCase(Locale.ENGLISH));
    }
}
