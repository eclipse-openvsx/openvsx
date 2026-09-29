/******************************************************************************
 * Copyright (c) 2024 Precies. Software Ltd and others
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
package org.eclipse.openvsx.metrics;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.aop.ObservedAspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class RegistryObservationConvention implements ObservationConvention<ObservedAspect.ObservedAspectContext> {

    private final JsonMapper mapper;

    public RegistryObservationConvention() {
        this.mapper = JsonMapper.shared();
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ObservedAspect.@NonNull ObservedAspectContext context) {
        //        var joinPoint = context.getProceedingJoinPoint();
        //        var args = joinPoint.getArgs();
        //        var methodSignature = (MethodSignature) joinPoint.getSignature();
        //        var parameterNames = methodSignature.getParameterNames();
        //        var argKeyValues = new KeyValue[args.length];
        //        for(var i = 0; i < args.length; i++) {
        //            var key = "args." + parameterNames[i];
        //            var value = convertObjectToString(args[i]);
        //            argKeyValues[i] = KeyValue.of(key, value);
        //        }

        return ObservationConvention.super.getHighCardinalityKeyValues(context);//.and(argKeyValues);
    }

    private String convertObjectToString(Object arg) {
        if (arg instanceof String) {
            return (String) arg;
        } else if (arg instanceof Number || arg instanceof Boolean) {
            return String.valueOf(arg);
        } else {
            try {
                return mapper.writeValueAsString(arg);
            } catch (JacksonException e) {
                return "";
            }
        }
    }

    @Override
    public boolean supportsContext(Observation.@NonNull Context context) {
        return context instanceof ObservedAspect.ObservedAspectContext;
    }

    @Override
    public String getName() {
        return "org.eclipse.openvsx.observed";
    }

    @Override
    public String getContextualName(ObservedAspect.ObservedAspectContext context) {
        var methodSignature = (MethodSignature) context.getProceedingJoinPoint().getSignature();
        var method = methodSignature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
