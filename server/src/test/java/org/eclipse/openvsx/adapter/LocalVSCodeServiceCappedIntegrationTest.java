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

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.Criterion.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * End-to-end, real-database counterpart to {@code
 * LocalVSCodeServiceTest#testExtensionQuery_versionsFlagCapped_resolvesLatestDirectlyEvenWhenCappedFetchIsEmpty}.
 * That mock test assumes the capped active-version fetch can come back without this extension's only
 * version for the requested platform; this class proves it against the real {@code
 * findAllActiveByExtensionIdAndTargetPlatform} dense_rank() query, which ranks pre-releases across ALL
 * target platforms combined per extension. Isolated in its own context because it needs {@code
 * ovsx.extension-query.max-pre-release-versions} capped, unlike every other extensionQuery scenario.
 */
@SpringBootTest
@TestPropertySource(properties = "ovsx.extension-query.max-pre-release-versions=2")
@Transactional
class LocalVSCodeServiceCappedIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    LocalVSCodeService service;

    @Autowired
    EntityManager em;

    private UserData owner;

    private UserData owner() {
        if (owner == null) {
            owner = new UserData();
            owner.setLoginName("local-vscode-service-capped-it-owner");
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

    private void persistVersion(
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

    @Test
    void cappedPreReleaseRankingAcrossPlatformsDoesNotHideThisPlatformsOnlyVersion() {
        var extension = persistExtension("it-ns3", "cap-test");
        // Five pre-releases on win32-x64, ranked #1-#5 across the whole extension (combined across
        // platforms) by the dense_rank() the capping query uses - these push the linux-x64 version
        // below the top-2 cap even though it is the ONLY version linux-x64 has.
        var base = LocalDateTime.now().minusDays(1);
        for (var i = 0; i < 5; i++) {
            persistVersion(extension, "0.9." + i, TargetPlatform.NAME_WIN32_X64, true, base.plusMinutes(i));
        }
        persistVersion(extension, "0.1.0", TargetPlatform.NAME_LINUX_X64, true, base.minusDays(1));
        em.flush();

        var param = paramFor(
                "it-ns3.cap-test",
                TargetPlatform.NAME_LINUX_X64,
                FLAG_INCLUDE_LATEST_VERSION_ONLY);

        var result = assertDoesNotThrow(() -> service.extensionQuery(param, 100));
        assertThat(result.results().getFirst().extensions())
                .as(
                        "linux-x64's only version must still be found as 'latest' even though the "
                                + "cross-platform pre-release cap would exclude it from the capped active-version list")
                .singleElement()
                .satisfies(
                        ext -> assertThat(ext.versions()).singleElement()
                                .extracting(ExtensionQueryResult.ExtensionVersion::version)
                                .isEqualTo("0.1.0"));
    }
}
