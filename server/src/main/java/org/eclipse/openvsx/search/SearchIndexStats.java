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
package org.eclipse.openvsx.search;

import org.jspecify.annotations.Nullable;

/**
 * What the registry can say about its search index without querying it as a user would.
 *
 * @param enabled          whether searching is available at all
 * @param implementation   which engine is answering searches: {@code elasticsearch}, {@code database},
 *                         or {@code none} when searching is switched off entirely
 * @param indexExists      whether the index has been created; only meaningful for elasticsearch
 * @param indexedDocuments how many extensions the index holds, or {@code null} when there is no index to
 *                         count - the database engine searches the tables directly
 * @param activeExtensions how many extensions the index is built from, so a mismatch with
 *                         {@code indexedDocuments} is visible without having to search for something and
 *                         notice it missing
 * @param maxResultWindow  the deepest result offset the index will serve
 */
public record SearchIndexStats(
        boolean enabled,
        String implementation,
        boolean indexExists,
        @Nullable Long indexedDocuments,
        long activeExtensions,
        @Nullable Long maxResultWindow
) {
    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String DATABASE = "database";
    public static final String NONE = "none";
}
