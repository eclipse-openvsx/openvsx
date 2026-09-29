/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerErrorException;

@RestController
public class SitemapController {

    private final SitemapService service;

    public SitemapController(SitemapService service) {
        this.service = service;
    }

    @GetMapping(path = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap() {
        try {
            var sitemap = service.generateSitemap();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                    .body(sitemap);
        } catch (ParserConfigurationException | IOException | TransformerException e) {
            throw new ServerErrorException("Failed to generate sitemap.xml", e);
        }
    }
}
