/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.metrics;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class MetricsConfiguration {
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry, new RegistryObservationConvention());
    }

    @Bean
    public ObservationFilter observationFilter(
            @Value("${management.metrics.tags.application:app}") String service,
            @Value("${management.metrics.tags.environment:development}") String environment,
            @Value("${management.metrics.tags.instance:local}") String instance
    ) {
        return context -> context
                .addLowCardinalityKeyValue(KeyValue.of("service.name", service))
                .addLowCardinalityKeyValue(KeyValue.of("deployment.environment", environment))
                .addLowCardinalityKeyValue(KeyValue.of("service.instance.id", instance));
    }
}
