/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.web;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.UrlUtil;

import static org.eclipse.openvsx.cache.CacheService.CACHE_SITEMAP;

@Component
public class SitemapService {

    private final RepositoryService repositories;
    private final WebUiProperties webUi;

    public SitemapService(RepositoryService repositories, WebUiProperties webUi) {
        this.repositories = repositories;
        this.webUi = webUi;
    }

    @Cacheable(CACHE_SITEMAP)
    public String generateSitemap() throws ParserConfigurationException, IOException, TransformerException {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        document.setXmlStandalone(true);
        var namespace = "http://www.sitemaps.org/schemas/sitemap/0.9";
        var urlset = document.createElementNS(namespace, "urlset");
        document.appendChild(urlset);

        var baseUrl = getBaseUrl();
        var rows = repositories.fetchSitemapRows();
        for (var row : rows) {
            var entry = document.createElement("url");
            var loc = document.createElement("loc");
            loc.setTextContent(baseUrl + row.namespace() + "/" + row.extension());
            entry.appendChild(loc);

            var lastmod = document.createElement("lastmod");
            lastmod.setTextContent(row.lastUpdated());
            entry.appendChild(lastmod);
            urlset.appendChild(entry);
        }

        try (var writer = new StringWriter()) {
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        }
    }

    private String getBaseUrl() {
        var webuiUrl = webUi.getUrl();
        String url;
        if (StringUtils.isEmpty(webuiUrl)) {
            url = UrlUtil.getBaseUrl();
        } else if (URI.create(webuiUrl).isAbsolute()) {
            url = webuiUrl;
        } else {
            url = UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), webuiUrl.split("/"));
        }
        if (!url.endsWith("/")) {
            url += "/";
        }

        return url + "extension/";
    }
}
