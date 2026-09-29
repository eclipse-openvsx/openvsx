/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for {@link NamespaceOwnershipCheckScanner}, extracted, to simplify testing.
 */
@Configuration
public class NamespaceOwnershipCheckConfig {
    /**
     * Is the namespace ownership check for extensions existing in the referenced gallery enabled or not.
     * <p>
     * Property: {@code ovsx.scanning.namespace-ownership-check.enabled}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.namespace-ownership-check.enabled:false}")
    private boolean enabled;

    /**
     * Is the namespace ownership check required or not.
     * <p>
     * Property: {@code ovsx.scanning.namespace-ownership-check.required}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.namespace-ownership-check.required:true}")
    private boolean required;

    /**
     * Is the namespace ownership check enforced or not.
     * <p>
     * Property: {@code ovsx.scanning.namespace-ownership-check.enforced}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.namespace-ownership-check.enforced:true}")
    private boolean enforced;

    /**
     * Is the namespace ownership check needed to run on already existing and active extensions or not.
     * <p>
     * Property: {@code ovsx.scanning.namespace-ownership-check.check-active-extensions}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.namespace-ownership-check.check-active-extensions:false}")
    private boolean checkActiveExtensions;

    /**
     * The referenced gallery API URL to perform the namespace ownership checks against.
     * <p>
     * Property: {@code ovsx.scanning.namespace-ownership-check.gallery-url}
     * Default: {@code ""}
     */
    @Value("${ovsx.scanning.namespace-ownership-check.gallery-url:}")
    private String galleryUrl;

    /**
     * Default constructor.
     */
    public NamespaceOwnershipCheckConfig() {
    }

    /**
     * For testing.
     */
    public NamespaceOwnershipCheckConfig(
            boolean enabled,
            boolean required,
            boolean enforced,
            boolean checkActiveExtensions,
            String galleryUrl
    ) {
        this.enabled = enabled;
        this.required = required;
        this.enforced = enforced;
        this.checkActiveExtensions = checkActiveExtensions;
        this.galleryUrl = galleryUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public boolean isCheckActiveExtensions() {
        return checkActiveExtensions;
    }

    public String getGalleryUrl() {
        return galleryUrl;
    }

    @PostConstruct
    public void validate() {
        if (enabled) {
            if (galleryUrl == null || galleryUrl.isEmpty()) {
                throw new IllegalStateException("ovsx.scanning.namespace-ownership-check.gallery-url must be set");
            }
        }
    }
}
