/*******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.mirror.aop;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class StorageUtilServiceAspect {

    private List<String> readOnlyMethods;

    public StorageUtilServiceAspect() {
        readOnlyMethods = List.of("uploadFile", "removeFile", "increaseDownloadCount");
    }

    @Around("execution(* org.eclipse.openvsx.storage.StorageUtilService.*(..))")
    public Object readOnlyMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        var methodName = joinPoint.getSignature().getName();
        return !readOnlyMethods.contains(methodName)
                ? joinPoint.proceed()
                : null;
    }
}
