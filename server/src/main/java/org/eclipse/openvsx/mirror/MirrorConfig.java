/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.mirror;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MirrorConfig {

    @Value("${ovsx.data.mirror.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Bean
    @ConfigurationProperties(prefix = "ovsx.data.mirror.exclude-extensions")
    public Set<String> excludeExtensions() {
        return new HashSet<>();
    }

    @Bean
    @ConfigurationProperties(prefix = "ovsx.data.mirror.include-extensions")
    public Set<String> includeExtensions() {
        return new HashSet<>();
    }
}
