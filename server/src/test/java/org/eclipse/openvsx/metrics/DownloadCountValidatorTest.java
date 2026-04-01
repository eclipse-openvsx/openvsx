/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.metrics;

import org.eclipse.openvsx.metrics.config.DownloadCountValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadCountValidatorTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private DownloadCountValidationProperties properties;
    private DownloadCountValidator validator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void beforeEach() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new DownloadCountValidationProperties();
        properties.setEnabled(true);
        properties.setHourlyLimitPerIp(3);
        properties.setKeyPrefix("download:dedup");
        properties.setLateArrivalHours(2);
        properties.setAutomatedClientKeywords(List.of("curl", "bot"));

        validator = new DownloadCountValidator(redisTemplate, "getRemoteAddr()", properties);
    }

    @Test
    public void shouldSkipValidationWhenDisabled() {
        properties.setEnabled(false);

        assertTrue(validator.shouldCountDownload(42L, "1.1.1.1", "curl/8.0", Instant.now()));
        Mockito.verifyNoInteractions(valueOps);
    }

    @Test
    public void shouldRejectAutomatedUserAgents() {
        assertFalse(validator.shouldCountDownload(42L, "1.1.1.1", "curl/8.0", Instant.now()));
        Mockito.verifyNoInteractions(valueOps);
    }

    @Test
    public void shouldCountWhenUnderHourlyLimit() {
        properties.setHourlyLimitPerIp(5);
        properties.setLateArrivalHours(2);
        properties.setKeyPrefix("custom:dedup");

        // Simulate first download in this hour (count = 1, under limit of 5)
        Mockito.when(valueOps.increment(Mockito.anyString())).thenReturn(1L);

        Instant eventTime = Instant.parse("2026-01-01T01:10:00Z");
        boolean result = validator.shouldCountDownload(99L, "10.10.10.10", "Mozilla/5.0", eventTime);

        assertTrue(result);

        // Key must include the custom prefix and extension ID
        Duration expectedTtl = Duration.ofHours(1).plusHours(2);
        Mockito.verify(valueOps).setIfAbsent(
                Mockito.argThat(key -> key.startsWith("custom:dedup:") && key.contains(":99:")),
                Mockito.eq("0"),
                Mockito.eq(expectedTtl)
        );
    }

    @Test
    public void shouldNotCountWhenHourlyLimitExceeded() {
        properties.setHourlyLimitPerIp(3);

        // Simulate 4th download — over the limit of 3
        Mockito.when(valueOps.increment(Mockito.anyString())).thenReturn(4L);

        boolean result = validator.shouldCountDownload(42L, "1.1.1.1", "Mozilla/5.0", Instant.now());

        assertFalse(result);
    }

    @Test
    public void shouldNotCountWhenClientIpUnavailable() {
        assertFalse(validator.shouldCountDownload(42L, null, "Mozilla/5.0", Instant.now()));
        Mockito.verifyNoInteractions(valueOps);
    }

    @Test
    public void shouldUseEventTimeBucketInKey() {
        properties.setLateArrivalHours(24);

        // Simulate first download in this hour (count = 1, under limit)
        Mockito.when(valueOps.increment(Mockito.anyString())).thenReturn(1L);

        // 2026-01-01T00:00:00Z → epochSecond = 1735689600 → hourBucket = 482136
        Instant eventTime = Instant.parse("2026-01-01T00:00:00Z");
        boolean result = validator.shouldCountDownload(99L, "10.0.0.1", "Mozilla/5.0", eventTime);

        assertTrue(result);

        // TTL = 1 hour + 24 late-arrival hours = 25 hours
        Duration expectedTtl = Duration.ofHours(1).plusHours(24);
        Mockito.verify(valueOps).setIfAbsent(
                Mockito.argThat(key -> key.startsWith("download:dedup:") && key.contains(":99:")),
                Mockito.eq("0"),
                Mockito.eq(expectedTtl)
        );
    }

    @Test
    public void shouldNotCountWhenEventTimeIsMissing() {
        boolean result = validator.shouldCountDownload(99L, "10.0.0.1", "Mozilla/5.0", null);
        assertFalse(result);
        Mockito.verifyNoInteractions(valueOps);
    }
}
