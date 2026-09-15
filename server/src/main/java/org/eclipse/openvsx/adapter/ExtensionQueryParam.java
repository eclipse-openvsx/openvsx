/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.adapter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Parameters of the metadata query")
public record ExtensionQueryParam(
        @Schema(description = "List of query filters") List<Filter> filters,
        @Schema(description = "Flags to indicate what metadata to include in the query response") int flags
) {

    public static final int FLAG_INCLUDE_VERSIONS = 0x1;
    public static final int FLAG_INCLUDE_FILES = 0x2;
    public static final int FLAG_INCLUDE_CATEGORY_AND_TAGS = 0x4;
    public static final int FLAG_INCLUDE_SHARED_ACCOUNTS = 0x8;
    public static final int FLAG_INCLUDE_VERSION_PROPERTIES = 0x10;
    public static final int FLAG_EXCLUDE_NON_VALIDATED = 0x20;
    public static final int FLAG_INCLUDE_INSTALLATION_TARGETS = 0x40;
    public static final int FLAG_INCLUDE_ASSET_URI = 0x80;
    public static final int FLAG_INCLUDE_STATISTICS = 0x100;
    public static final int FLAG_INCLUDE_LATEST_VERSION_ONLY = 0x200;
    public static final int FLAG_UNPUBLISHED = 0x1000;

    @Schema(description = "Query filter")
    public record Filter(
            @Schema(description = "List of filter criteria") List<Criterion> criteria,
            @Schema(description = "Page number") int pageNumber,
            @Schema(description = "Maximal number of results per page") int pageSize,
            @Schema(
                defaultValue = "0",
                allowableValues = {
                    "0",
                    "4",
                    "5",
                    "6"
                },
                description = "Query result sort key<br/><br/>values:<br/>* 0 Relevance<br/>* 4 InstallCount<br/>* 5 PublishedDate<br/>* 6 AverageRating"
            ) int sortBy,
            @Schema(
                defaultValue = "0",
                allowableValues = { "0", "1" },
                description = "Query result sort order<br/><br/>values:<br/>* 0 Descending<br/>* 1 Ascending"
            ) int sortOrder
    ) {

        public String findCriterion(int type) {
            if (criteria == null || criteria.isEmpty()) {
                return null;
            }
            return criteria.stream()
                    .filter(c -> c.filterType == type)
                    .findFirst()
                    .map(c -> c.value)
                    .orElse(null);
        }

        public List<String> findCriteria(int type) {
            if (criteria == null || criteria.isEmpty()) {
                return Collections.emptyList();
            }
            return criteria.stream()
                    .filter(c -> c.filterType == type && c.value != null)
                    .map(c -> c.value)
                    .collect(Collectors.toList());
        }
    }

    @Schema(description = "Filter criteria")
    public record Criterion(
            @Schema(
                allowableValues = {
                    "1",
                    "4",
                    "5",
                    "7",
                    "8",
                    "9",
                    "10",
                    "12"
                },
                description = "Filter type<br/><br/>values:<br/>* 1 TAG<br/>* 4 EXTENSION_ID<br/>* 5 CATEGORY<br/>* 7 EXTENSION_NAME<br/>* 8 TARGET<br/>* 9 FEATURED<br/>* 10 SEARCH_TEXT<br/>* 12 EXCLUDE_WITH_FLAGS"
            ) int filterType,
            @Schema(description = "Filter value") String value
    ) {
        public static final int FILTER_TAG = 1;
        public static final int FILTER_EXTENSION_ID = 4;
        public static final int FILTER_CATEGORY = 5;
        public static final int FILTER_EXTENSION_NAME = 7;
        public static final int FILTER_TARGET = 8;
        public static final int FILTER_FEATURED = 9;
        public static final int FILTER_SEARCH_TEXT = 10;
        public static final int FILTER_EXCLUDE_WITH_FLAGS = 12;
    }
}
