/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.adapter;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.UrlConfigService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class VSCodeIdService {
    // TODO: check if this version is still valid when connecting to the VSC Marketplace
    private static final String API_VERSION = "3.0-preview.1";

    private final RestTemplate vsCodeIdRestTemplate;
    private final UrlConfigService urlConfigService;
    private final JobRequestScheduler scheduler;

    @Value("${ovsx.data.mirror.enabled:false}")
    boolean mirrorEnabled;

    @Value("${ovsx.vscode.upstream.update-on-start:false}")
    boolean updateOnStart;

    @Value("${ovsx.migrations.delay.seconds:0}")
    long delay;

    public VSCodeIdService(
            RestTemplate vsCodeIdRestTemplate,
            UrlConfigService urlConfigService,
            JobRequestScheduler scheduler
    ) {
        this.vsCodeIdRestTemplate = vsCodeIdRestTemplate;
        this.urlConfigService = urlConfigService;
        this.scheduler = scheduler;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        if (mirrorEnabled) {
            return;
        }
        if (updateOnStart) {
            scheduler.schedule(TimeUtil.getCurrentUTC().plusSeconds(delay), new HandlerJobRequest<>(VSCodeIdDailyUpdateJobRequestHandler.class));
        }

        scheduler.scheduleRecurrently("VSCodeIdDailyUpdate", Cron.daily(3), ZoneId.of("UTC"), new HandlerJobRequest<>(VSCodeIdDailyUpdateJobRequestHandler.class));
    }

    public String getRandomPublicId() {
        return UUID.randomUUID().toString();
    }

    public PublicIds getUpstreamPublicIds(Extension extension) {
        String extensionPublicId = null;
        String namespacePublicId = null;
        var upstream = getUpstreamExtension(extension);
        if (upstream != null) {
            extensionPublicId = upstream.extensionId();
            if (upstream.publisher() != null) {
                namespacePublicId = upstream.publisher().publisherId();
            }
        }

        return new PublicIds(namespacePublicId, extensionPublicId);
    }

    private ExtensionQueryResult.Extension getUpstreamExtension(Extension extension) {
        var galleryUrl = urlConfigService.getUpstreamGalleryUrl();
        if (StringUtils.isEmpty(galleryUrl)) {
            return null;
        }

        var requestUrl = UrlUtil.createApiUrl(galleryUrl, "extensionquery");
        var requestData = createRequestData(extension);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json;api-version=" + API_VERSION);
        var result = vsCodeIdRestTemplate.postForObject(requestUrl, new HttpEntity<>(requestData, headers), ExtensionQueryResult.class);
        if (result != null && result.results() != null && !result.results().isEmpty()) {
            var item = result.results().getFirst();
            if (item.extensions() != null && !item.extensions().isEmpty()) {
                return item.extensions().getFirst();
            }
        }

        return null;
    }

    private ExtensionQueryParam createRequestData(Extension extension) {
        var criteria = List.of(
                new ExtensionQueryParam.Criterion(
                        ExtensionQueryParam.Criterion.FILTER_TARGET,
                        "Microsoft.VisualStudio.Code"
                ),
                new ExtensionQueryParam.Criterion(
                        ExtensionQueryParam.Criterion.FILTER_EXTENSION_NAME,
                        NamingUtil.toExtensionId(extension)
                )
        );

        return new ExtensionQueryParam(List.of(new ExtensionQueryParam.Filter(criteria, 1, 1, 0, 0)), 0);
    }
}
