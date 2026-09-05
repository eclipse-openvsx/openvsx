/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.migration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code ovsx.migrations.delay.seconds} and {@code ovsx.registry.version}, previously declared
 * independently in every startup job that delays itself behind the registry's own start-up
 * (migrations, VS Code id daily update, extension control update).
 */
@Component
public class MigrationsProperties {

    @Value("${ovsx.migrations.delay.seconds:0}")
    private long delaySeconds;

    @Value("${ovsx.registry.version:}")
    private String registryVersion;

    public long getDelaySeconds() {
        return delaySeconds;
    }

    public String getRegistryVersion() {
        return registryVersion;
    }
}
