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
package org.eclipse.openvsx;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.publish.PublishExtensionVersionHandler;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.DrainOnCloseInputStream;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ExtensionServiceTest {

    @Mock
    PublishingConfig publishingConfig;
    @Mock
    EntityManager entityManager;
    @Mock
    RepositoryService repositories;
    @Mock
    SearchUtilService search;
    @Mock
    CacheService cache;
    @Mock
    LogService logs;
    @Mock
    PublishExtensionVersionHandler publishHandler;
    @Mock
    JobRequestScheduler scheduler;
    @Mock
    ExtensionScanService scanService;
    @Mock
    ExtensionScanPersistenceService scanPersistenceService;

    private ExtensionService svc;

    @BeforeEach
    void setUp() {
        svc = new ExtensionService(
                publishingConfig,
                entityManager,
                repositories,
                search,
                cache,
                logs,
                publishHandler,
                scheduler,
                scanService,
                scanPersistenceService);
    }

    @Test
    void shouldNotReactivateExtensionsWithErroredScans() {
        var user = mockUser();
        var ext = mockExtension();
        var extVersion = mockExtensionVersion(ext, "1.1.0", ScanStatus.ERRORED, user);
        ext.getVersions().add(extVersion);

        svc.reactivateExtensions(user);

        assertThat(ext.isActive()).isFalse();
        assertThat(extVersion.isActive()).isFalse();
    }

    @Test
    void shouldNotReactivateExtensionsWithQuarantinedScans() {
        var user = mockUser();
        var ext = mockExtension();
        var extVersion = mockExtensionVersion(ext, "1.1.0", ScanStatus.QUARANTINED, user);
        ext.getVersions().add(extVersion);

        svc.reactivateExtensions(user);

        assertThat(ext.isActive()).isFalse();
        assertThat(extVersion.isActive()).isFalse();
    }

    @Test
    void shouldReactivateExtensionsWithPassedScans() {
        var user = mockUser();
        var ext = mockExtension();
        var extVersion = mockExtensionVersion(ext, "1.1.0", ScanStatus.PASSED, user);
        ext.getVersions().add(extVersion);

        svc.reactivateExtensions(user);

        assertThat(ext.isActive()).isTrue();
        assertThat(extVersion.isActive()).isTrue();
        // becoming visible again is its own transition, reported after the one for the deactivation
        // rather than replacing it
        Mockito.verify(repositories)
                .recordExtensionVersionChange(
                        Mockito.eq(extVersion),
                        Mockito.eq(ExtensionVersionState.ACTIVE),
                        Mockito.any());
    }

    @Test
    void shouldNotRecordAChangeForVersionsThatAreNotReactivated() {
        var user = mockUser();
        var ext = mockExtension();
        var extVersion = mockExtensionVersion(ext, "1.1.0", ScanStatus.QUARANTINED, user);
        ext.getVersions().add(extVersion);

        svc.reactivateExtensions(user);

        // The version stays hidden, so nothing happened that the changes feed should report.
        assertThat(extVersion.isActive()).isFalse();
        Mockito.verify(repositories, Mockito.never())
                .recordExtensionVersionChange(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
    }

    @Test
    void shouldReportADeletedVersionAsRemoved() {
        var ext = mockExtension();
        var extVersion = plainExtensionVersion(ext, "1.1.0");
        ext.getVersions().add(extVersion);
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        // the feed announced this version as available at some point
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion))
                .thenReturn(Optional.of(change(extVersion, ExtensionVersionState.ACTIVE)));

        svc.deleteExtensionVersion(mockUser(), extVersion);

        // The version stops being available for download here, so the feed has to report that it is gone.
        Mockito.verify(repositories)
                .recordExtensionVersionChange(
                        Mockito.eq(extVersion),
                        Mockito.eq(ExtensionVersionState.REMOVED),
                        Mockito.any());
    }

    @Test
    void shouldNotReportADeletionOfAVersionThatWasNeverPublic() {
        var ext = mockExtension();
        var extVersion = plainExtensionVersion(ext, "1.1.0");
        // a version that never became public: published, then held back by a quarantining scan
        extVersion.setActive(false);
        ext.getVersions().add(extVersion);
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        // no entry was ever written for it
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion)).thenReturn(Optional.empty());

        svc.deleteExtensionVersion(mockUser(), extVersion);

        // The feed never announced this version, so a deletion has nothing to withdraw -- reporting one
        // would tell consumers a version they have never seen is gone. Same rule as for a purge.
        assertThat(extVersion.isRemoved()).isTrue();
        Mockito.verify(repositories, Mockito.never())
                .recordExtensionVersionChange(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReportAPurgedVersionAsRemoved() {
        var extVersion = plainExtensionVersion(mockExtension(), "1.1.0");
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        // the feed announced this version as available at some point
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion))
                .thenReturn(Optional.of(change(extVersion, ExtensionVersionState.ACTIVE)));

        svc.removeExtensionVersion(extVersion);

        // A purge takes the row with it, so unless the feed reports it the version would just stop
        // appearing and consumers would keep offering a download that no longer exists. It is reported
        // as removed like a deletion: the tombstone a deletion keeps is invisible from the outside.
        // Recorded as a purge, so the entry does not reference the row this transaction is about to delete.
        Mockito.verify(repositories)
                .recordPurgedExtensionVersionChange(
                        Mockito.eq(extVersion),
                        Mockito.eq(ExtensionVersionState.REMOVED),
                        Mockito.any());
    }

    @Test
    void shouldNotReportAPurgeOfAVersionThatWasAlreadyRemoved() {
        var extVersion = plainExtensionVersion(mockExtension(), "1.1.0");
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion))
                .thenReturn(Optional.of(change(extVersion, ExtensionVersionState.REMOVED)));

        svc.removeExtensionVersion(extVersion);

        // Purging a deleted version only drops its tombstone. The feed already reported it as gone, so a
        // second entry would claim a transition that never happened.
        Mockito.verify(repositories, Mockito.never())
                .recordPurgedExtensionVersionChange(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldNotReportAPurgeOfAVersionThatWasNeverPublic() {
        var extVersion = plainExtensionVersion(mockExtension(), "1.1.0");
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        // no entry was ever written for it, e.g. its publication failed before it was activated
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion)).thenReturn(Optional.empty());

        svc.removeExtensionVersion(extVersion);

        // The feed never announced this version, so it has nothing to withdraw. Reporting a removal here
        // would tell consumers about a version they have never seen.
        Mockito.verify(repositories, Mockito.never())
                .recordPurgedExtensionVersionChange(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldDetachTheLogFromAPurgedVersionEvenWhenItReportsNothing() {
        var extVersion = plainExtensionVersion(mockExtension(), "1.1.0");
        Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
        Mockito.when(repositories.findLatestExtensionVersionChange(extVersion))
                .thenReturn(Optional.of(change(extVersion, ExtensionVersionState.REMOVED)));

        svc.removeExtensionVersion(extVersion);

        // The entries stay in the log but must stop pointing at the row being deleted, on this path too:
        // an entry still referencing it when the transaction flushes is rejected outright, which would
        // make purging a previously deleted version fail altogether.
        Mockito.verify(repositories).detachExtensionVersionChanges(extVersion);
    }

    @Test
    void shouldReactivateExtensionsWithQuarantinedScansAndAllowed() {
        var user = mockUser();
        var ext = mockExtension();
        var extVersion = mockExtensionVersion(ext, "1.1.0", ScanStatus.QUARANTINED, user);
        ext.getVersions().add(extVersion);

        var scan = repositories.findLatestExtensionScan(extVersion);
        var decision = new AdminScanDecision();
        decision.setDecision(AdminScanDecision.ALLOWED);

        Mockito.when(repositories.findAdminScanDecision(scan)).thenReturn(decision);

        svc.reactivateExtensions(user);

        assertThat(ext.isActive()).isTrue();
        assertThat(extVersion.isActive()).isTrue();
    }

    @Test
    void shouldNotReactivateRemovedVersions() {
        var user = mockUser();
        var ext = mockExtension();

        // A soft-deleted (removed) version is a permanent tombstone: even though it is inactive (and would
        // otherwise be a reactivation candidate), it must never be brought back to life.
        var extVersion = new ExtensionVersion();
        extVersion.setId(1L);
        extVersion.setVersion("1.1.0");
        extVersion.setTargetPlatform("linux");
        extVersion.setActive(false);
        extVersion.setRemoved(true);
        extVersion.setExtension(ext);
        ext.getVersions().add(extVersion);

        Mockito.when(repositories.findVersionsByUser(user, false)).thenReturn(Streamable.of(extVersion));

        svc.reactivateExtensions(user);

        assertThat(extVersion.isActive()).isFalse();
        assertThat(ext.isActive()).isFalse();
        // A tombstone is rejected up front, so its scan state is never even consulted.
        Mockito.verify(repositories, Mockito.never()).findLatestExtensionScan(Mockito.any());
    }

    @Test
    void shouldEvictSitemapCacheOnUpdate() {
        var ext = mockExtension();

        // lastUpdatedDate - what sitemap.xml reports as `lastmod` - is stamped right here, so this is
        // the one place that must evict it; see #2229.
        svc.updateExtension(ext);

        Mockito.verify(cache).evictSitemap();
    }

    /**
     * Validating and scanning a package that can not be published anyway is pointless, so the publish
     * preconditions (publisher exists, access rights, version not published yet) are checked first and
     * no scan is initialized or run when they fail.
     */
    @Test
    void shouldNotScanWhenPublishPreconditionsFail() {
        Mockito.when(publishingConfig.getMaxContentSize()).thenReturn(1024L);
        Mockito.when(scanService.isEnabled()).thenReturn(true);
        Mockito.doThrow(new ErrorResultException("Insufficient access rights for publisher: redhat"))
                .when(publishHandler).checkPublishPreconditions(Mockito.any(), Mockito.any());

        var token = mockToken();
        var content = new ByteArrayInputStream("extension package".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(
                () -> svc.publishVersion(
                        content,
                        new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Insufficient access rights");

        Mockito.verify(scanService, Mockito.never()).initializeScan(Mockito.any(), Mockito.any());
        Mockito.verify(scanService, Mockito.never())
                .runValidation(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(scanService, Mockito.never()).removeScan(Mockito.any());
        Mockito.verify(publishHandler, Mockito.never())
                .publishAsync(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldRejectAPackageExceedingTheMaxContentSize() {
        var maxContentSize = 10L;
        Mockito.when(publishingConfig.getMaxContentSize()).thenReturn(maxContentSize);

        var token = mockToken();
        var raw = new ByteArrayInputStream(
                "this content is well over ten bytes long".getBytes(StandardCharsets.UTF_8));
        // LocalRegistryService#publish always wraps the request body this way before calling
        // publishVersion(...); wrapping it here too, with the same cap, is what actually reaches
        // this code path in production.
        var content = new DrainOnCloseInputStream(raw, maxContentSize);

        assertThatThrownBy(
                () -> svc.publishVersion(
                        content,
                        new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null)))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("exceeds the size limit")
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    // ---------- UTILITY ----------//

    /**
     * A version attached to the given extension, without the scan and lookup stubbing that
     * {@link #mockExtensionVersion} sets up for the reactivation flow.
     */
    private ExtensionVersion plainExtensionVersion(Extension extension, String version) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(1L);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform("linux");
        extVersion.setActive(true);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setExtension(extension);
        return extVersion;
    }

    private ExtensionVersionChange change(ExtensionVersion extVersion, ExtensionVersionState state) {
        var change = new ExtensionVersionChange();
        change.setExtensionVersion(extVersion);
        change.setState(state);
        change.setChangedAt(LocalDateTime.parse("2000-01-01T10:00"));
        return change;
    }

    private Extension mockExtension() {
        var namespace = new Namespace();
        namespace.setId(2);
        namespace.setPublicId("test-2");
        namespace.setName("redhat");

        var extension = new Extension();
        extension.setId(1);
        extension.setPublicId("test-1");
        extension.setName("vscode-yaml");
        extension.setAverageRating(3.0);
        extension.setReviewCount(10L);
        extension.setDownloadCount(100);
        extension.setActive(false);
        extension.setPublishedDate(LocalDateTime.parse("1999-12-01T09:00"));
        extension.setLastUpdatedDate(LocalDateTime.parse("2000-01-01T10:00"));
        extension.setNamespace(namespace);

        return extension;
    }

    private ExtensionVersion mockExtensionVersion(
            Extension extension,
            String version,
            ScanStatus status,
            UserData user
    ) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(1L);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform("linux");
        extVersion.setPreview(true);
        extVersion.setActive(false);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setDisplayName("YAML");
        extVersion.setDescription("YAML Language Support");
        extVersion.setEngines(List.of("vscode@^1.31.0"));
        extVersion.setRepository("https://github.com/redhat-developer/vscode-yaml");
        extVersion.setDependencies(Collections.emptyList());
        extVersion.setBundledExtensions(Collections.emptyList());
        extVersion.setLocalizedLanguages(Collections.emptyList());
        extVersion.setExtension(extension);

        var completedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        var scan = new ExtensionScan();
        scan.setId(10L);
        scan.setStatus(status);
        scan.setCompletedAt(completedAt);
        scan.setExtensionVersion(version);

        Mockito.when(repositories.findLatestExtensionScan(extVersion)).thenReturn(scan);
        Mockito.when(repositories.findVersionsByUser(user, false)).thenReturn(Streamable.of(extVersion));

        return extVersion;
    }

    private UserData mockUser() {
        var user = new UserData();
        user.setLoginName("test");
        user.setProvider("github");
        user.setEclipseToken(new AuthToken("12345", null, null, null, null, null));
        return user;
    }

    private PersonalAccessToken mockToken() {
        var token = new PersonalAccessToken();
        token.setId(1L);
        token.setValue("token");
        token.setUser(mockUser());
        token.setType(PersonalAccessTokenType.LLT);
        return token;
    }
}
