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

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CdnPurgeJobRequestHandlerTest {

    private final CdnPurgeClient client = Mockito.mock(CdnPurgeClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CdnPurgeMetrics metrics = new CdnPurgeMetrics(registry);

    private double counter(String name) {
        return registry.get(name).counter().count();
    }

    @Test
    void purgesTheKeysItWasGiven() throws Exception {
        new CdnPurgeJobRequestHandler(client, metrics).run(new CdnPurgeJobRequest(List.of("ext/foo/bar", "ns/foo")));

        verify(client).purge(List.of("ext/foo/bar", "ns/foo"));
        assertThat(counter(CdnPurgeMetrics.PURGED_METRIC)).isEqualTo(2);
        assertThat(counter(CdnPurgeMetrics.FAILED_METRIC)).isZero();
    }

    // Rethrown so JobRunr retries it, and counted because a purge that never lands is invisible from
    // the outside: the registry answers correctly while the CDN keeps serving what it had.
    @Test
    void countsAndRethrowsWhatTheCdnRefused() {
        doThrow(new IllegalStateException("nope")).when(client).purge(Mockito.anyCollection());
        var handler = new CdnPurgeJobRequestHandler(client, metrics);

        assertThatThrownBy(() -> handler.run(new CdnPurgeJobRequest(List.of("ext/foo/bar"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter(CdnPurgeMetrics.FAILED_METRIC)).isEqualTo(1);
        assertThat(counter(CdnPurgeMetrics.PURGED_METRIC)).isZero();
    }

    // The provider can be unconfigured between a job being enqueued and it running.
    @Test
    void doesNothingWithoutAClient() throws Exception {
        new CdnPurgeJobRequestHandler(null, metrics).run(new CdnPurgeJobRequest(List.of("ext/foo/bar")));

        verify(client, never()).purge(Mockito.anyCollection());
        assertThat(counter(CdnPurgeMetrics.PURGED_METRIC)).isZero();
    }
}
