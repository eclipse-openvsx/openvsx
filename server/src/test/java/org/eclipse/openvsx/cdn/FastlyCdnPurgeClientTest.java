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
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FastlyCdnPurgeClientTest {

    private static final String PURGE_URL = "https://api.fastly.test/service/svc-1/purge";

    private MockRestServiceServer server;

    private FastlyCdnPurgeClient client(boolean softPurge) {
        var config = new CdnPurgeConfig();
        ReflectionTestUtils.setField(config, "provider", "fastly");
        ReflectionTestUtils.setField(config, "fastlyServiceId", "svc-1");
        ReflectionTestUtils.setField(config, "fastlyApiToken", "token-1");
        ReflectionTestUtils.setField(config, "fastlySoftPurge", softPurge);
        ReflectionTestUtils.setField(config, "fastlyApiUrl", "https://api.fastly.test");

        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new FastlyCdnPurgeClient(config, builder);
    }

    @Test
    void asksFastlyToDropTheKeys() {
        var client = client(true);
        server.expect(requestTo(PURGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Fastly-Key", "token-1"))
                .andExpect(header("Fastly-Soft-Purge", "1"))
                .andExpect(content().json("{\"surrogate_keys\":[\"ext/foo/bar\",\"ns/foo\"]}"))
                .andRespond(withSuccess());

        client.purge(List.of("ext/foo/bar", "ns/foo"));

        server.verify();
    }

    @Test
    void dropsTheKeysOutrightWhereSoftPurgingIsTurnedOff() {
        var client = client(false);
        server.expect(requestTo(PURGE_URL))
                .andExpect(headerDoesNotExist("Fastly-Soft-Purge"))
                .andRespond(withSuccess());

        client.purge(List.of("ext/foo/bar"));

        server.verify();
    }

    // Fastly takes at most 256 keys in one bulk purge, and a namespace-wide change can name more.
    @Test
    void splitsMoreKeysThanFastlyTakesAtOnce() {
        var client = client(true);
        var keys = IntStream.range(0, FastlyCdnPurgeClient.MAX_KEYS_PER_REQUEST + 1)
                .mapToObj(i -> "ext/foo/bar-" + i)
                .toList();
        server.expect(requestTo(PURGE_URL)).andRespond(withSuccess());
        server.expect(requestTo(PURGE_URL)).andRespond(withSuccess());

        client.purge(keys);

        server.verify();
    }

    // Thrown, not swallowed: the job retries, and a lost purge serves stale responses until they expire.
    @Test
    void failsWhenFastlyDoesNotAccept() {
        var client = client(true);
        server.expect(requestTo(PURGE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.purge(List.of("ext/foo/bar"))).isInstanceOf(Exception.class);
    }
}
