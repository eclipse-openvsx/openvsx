/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.mirror.aop;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpstreamRegistryServiceAspectTest {

    @Test
    void spacesCallsToTheConfiguredRate() throws Throwable {
        var aspect = new UpstreamRegistryServiceAspect(20);
        var joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("result");

        var start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            assertThat(aspect.rateLimitMethodCall(joinPoint)).isEqualTo("result");
        }
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        // the first call is immediate, the other four wait 50 ms each
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(190));
        verify(joinPoint, times(5)).proceed();
    }

    @Test
    void supportsFractionalRates() throws Throwable {
        var aspect = new UpstreamRegistryServiceAspect(2.5);
        var joinPoint = mock(ProceedingJoinPoint.class);

        var start = System.nanoTime();
        aspect.rateLimitMethodCall(joinPoint);
        aspect.rateLimitMethodCall(joinPoint);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        // 2.5 rps is one call every 400 ms
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(390));
    }

    @Test
    void doesNotLimitByDefault() throws Throwable {
        var aspect = new UpstreamRegistryServiceAspect(-1);
        var joinPoint = mock(ProceedingJoinPoint.class);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            aspect.rateLimitMethodCall(joinPoint);
        }
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
        verify(joinPoint, times(1000)).proceed();
    }

    @Test
    void rejectsNonPositiveRates() {
        assertThatThrownBy(() -> new UpstreamRegistryServiceAspect(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpstreamRegistryServiceAspect(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
