/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.settings;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.RegistryAPI;
import org.eclipse.openvsx.UserAPI;
import org.eclipse.openvsx.json.ResultJson;

/**
 * A hand-written {@link StaticMethodMatcherPointcutAdvisor} rather than an AspectJ {@code @Aspect}: an
 * AspectJ {@code execution()} pointcut has to spin up its own PointcutParser/World and scan the classpath
 * to resolve types on every candidate bean, on every Spring context boot - this was the single largest
 * cost in the test suite's context startup time (roughly 70% of it), since this aspect (unlike the other
 * four in {@code mirror.aop}) has no {@code @ConditionalOnProperty} and so is always active. A plain
 * Method/Class check has none of that cost.
 */
@Component
public class ReadOnlyEndpointAspect extends StaticMethodMatcherPointcutAdvisor {

    public ReadOnlyEndpointAspect(SettingsService settings) {
        super((MethodInterceptor) invocation -> settings.isReadOnly()
                ? ResponseEntity.status(409).body(ResultJson.error("Registry is in read-only mode."))
                : invocation.proceed());
        setClassFilter(
                type -> type == RegistryAPI.class || type == UserAPI.class
                        || (type.getPackageName().equals("org.eclipse.openvsx.admin")
                                && type.getSimpleName().endsWith("API")));
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getReturnType() == ResponseEntity.class && method.isAnnotationPresent(MutatingOperation.class);
    }
}
