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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.Criterion.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * End-to-end {@link LocalVSCodeService#extensionQuery} coverage against a real database, rather than
 * the mocked {@link RepositoryService} in {@link LocalVSCodeServiceTest}. {@code
 * LocalVSCodeServiceTest} pins down the branching (which repository method gets called for which
 * flags) assuming those queries behave correctly; this class checks that assumption itself, exercising
 * the real jOOQ-generated SQL. Uses the default (unbounded) {@code
 * ovsx.extension-query.max-pre-release-versions}; see {@link LocalVSCodeServiceCappedIntegrationTest}
 * for the capped case, which needs its own Spring context.
 */
@SpringBootTest
@Transactional
class LocalVSCodeServiceIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    LocalVSCodeService service;

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    private UserData owner;

    private UserData owner() {
        if (owner == null) {
            owner = new UserData();
            owner.setLoginName("local-vscode-service-it-owner");
            em.persist(owner);
            em.flush();
        }
        return owner;
    }

    private Extension persistExtension(String namespaceName, String extensionName) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        em.persist(namespace);

        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(namespace);
        extension.setActive(true);
        extension.setDownloadable(true);
        extension.setPublishedDate(LocalDateTime.now());
        extension.setLastUpdatedDate(LocalDateTime.now());
        em.persist(extension);
        em.flush();
        return extension;
    }

    private ExtensionVersion persistVersion(
            Extension extension,
            String version,
            String targetPlatform,
            boolean preRelease,
            LocalDateTime timestamp
    ) {
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setActive(true);
        extVersion.setPreRelease(preRelease);
        extVersion.setTimestamp(timestamp);
        extVersion.setDisplayName("Display " + extension.getName() + " " + version + "@" + targetPlatform);
        extVersion.setPublishedBy(owner());
        em.persist(extVersion);
        return extVersion;
    }

    private ExtensionQueryParam paramFor(String extensionId, String targetPlatform, int flags) {
        var criteria = new java.util.ArrayList<Criterion>();
        criteria.add(new Criterion(FILTER_EXTENSION_NAME, extensionId));
        if (targetPlatform != null) {
            criteria.add(new Criterion(FILTER_TARGET, targetPlatform));
        }
        var filter = new ExtensionQueryParam.Filter(criteria, 1, 100, 0, 0);
        return new ExtensionQueryParam(List.of(filter), flags);
    }

    // Regression test for the `if (latest == null) continue;` guard, against real Postgres: an
    // extension that matches the id/name filter but has no active version for the requested target
    // platform used to NPE (`latestVersions.get(...)` returns null, `toQueryExtension` dereferences
    // it unconditionally). It must now be silently omitted from the result instead.
    @Test
    void extensionWithNoVersionForRequestedPlatformIsSkippedNotCrashed() {
        var extension = persistExtension("it-ns1", "only-win32");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_WIN32_X64, false, LocalDateTime.now());
        em.flush();

        var param = paramFor("it-ns1.only-win32", TargetPlatform.NAME_LINUX_X64, 0);

        var result = assertDoesNotThrow(() -> service.extensionQuery(param, 100));
        assertThat(result.results().getFirst().extensions())
                .as("extension with no version for the requested platform must be omitted, not crash")
                .isEmpty();
    }

    // Demonstrates the row-fetch reduction the PR claims: with only FLAG_INCLUDE_LATEST_VERSION_ONLY
    // and a concrete target platform, findActiveExtensionVersions would pull every active version row
    // just to derive "latest"; findLatestVersions pulls exactly one row per extension via a LATERAL
    // LIMIT 1 join instead. Both must agree on which version is "latest".
    @Test
    void latestVersionOnlyWithTargetPlatformFetchesOneRowInsteadOfTheWholeHistory() {
        var extension = persistExtension("it-ns2", "big-prerelease");
        persistVersion(extension, "1.0.0", TargetPlatform.NAME_UNIVERSAL, false, LocalDateTime.now().minusDays(1));
        var base = LocalDateTime.now().minusDays(2);
        ExtensionVersion expectedLatest = null;
        for (var i = 1; i <= 20; i++) {
            expectedLatest = persistVersion(
                    extension,
                    "0.1." + i,
                    TargetPlatform.NAME_LINUX_X64,
                    true,
                    base.plusMinutes(i));
        }
        em.flush();

        var ids = Set.of(extension.getId());
        var fullFetch = repositories.findActiveExtensionVersions(ids, TargetPlatform.NAME_LINUX_X64, -1);
        var directLatest = repositories.findLatestVersions(ids, TargetPlatform.NAME_LINUX_X64);
        assertThat(fullFetch).as("sanity: the full active-version fetch really does return every pre-release row")
                .hasSize(20);
        assertThat(directLatest).as("the direct bulk query returns exactly one row per extension")
                .hasSize(1);
        assertThat(directLatest.getFirst().getVersion()).isEqualTo(expectedLatest.getVersion());

        var param = paramFor(
                "it-ns2.big-prerelease",
                TargetPlatform.NAME_LINUX_X64,
                FLAG_INCLUDE_LATEST_VERSION_ONLY);
        var result = service.extensionQuery(param, 100);
        var queried = result.results().getFirst().extensions().getFirst();
        assertThat(queried.versions()).singleElement().extracting(ExtensionQueryResult.ExtensionVersion::version)
                .isEqualTo(expectedLatest.getVersion());
    }
}
