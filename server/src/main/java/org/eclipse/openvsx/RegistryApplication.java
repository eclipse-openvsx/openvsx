/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import org.eclipse.openvsx.mirror.ReadOnlyRequestFilter;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.web.ShallowEtagHeaderFilter;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication(
    exclude = {
        // currently no redis / elasticsearch repositories are being used
        // exclude autoconfiguration for them to avoid unnecessary logging
        // messages due to existing jpa repositories
        // can be removed once such repositories are in use
        DataElasticsearchRepositoriesAutoConfiguration.class,
        DataElasticsearchReactiveRepositoriesAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class,
    }
)
@EnableScheduling
// Class-based (CGLIB) proxying, not the default interface-based one: a @Retryable bean that also
// implements an interface (e.g. LocalRegistryService/IExtensionRegistry) would otherwise get a proxy
// assignable only to that interface, breaking every other bean wired to the concrete type.
@EnableResilientMethods(proxyTargetClass = true)
@EnableAsync
@EnableConfigurationProperties(OAuth2AttributesConfig.class)
// Need to enable serialization support for spring data's Page, see:
// https://stevenpg.com/posts/spring-data-page-impl-serialization-warning/
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class RegistryApplication {

    static void main(String[] args) {
        SpringApplication.run(RegistryApplication.class, args);
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public Jackson3JsonMapper jobRunrJsonMapper() {
        // allow certain JDK classes as they are used in SendMailJobRequest
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.");

        return new Jackson3JsonMapper(typeValidator);
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        var registrationBean = new FilterRegistrationBean<ShallowEtagHeaderFilter>();
        registrationBean.setFilter(new ShallowEtagHeaderFilter());
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);

        return registrationBean;
    }

    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler();
    }

    @Bean
    @ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
    public FilterRegistrationBean<ReadOnlyRequestFilter> readOnlyRequestFilter(
            @Value("${ovsx.data.mirror.read-only.allowed-endpoints}") String[] allowedEndpoints,
            @Value("${ovsx.data.mirror.read-only.disallowed-methods}") String[] disallowedMethods
    ) {
        var registrationBean = new FilterRegistrationBean<ReadOnlyRequestFilter>();
        registrationBean.setFilter(new ReadOnlyRequestFilter(allowedEndpoints, disallowedMethods));
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);

        return registrationBean;
    }
}
