/********************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.mirror;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.server.runner.ThreadLocalJobContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.UrlConfigService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.XmlUtil;

import static org.eclipse.openvsx.util.UrlUtil.createApiUrl;

@Component
public class DataMirrorJobRequestHandler implements JobRequestHandler<DataMirrorJobRequest> {

    protected final Logger logger = LoggerFactory.getLogger(DataMirrorJobRequestHandler.class);

    private DataMirrorService data;
    private final RepositoryService repositories;
    private final RestTemplate backgroundRestTemplate;
    private final UrlConfigService urlConfigService;
    private final ExtensionService extensions;
    private final MirrorExtensionService mirrorExtensionService;
    private final DateTimeFormatter dateFormatter;

    public DataMirrorJobRequestHandler(
            Optional<DataMirrorService> dataMirrorService,
            RepositoryService repositories,
            RestTemplate backgroundRestTemplate,
            UrlConfigService urlConfigService,
            ExtensionService extensions,
            MirrorExtensionService mirrorExtensionService
    ) {
        dataMirrorService.ifPresent(service -> this.data = service);
        this.repositories = repositories;
        this.backgroundRestTemplate = backgroundRestTemplate;
        this.urlConfigService = urlConfigService;
        this.extensions = extensions;
        this.mirrorExtensionService = mirrorExtensionService;
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }

    @Override
    @Job(name = "Data Mirror", retries = 0)
    public void run(DataMirrorJobRequest jobRequest) throws Exception {
        if (data == null) {
            return;
        }

        logger.debug(">> Starting DataMirrorJob");
        var sitemap = getSitemap();
        if (sitemap == null) {
            logger.error("failed to fetch sitemap");
            return;
        }

        try {
            var mirrorUser = data.createMirrorUser();
            var extensionIds = processUrls(sitemap.getElementsByTagName("url"), mirrorUser);
            deleteOtherExtensions(extensionIds, mirrorUser);
        } catch (Exception e) {
            logger.error("failed to mirror data", e);
            throw e;
        } finally {
            logger.debug("<< Completed DataMirrorJob");
        }
    }

    private List<String> processUrls(NodeList urls, UserData mirrorUser) {
        var jobContext = ThreadLocalJobContext.getJobContext();
        var jobLogger = jobContext.logger();

        var extensionIds = new ArrayList<String>();
        var progress = jobContext.progressBar(urls.getLength());
        for (var i = 0; i < urls.getLength(); i++) {
            var url = (Element) urls.item(i);
            var extensionId = getExtensionId(url);
            var id = NamingUtil.fromExtensionId(extensionId);
            if (id == null) {
                logger.warn("failed to get id from extension {}", extensionId);
                continue;
            }
            var namespace = id.namespace();
            var extension = id.extension();
            if (!data.match(namespace, extension)) {
                jobLogger.info("excluded, skipping " + extensionId + " (" + (i + 1) + "/" + urls.getLength() + ")");
                continue;
            }

            jobLogger.info("mirroring " + extensionId + " (" + (i + 1) + "/" + urls.getLength() + ")");
            try {
                LocalDate lastModified = getLastModified(url, extensionId);
                mirrorExtensionService.mirrorExtension(namespace, extension, mirrorUser, lastModified, jobContext);
            } catch (Exception e) {
                logger.error("failed to mirror {}", extensionId, e);
            }
            extensionIds.add(extensionId);
            progress.incrementSucceeded();
        }

        return extensionIds;
    }

    private void deleteOtherExtensions(List<String> extensionIds, UserData mirrorUser) {
        var notMatchingExtensions = repositories.findAllNotMatchingByExtensionId(extensionIds);
        for (var extension : notMatchingExtensions) {
            var extensionId = NamingUtil.toExtensionId(extension);
            ThreadLocalJobContext.getJobContext().logger().info("deleting " + extensionId);
            try {
                // Purge by name so that the extension is locked and re-read inside the purge transaction:
                // the entities of this query are detached here, they can not be removed as they are.
                extensions.purgeExtension(mirrorUser, extension.getNamespace().getName(), extension.getName());
            } catch (ErrorResultException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) {
                    logger.warn("mirror: failed to delete extension {}", extensionId, e);
                }
            } catch (Exception e) {
                logger.error("mirror: failed to delete extension {}", extensionId, e);
            }
        }
    }

    private @Nullable Document getSitemap() {
        var requestUrl = URI.create(createApiUrl(urlConfigService.getMirrorServerUrl(), "sitemap.xml"));
        var request = new RequestEntity<Void>(HttpMethod.GET, requestUrl);
        var response = backgroundRestTemplate.exchange(request, String.class);
        var body = response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
        if (body == null) {
            return null;
        }

        return XmlUtil.safeParse(body);
    }

    private String getExtensionId(Element url) {
        var location = URI.create(url.getElementsByTagName("loc").item(0).getTextContent());
        var pathParams = location.getPath().split("/");
        var namespace = pathParams[pathParams.length - 2];
        var extension = pathParams[pathParams.length - 1];
        return NamingUtil.toExtensionId(namespace, extension);
    }

    private LocalDate getLastModified(Element url, String extensionId) {
        try {
            var lastModifiedString = url.getElementsByTagName("lastmod").item(0).getTextContent();
            return LocalDate.parse(lastModifiedString, dateFormatter);
        } catch (Exception e) {
            logger.error("failed to resolve last modified date {}", extensionId, e);
        }

        return null;
    }
}
