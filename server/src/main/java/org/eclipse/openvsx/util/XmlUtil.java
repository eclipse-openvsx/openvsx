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

import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlUtil.class);

    private XmlUtil() {
    }

    /**
     * Parses the provided string as an XML document using a safe {@code DocumentBuilder}
     * instance to prevent XXE attacks.
     *
     * @param input the XML document as string
     * @return a {@code Document} instance if parsing succeeded, or {@code null} otherwise.
     */
    public static @Nullable Document safeParse(String input) {
        try (var reader = new StringReader(input)) {
            var builder = safeDocumentBuilder();
            if (builder != null) {
                return builder.parse(new InputSource(reader));
            }
        } catch (SAXException | IOException e) {
            LOGGER.error("Failed to parse XML Document: {}", e.getMessage());
        }

        return null;
    }

    private static DocumentBuilder safeDocumentBuilder() {
        // construct a safe DocumentBuilder to prevent XXE attacks
        // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        String[] featuresToEnable = {
            // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all
            // XML entity attacks are prevented
            // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
            "http://apache.org/xml/features/disallow-doctype-decl",
        };

        String[] featuresToDisable = {
            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
            // JDK7+ - http://xml.org/sax/features/external-general-entities
            // This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
            "http://xml.org/sax/features/external-general-entities",

            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
            // JDK7+ - http://xml.org/sax/features/external-parameter-entities
            // This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
            "http://xml.org/sax/features/external-parameter-entities",

            // Disable external DTDs as well
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        };

        for (String feature : featuresToEnable) {
            try {
                dbf.setFeature(feature, true);
            } catch (ParserConfigurationException e) {
                LOGGER.debug("The feature '{}' is not supported by your XML processor.", feature);
            }
        }

        for (String feature : featuresToDisable) {
            try {
                dbf.setFeature(feature, false);
            } catch (ParserConfigurationException e) {
                LOGGER.debug("The feature '{}' is not supported by your XML processor.", feature);
            }
        }

        try {
            // Add these as per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            // As stated in the documentation, "Feature for Secure Processing (FSP)" is the central mechanism that will
            // help you safeguard XML processing. It instructs XML processors, such as parsers, validators,
            // and transformers, to try and process XML securely, and the FSP can be used as an alternative to
            // dbf.setExpandEntityReferences(false); to allow some safe level of Entity Expansion
            // Exists from JDK6.
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            var builder = dbf.newDocumentBuilder();
            // disable explicit logging in the xml parser
            builder.setErrorHandler(null);
            return builder;
        } catch (ParserConfigurationException e) {
            LOGGER.error("Could not build a safe XML processor: {}", e.getMessage());
        }

        return null;
    }
}
