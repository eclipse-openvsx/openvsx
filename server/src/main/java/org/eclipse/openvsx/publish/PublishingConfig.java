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

package org.eclipse.openvsx.publish;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties("ovsx.publishing")
public class PublishingConfig {
    private static final int MAX_CONTENT_SIZE = 512 * 1024 * 1024;

    private long maxContentSize = MAX_CONTENT_SIZE;

    private boolean requireLicense;

    /**
     * Allows to specify a list of unsupported icon formats identified by their extension.
     * <p>
     * See: <a href="https://github.com/eclipse-openvsx/openvsx/issues/2">Block SVG Images</a>
     */
    private List<String> unsupportedIconFormats = List.of("svg");

    public long getMaxContentSize() {
        return maxContentSize;
    }

    public void setMaxContentSize(int maxContentSize) {
        this.maxContentSize = maxContentSize;
    }

    public boolean isRequireLicense() {
        return requireLicense;
    }

    public void setRequireLicense(boolean requireLicense) {
        this.requireLicense = requireLicense;
    }

    public List<String> getUnsupportedIconFormats() {
        return unsupportedIconFormats;
    }

    public void setUnsupportedIconFormats(List<String> unsupportedIconFormats) {
        this.unsupportedIconFormats = unsupportedIconFormats;
    }
}
