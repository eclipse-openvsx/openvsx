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

///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

import java.util.Locale;

// Prints the country name to ISO code table as "<english name in lower case>\t<code>", one per line,
// for scripts/backfill-download-events.sh to map the country names Fastly writes.
//
// This is the same derivation as CountryCodes in the server - the display names of
// Locale.getISOCountries() in English - rather than a copy of the table, so a backfilled row carries
// the country the live ingestion would have stored. It takes no dependencies, so it runs as
// `java CountryCodesTable.java` without jbang.
void main() {
    for (var code : Locale.getISOCountries()) {
        var name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
        System.out.println(name + "\t" + code);
    }
}
