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
package org.eclipse.openvsx.adapter;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Limits on a {@code /vscode/gallery/extensionquery} request; one over them is answered with 400. */
@Configuration
public class ExtensionQueryLimitsConfig {

    /**
     * The maximum number of criteria in a query's filter. VS Code sends one per installed extension, all
     * in one query, so this must stay above the largest set of extensions a user has installed.
     * <p>
     * Property: {@code ovsx.extension-query.max-criteria}
     * Default: {@code 1000}
     */
    @Value("${ovsx.extension-query.max-criteria:1000}")
    private int maxCriteria;

    /**
     * The maximum page size of a search, that is a query without extension id or name criteria.
     * <p>
     * Property: {@code ovsx.extension-query.max-search-page-size}
     * Default: {@code 100}
     */
    @Value("${ovsx.extension-query.max-search-page-size:100}")
    private int maxSearchPageSize;

    @PostConstruct
    public void validate() {
        if (maxCriteria <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.extension-query.max-criteria must be a positive number, got: " + maxCriteria);
        }
        if (maxSearchPageSize <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.extension-query.max-search-page-size must be a positive number, got: " + maxSearchPageSize);
        }
    }

    public int getMaxCriteria() {
        return maxCriteria;
    }

    public int getMaxSearchPageSize() {
        return maxSearchPageSize;
    }
}
