/********************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
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
package org.eclipse.openvsx.mirror.aop;

import com.google.common.util.concurrent.RateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class UpstreamRegistryServiceAspect {

    private RateLimiter rateLimiter;

    public UpstreamRegistryServiceAspect(
            @Value("${ovsx.data.mirror.requests-per-second:-1}") double requestsPerSecond
    ) {
        if (requestsPerSecond == -1) {
            requestsPerSecond = Double.MAX_VALUE;
        }

        this.rateLimiter = RateLimiter.create(requestsPerSecond);
    }

    @Around("execution(* org.eclipse.openvsx.UpstreamRegistryService.*(..))")
    public Object rateLimitMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        rateLimiter.acquire();
        return joinPoint.proceed();
    }
}
