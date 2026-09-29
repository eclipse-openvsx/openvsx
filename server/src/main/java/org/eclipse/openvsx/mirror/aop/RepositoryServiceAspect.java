/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.mirror.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.storage.StorageUtilService;

@Aspect
@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class RepositoryServiceAspect {

    private final StorageUtilService storageUtil;

    public RepositoryServiceAspect(StorageUtilService storageUtil) {
        this.storageUtil = storageUtil;
    }

    // FIXME: In PublishExtensionVersionHandler.mirror(TempFile extensionFile, String signatureName)
    //        it is mentioned that FileResources should not be stored but generated on the fly
    //        however they are persisted in the DB and this pointcut points to a non-existing method
    @Around("execution(* org.eclipse.openvsx.repositories.RepositoryService.findFileByTypeAndName(..))")
    public Object findFileByTypeAndName(ProceedingJoinPoint joinPoint) throws Throwable {
        var args = joinPoint.getArgs();
        var resource = new FileResource();
        resource.setExtension((ExtensionVersion) args[0]);
        resource.setName((String) args[2]);
        resource.setType((String) args[1]);
        resource.setStorageType(storageUtil.getActiveStorageType());
        return resource;
    }
}
