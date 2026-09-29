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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CountryCodesTest {

    @Test
    public void testIsoCodesPassThrough() {
        assertEquals("US", CountryCodes.toIsoCode("US"));
        assertEquals("US", CountryCodes.toIsoCode("us"));
        assertEquals("DE", CountryCodes.toIsoCode("de"));
    }

    @Test
    public void testEnglishCountryNames() {
        assertEquals("US", CountryCodes.toIsoCode("united states"));
        assertEquals("DE", CountryCodes.toIsoCode("Germany"));
        assertEquals("NL", CountryCodes.toIsoCode("netherlands"));
    }

    @Test
    public void testUnknownValues() {
        assertNull(CountryCodes.toIsoCode(null));
        assertNull(CountryCodes.toIsoCode(""));
        assertNull(CountryCodes.toIsoCode("atlantis"));
        assertNull(CountryCodes.toIsoCode("zz"));
    }
}
