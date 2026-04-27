/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.util;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
class VersionServiceTest {

    @MockitoBean
    RepositoryService repositories;

    @Autowired
    VersionService versions;

    @Test
    void testGetLatestVersion() {
        var release = new ExtensionVersion();
        release.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        release.setVersion("1.0.0");

        var minor = new ExtensionVersion();
        minor.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        minor.setVersion("0.0.5");

        var major = new ExtensionVersion();
        major.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        major.setVersion("0.3.0");

        var latest = versions.getLatest(List.of(major, minor, release), false);
        assertEquals(release, latest);
    }

    @Test
    void testGetLatestTargetPlatformSortUniversal() {
        var version = "1.0.0";
        var universal = new ExtensionVersion();
        universal.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        universal.setVersion(version);

        var linux = new ExtensionVersion();
        linux.setTargetPlatform(TargetPlatform.NAME_LINUX_X64);
        linux.setVersion(version);

        var windows = new ExtensionVersion();
        windows.setTargetPlatform(TargetPlatform.NAME_WIN32_ARM64);
        windows.setVersion(version);

        var latest = versions.getLatest(List.of(windows, linux, universal), false);
        assertEquals(universal, latest);
    }

    @Test
    void testGetLatestTargetPlatformSort() {
        var version = "1.0.0";
        var web = new ExtensionVersion();
        web.setTargetPlatform(TargetPlatform.NAME_WEB);
        web.setVersion(version);

        var linux = new ExtensionVersion();
        linux.setTargetPlatform(TargetPlatform.NAME_LINUX_X64);
        linux.setVersion(version);

        var windows = new ExtensionVersion();
        windows.setTargetPlatform(TargetPlatform.NAME_WIN32_ARM64);
        windows.setVersion(version);

        var latest = versions.getLatest(List.of(windows, linux, web), false);
        assertEquals(linux, latest);
    }

    @Test
    void testGetLatestPreRelease() {
        var release = new ExtensionVersion();
        release.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        release.setPreRelease(false);
        release.setVersion("1.0.0");

        var minor = new ExtensionVersion();
        minor.setTargetPlatform(TargetPlatform.NAME_LINUX_ARM64);
        minor.setPreRelease(true);
        minor.setVersion("0.0.5");

        var major = new ExtensionVersion();
        major.setTargetPlatform(TargetPlatform.NAME_LINUX_ARM64);
        major.setPreRelease(true);
        major.setVersion("0.3.0");

        var latest = versions.getLatest(List.of(major, minor, release), false, true);
        assertEquals(major, latest);
    }

    @Test
    void testGetLatestNoPreRelease() {
        var release = new ExtensionVersion();
        release.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        release.setPreRelease(false);
        release.setVersion("1.0.0");

        var minor = new ExtensionVersion();
        minor.setTargetPlatform(TargetPlatform.NAME_LINUX_ARM64);
        minor.setPreRelease(true);
        minor.setVersion("1.0.0-next.1fd3e8c");

        var major = new ExtensionVersion();
        major.setTargetPlatform(TargetPlatform.NAME_LINUX_ARM64);
        major.setPreRelease(true);
        major.setVersion("0.3.0");

        var latest = versions.getLatest(List.of(major, minor, release), false, false);
        assertEquals(release, latest);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        VersionService versionService(RepositoryService repositoryService) {
            return new VersionService(repositoryService);
        }
    }
}