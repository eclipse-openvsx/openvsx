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
package org.eclipse.openvsx.entities;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminStatisticsTest {

    @Test
    void exportsVersionsByPublishMethodToCsvAndJson() {
        var statistics = new AdminStatistics();
        statistics.setExtensionsByRating(Map.of());
        statistics.setPublishersByExtensionsPublished(Map.of());
        statistics.setTopMostActivePublishingUsers(Map.of());
        statistics.setTopNamespaceExtensions(Map.of());
        statistics.setTopNamespaceExtensionVersions(Map.of());
        statistics.setTopMostDownloadedExtensions(Map.of());
        statistics.setExtensionVersionsByPublishMethod(Map.of("TPT", 3, "NONE", 5));

        var csv = statistics.toCsv().split("\n");
        // Every method gets a column, so CSVs of different months line up.
        assertThat(csv[0]).endsWith(
                ",extension_versions_published_with_llt,extension_versions_published_with_llp"
                        + ",extension_versions_published_with_ott,extension_versions_published_with_tpt"
                        + ",extension_versions_published_with_none");
        assertThat(csv[1]).endsWith(",0,0,0,3,5");

        assertThat(statistics.toJson().getExtensionVersionsByPublishMethod())
                .extracting(e -> e.getPublishMethod() + "=" + e.getExtensionVersions())
                .containsExactly("NONE=5", "TPT=3");
    }
}
