/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.cdn;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where to send cache purges, and whether to send them at all.
 * <p>
 * Off unless a provider is named, so a registry with no CDN in front of it does no extra work and
 * needs no configuration. See {@code org.eclipse.openvsx.web.SurrogateKey} for what is purged.
 */
@Component
public class CdnPurgeConfig {

    /**
     * The CDN to purge, {@code ovsx.cdn.purge.provider}. Only {@code fastly} is implemented; empty,
     * the default, purges nothing.
     */
    @Value("${ovsx.cdn.purge.provider:}")
    private String provider;

    /**
     * The Fastly service whose cache is purged, {@code ovsx.cdn.purge.fastly.service-id}.
     */
    @Value("${ovsx.cdn.purge.fastly.service-id:}")
    private String fastlyServiceId;

    /**
     * A Fastly API token with the {@code purge_select} scope, {@code ovsx.cdn.purge.fastly.api-token}.
     */
    @Value("${ovsx.cdn.purge.fastly.api-token:}")
    private String fastlyApiToken;

    /**
     * Whether to purge softly, {@code ovsx.cdn.purge.fastly.soft}. A soft purge marks the cached
     * responses stale rather than dropping them, so the CDN can serve them while it fetches the new
     * ones; publishing a popular extension then does not send every client for it to the origin at
     * once. On by default: a registry is read far more than it is written.
     */
    @Value("${ovsx.cdn.purge.fastly.soft:true}")
    private boolean fastlySoftPurge;

    /**
     * The Fastly API endpoint, {@code ovsx.cdn.purge.fastly.api-url}. Configurable for testing.
     */
    @Value("${ovsx.cdn.purge.fastly.api-url:https://api.fastly.com}")
    private String fastlyApiUrl;

    public String getProvider() {
        return provider;
    }

    public String getFastlyServiceId() {
        return fastlyServiceId;
    }

    public String getFastlyApiToken() {
        return fastlyApiToken;
    }

    public boolean isFastlySoftPurge() {
        return fastlySoftPurge;
    }

    public String getFastlyApiUrl() {
        return fastlyApiUrl;
    }

    /**
     * Whether a provider is configured completely enough to purge anything. A half-configured
     * provider is treated as no provider rather than as an error: the registry serves fine without
     * purging, and failing to start over a CDN credential would be a poor trade.
     */
    public boolean isEnabled() {
        return "fastly".equalsIgnoreCase(provider)
                && StringUtils.isNotBlank(fastlyServiceId)
                && StringUtils.isNotBlank(fastlyApiToken);
    }
}
