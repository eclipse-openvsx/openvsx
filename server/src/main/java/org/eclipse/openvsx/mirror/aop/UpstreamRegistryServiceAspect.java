/******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
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

import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.Bucket;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class UpstreamRegistryServiceAspect {

    private final @Nullable BlockingBucket bucket;

    public UpstreamRegistryServiceAspect(
            @Value("${ovsx.data.mirror.requests-per-second:-1}") double requestsPerSecond
    ) {
        if (requestsPerSecond == -1) {
            this.bucket = null;
            return;
        }
        if (!(requestsPerSecond > 0)) {
            throw new IllegalArgumentException(
                    "ovsx.data.mirror.requests-per-second must be positive or -1, got " + requestsPerSecond);
        }

        var interval = Duration.ofNanos(Math.max(1, Math.round(1_000_000_000d / requestsPerSecond)));
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(1).refillGreedy(1, interval))
                .build()
                .asBlocking();
    }

    @Around("execution(* org.eclipse.openvsx.UpstreamRegistryService.*(..))")
    public Object rateLimitMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        if (bucket != null) {
            bucket.consumeUninterruptibly(1);
        }
        return joinPoint.proceed();
    }
}
