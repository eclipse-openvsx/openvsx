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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Purges a Fastly service by surrogate key.
 *
 * @see <a href="https://www.fastly.com/documentation/reference/api/purging/">Fastly purging API</a>
 */
@Component
@ConditionalOnProperty(name = "ovsx.cdn.purge.provider", havingValue = "fastly")
public class FastlyCdnPurgeClient implements CdnPurgeClient {

    /** What Fastly accepts in one bulk purge. */
    static final int MAX_KEYS_PER_REQUEST = 256;

    private final CdnPurgeConfig config;
    private final RestClient restClient;

    public FastlyCdnPurgeClient(CdnPurgeConfig config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void purge(Collection<String> keys) {
        for (var batch : batches(keys)) {
            var request = restClient.post()
                    .uri(config.getFastlyApiUrl() + "/service/{serviceId}/purge", config.getFastlyServiceId())
                    .header("Fastly-Key", config.getFastlyApiToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON);
            if (config.isFastlySoftPurge()) {
                // stale rather than gone: the CDN keeps serving while it refetches, so publishing a
                // popular extension does not send everyone asking for it to the origin at once
                request = request.header("Fastly-Soft-Purge", "1");
            }
            request.body(Map.of("surrogate_keys", batch))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private static List<List<String>> batches(Collection<String> keys) {
        var all = List.copyOf(keys);
        var batches = new ArrayList<List<String>>();
        for (var start = 0; start < all.size(); start += MAX_KEYS_PER_REQUEST) {
            batches.add(all.subList(start, Math.min(start + MAX_KEYS_PER_REQUEST, all.size())));
        }
        return batches;
    }
}
