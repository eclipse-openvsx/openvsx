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
package org.eclipse.openvsx;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes Spring MVC async request handling run on the calling thread in MockMvc tests.
 * <p>
 * Endpoints that return a {@code StreamingResponseBody} hand the body off to the MVC async task
 * executor, which by default is a separate thread. {@code MockMvc.perform(..)} returns as soon as
 * async processing has started, so that thread writes to the {@code MockHttpServletResponse} while
 * the test thread — and Spring Boot's "print MVC result on failure" handler, which reads the
 * response headers at the end of every {@code perform(..)} — is still reading it. Neither
 * {@code MockHttpServletResponse} nor its header holders are thread-safe, so this occasionally
 * blows up with a {@link NullPointerException} deep inside the mock response instead of a test
 * failure. See <a href="https://github.com/eclipse-openvsx/openvsx/issues/1618">#1618</a>.
 * <p>
 * Running the async task inline removes the second thread altogether: the response is fully
 * written before {@code perform(..)} returns. The request is still marked as async started and the
 * async result is still recorded, so {@code request().asyncStarted()} and
 * {@code MvcResult#getAsyncResult()} keep working as before.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockMvcAsyncConfig {

    // Lowest precedence so this wins over Spring Boot's auto-configured async task executor.
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    WebMvcConfigurer inlineAsyncTaskExecutorConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(new TaskExecutorAdapter(Runnable::run));
            }
        };
    }
}
