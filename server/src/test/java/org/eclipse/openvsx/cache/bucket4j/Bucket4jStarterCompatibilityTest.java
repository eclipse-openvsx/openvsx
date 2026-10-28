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
package org.eclipse.openvsx.cache.bucket4j;

import com.giffing.bucket4j.spring.boot.starter.config.filter.servlet.Bucket4JAutoConfigurationServletFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class Bucket4jStarterCompatibilityTest {

    // Spring CGLIB-enhances this auto-configuration when bucket4j.enabled=true and resolves its
    // method signatures; a starter built against another Spring Boot major fails there with NoClassDefFoundError.
    @Test
    void servletFilterAutoConfigurationResolvesAgainstSpringBoot() {
        assertThatCode(Bucket4JAutoConfigurationServletFilter.class::getDeclaredMethods).doesNotThrowAnyException();
    }
}
