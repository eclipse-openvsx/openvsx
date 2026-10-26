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
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.AdminStatistics;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AdminStatisticCalculationsRepositoryTest extends AbstractPostgresContainerTest {

    // A month far in the past, so versions other tests leave in the shared database don't fall into it.
    private static final LocalDateTime MONTH = LocalDateTime.of(1999, 3, 1, 0, 0);

    @Autowired
    AdminStatisticCalculationsRepository repo;

    @Autowired
    EntityManager em;

    @Test
    void countsDistinctVersionsPublishedInTheMonthByPublishMethod() {
        var publisher = new UserData();
        publisher.setLoginName("admin-statistics-test-publisher");
        em.persist(publisher);
        var namespace = new Namespace();
        namespace.setName("admin-statistics-test");
        em.persist(namespace);
        var first = persistExtension(namespace, "first");
        var second = persistExtension(namespace, "second");

        // One release built for two platforms counts once.
        persistVersion(first, "1.0.0", TargetPlatform.NAME_LINUX_X64, PersonalAccessTokenType.TPT, MONTH, publisher);
        persistVersion(
                first,
                "1.0.0",
                TargetPlatform.NAME_WIN32_X64,
                PersonalAccessTokenType.TPT,
                MONTH.plusDays(1),
                publisher);
        persistVersion(
                first,
                "1.1.0",
                TargetPlatform.NAME_UNIVERSAL,
                PersonalAccessTokenType.LLP,
                MONTH.plusDays(30),
                publisher);
        persistVersion(second, "1.0.0", TargetPlatform.NAME_UNIVERSAL, null, MONTH.plusDays(2), publisher);
        // Outside the month on either side.
        persistVersion(
                second,
                "0.9.0",
                TargetPlatform.NAME_UNIVERSAL,
                PersonalAccessTokenType.LLT,
                MONTH.minusSeconds(1),
                publisher);
        persistVersion(
                second,
                "2.0.0",
                TargetPlatform.NAME_UNIVERSAL,
                PersonalAccessTokenType.LLT,
                MONTH.plusMonths(1),
                publisher);
        em.flush();

        var counts = repo.countExtensionVersionsPublishedGroupedByPublishMethod(MONTH, MONTH.plusMonths(1));

        assertThat(counts)
                .isEqualTo(Map.of("TPT", 1, "LLP", 1, AdminStatistics.PUBLISH_METHOD_NONE, 1));
    }

    private Extension persistExtension(Namespace namespace, String name) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setActive(true);
        extension.setDownloadable(true);
        extension.setPublishedDate(MONTH);
        extension.setLastUpdatedDate(MONTH);
        em.persist(extension);
        return extension;
    }

    private void persistVersion(
            Extension extension,
            String version,
            String targetPlatform,
            PersonalAccessTokenType tokenType,
            LocalDateTime timestamp,
            UserData publisher
    ) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setActive(true);
        extVersion.setPublishedBy(publisher);
        extVersion.setPublishedWithTt(tokenType);
        extVersion.setTimestamp(timestamp);
        em.persist(extVersion);
    }
}
