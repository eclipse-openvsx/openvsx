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
package org.eclipse.openvsx.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtensionQueryConfig {
    @Bean
    @ConditionalOnMissingBean(IExtensionQueryRequestHandler.class)
    public IExtensionQueryRequestHandler defaultExtensionQueryRequestHandler(
            LocalVSCodeService local,
            UpstreamVSCodeService upstream
    ) {
        return new DefaultExtensionQueryRequestHandler(local, upstream);
    }
}
