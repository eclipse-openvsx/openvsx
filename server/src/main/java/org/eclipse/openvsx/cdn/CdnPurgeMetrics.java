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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * How much of what this registry asked the CDN to drop actually got dropped.
 * <p>
 * Worth watching, because a failed purge is invisible from the outside: the registry answers
 * correctly and the CDN keeps serving what it had, so the only symptom is a reader seeing an old
 * version for as long as the cached response lives.
 */
@Component
public class CdnPurgeMetrics {

    public static final String PURGED_METRIC = "openvsx_cdn_purged_keys_total";
    public static final String FAILED_METRIC = "openvsx_cdn_purge_failed_keys_total";

    private final Counter purged;
    private final Counter failed;

    public CdnPurgeMetrics(MeterRegistry registry) {
        this.purged = Counter.builder(PURGED_METRIC)
                .description("Surrogate keys the CDN was successfully told to drop")
                .register(registry);
        this.failed = Counter.builder(FAILED_METRIC)
                .description("Surrogate keys the CDN could not be told to drop, leaving it stale")
                .register(registry);
    }

    public void purged(int keys) {
        purged.increment(keys);
    }

    public void failed(int keys) {
        failed.increment(keys);
    }
}
