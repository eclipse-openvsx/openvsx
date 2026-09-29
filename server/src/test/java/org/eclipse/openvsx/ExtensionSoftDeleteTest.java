/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.Streamable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Integration tests for the soft-delete (immutable version) feature in {@link ExtensionService}.
 * <p>
 * A "deleted" extension version is soft-deleted: its row is kept as a permanent tombstone (marked
 * {@code removed} and inactive, with its files stripped from storage) so the version identity stays
 * reserved and can never be republished. Only a purge physically removes the row and frees the identity.
 * These tests exercise the end-to-end behaviour against a real database, as well as the query paths that
 * must exclude tombstones from public surfaces.
 * <p>
 * {@code @Isolated}: collides with {@code ConsistencyCheckServiceTest}/{@code ExtensionActiveFlagCheckTest}
 * scanning and locking every {@code Extension} row while this test mutates its own concurrently.
 */
@SpringBootTest
@Isolated
class ExtensionSoftDeleteTest extends AbstractPostgresContainerTest {

    private static final String NAMESPACE = "soft-delete-testns";
    private static final String EXTENSION = "soft-delete-testext";
    private static final String OWNER_LOGIN = "soft-delete-owner";

    @Autowired
    ExtensionService extensionService;

    @MockitoSpyBean
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    private long extensionId;
    private long ownerId;
    private long ownerTokenId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var owner = new UserData();
            owner.setLoginName(OWNER_LOGIN);
            em.persist(owner);

            var token = new PersonalAccessToken();
            token.setUser(owner);
            token.setValue(OWNER_LOGIN + "_token");
            token.setCreatedTimestamp(LocalDateTime.now());
            token.setActive(true);
            token.setType(PersonalAccessTokenType.LLT);
            em.persist(token);

            var namespace = new Namespace();
            namespace.setName(NAMESPACE);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(EXTENSION);
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);
            em.flush();

            ownerId = owner.getId();
            ownerTokenId = token.getId();
            extensionId = extension.getId();
        });
    }

    private UserData owner() {
        var owner = new UserData();
        owner.setId(ownerId);
        owner.setLoginName(OWNER_LOGIN);
        return owner;
    }

    /**
     * Deleting a version while another exists soft-deletes it: the row survives as a tombstone that is
     * marked removed, inactive, and stamped with the deleting user and a timestamp.
     */
    @Test
    void deleteExtensionVersion_softDeletesKeepingTombstone() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);

        assertThat(versionExists("1.0.0"))
                .as("a soft-deleted version's row must be kept as an immutable tombstone")
                .isTrue();
        assertThat(versionRemoved("1.0.0"))
                .as("a soft-deleted version must be marked removed and inactive")
                .isTrue();
        assertThat(removedByOf("1.0.0"))
                .as("a soft-deleted version records who removed it")
                .isEqualTo(ownerId);
        assertThat(removedTimestampOf("1.0.0"))
                .as("a soft-deleted version records when it was removed")
                .isNotNull();
        assertThat(versionRemoved("2.0.0"))
                .as("an untouched version must stay live")
                .isFalse();
    }

    /**
     * Deleting an already-removed version is an idempotent no-op: it neither fails nor re-stamps the
     * tombstone (the original removal metadata is preserved).
     */
    @Test
    void deleteExtensionVersion_isIdempotentForAlreadyRemoved() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);
        var firstTimestamp = removedTimestampOf("1.0.0");

        // A second delete of the same version must not throw and must not touch the tombstone.
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);

        assertThat(versionRemoved("1.0.0")).isTrue();
        assertThat(removedTimestampOf("1.0.0"))
                .as("re-deleting a tombstone must not re-stamp its removal timestamp")
                .isEqualTo(firstTimestamp);
    }

    /**
     * Purging permanently removes the version row (unlike soft-delete), freeing the identity.
     */
    @Test
    void purgeExtensionVersion_physicallyRemovesRow() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, targets);

        assertThat(versionExists("1.0.0"))
                .as("a purged version's row must be physically removed")
                .isFalse();
        assertThat(versionExists("2.0.0"))
                .as("an untouched version must survive the purge")
                .isTrue();
    }

    /**
     * Soft-deleting then purging the same version leaves no row behind, freeing the identity.
     */
    @Test
    void softDeleteThenPurge_removesTombstone() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);
        assertThat(versionRemoved("1.0.0")).isTrue();

        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, targets);
        assertThat(versionExists("1.0.0"))
                .as("purging a tombstone must physically remove its row")
                .isFalse();
    }

    /**
     * Background jobs (mirror, migrations) collect the versions to purge in one transaction and purge them
     * afterwards, so the version handed over is detached. It must still be removed rather than blowing up
     * with "Removing a detached instance".
     */
    @Test
    void purgeExtensionVersion_acceptsDetachedVersion() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        // read in a transaction of its own, so the version is detached by the time it is purged
        var detached = new TransactionTemplate(txManager).execute(
                status -> repositories.findVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, EXTENSION, NAMESPACE));

        extensionService.purgeExtensionVersion(owner(), detached);

        assertThat(versionExists("1.0.0"))
                .as("purging a detached version must physically remove its row")
                .isFalse();
        assertThat(versionExists("2.0.0"))
                .as("an untouched version must survive the purge")
                .isTrue();
    }

    /**
     * Purging an extension by name locks and re-reads it, so callers that only hold detached entities
     * (the mirror deleting extensions dropped upstream) can purge it.
     */
    @Test
    void purgeExtension_byNameRemovesExtensionAndVersions() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, false, true);

        extensionService.purgeExtension(owner(), NAMESPACE, EXTENSION);

        assertThat(versionExists("1.0.0")).isFalse();
        assertThat(versionExists("2.0.0"))
                .as("purging an extension must also remove its tombstones")
                .isFalse();
        assertThat(extensionExists())
                .as("purging an extension must remove the extension record itself")
                .isFalse();
    }

    /**
     * Fix #2: the version listing used by the public {@code /versions} API must never surface tombstones.
     */
    @Test
    void findActiveVersionsSorted_excludesRemovedVersions() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, false, true);

        var page = repositories.findActiveVersionsSorted(NAMESPACE, EXTENSION, PageRequest.of(0, 10));
        var versions = page.getContent().stream().map(ExtensionVersion::getVersion).toList();

        assertThat(versions)
                .as("the public versions listing must exclude soft-deleted versions")
                .contains("1.0.0")
                .doesNotContain("2.0.0");
    }

    /**
     * Fix #3: the download URL map must never surface tombstones (whose files are gone anyway).
     */
    @Test
    void findVersionsForUrls_excludesRemovedVersions() {
        // Same version string, two target platforms: the universal one is live, the linux one is removed.
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("1.0.0", TargetPlatform.NAME_LINUX_X64, false, true);

        var extension = repositories.findExtension(EXTENSION, NAMESPACE);
        var forUrls = repositories.findVersionsForUrls(extension, null, "1.0.0");
        var platforms = forUrls.stream().map(ExtensionVersion::getTargetPlatform).toList();

        assertThat(platforms)
                .as("the download map must only include active target platforms, never tombstones")
                .containsExactly(TargetPlatform.NAME_UNIVERSAL);
    }

    /**
     * The dependency guard must fire when a delete removes the last <em>active</em> versions, even if
     * older tombstones still occupy rows. Regression test for the flaw where the guard was keyed on the
     * total row count (tombstones included) instead of the active versions, so deleting all active
     * versions of a depended-on extension slipped through without the check.
     */
    @Test
    void deleteExtension_runsDependencyCheckWhenDeletingAllActiveVersionsDespiteTombstone() {
        persistVersion("0.9.0", TargetPlatform.NAME_UNIVERSAL, false, true); // pre-existing tombstone
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false); // the only active version

        // Simulate another extension depending on this one, so the dependency guard must reject.
        doReturn(Streamable.of(dependantReference())).when(repositories).findDependenciesReference(any());

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        assertThatThrownBy(() -> extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets))
                .as("deleting all active versions of a depended-on extension must run the dependency check")
                .isInstanceOf(ErrorResultException.class);

        assertThat(versionRemoved("1.0.0"))
                .as("a rejected delete must leave the active version untouched")
                .isFalse();
        assertThat(versionActive("1.0.0"))
                .as("the active version must survive the rejected delete")
                .isTrue();
    }

    /**
     * Deleting only a subset of the active versions is not a delete-all, so the dependency guard must
     * not fire: the selected version is soft-deleted and the remaining active version stays live.
     */
    @Test
    void deleteExtension_skipsDependencyCheckWhenDeletingSubsetOfActiveVersions() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        // Even if a dependency exists, deleting a subset must not trigger the guard.
        doReturn(Streamable.of(dependantReference())).when(repositories).findDependenciesReference(any());

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.deleteExtension(owner(), false, NAMESPACE, EXTENSION, targets);

        assertThat(versionRemoved("1.0.0"))
                .as("deleting a subset must soft-delete the selected version without a dependency check")
                .isTrue();
        assertThat(versionActive("2.0.0"))
                .as("the remaining active version must stay live")
                .isTrue();
    }

    /**
     * Purging explicit versions must remove only those versions: pre-existing tombstones and the
     * extension record itself must survive so reserved identities stay reserved.
     */
    @Test
    void purgeExtension_withExplicitTargets_keepsExtensionAndTombstones() {
        persistVersion("0.9.0", TargetPlatform.NAME_UNIVERSAL, false, true); // pre-existing tombstone
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, targets);

        assertThat(versionExists("1.0.0"))
                .as("the purged version's row must be physically removed")
                .isFalse();
        assertThat(versionExists("0.9.0"))
                .as("a tombstone the caller did not select must survive a scoped purge")
                .isTrue();
        assertThat(extensionExists())
                .as("a scoped purge must not remove the extension record itself")
                .isTrue();
    }

    /**
     * Purging every version of an extension by naming them all removes the extension as a whole,
     * so its record is not left orphaned.
     */
    @Test
    void purgeExtension_purgingAllVersionsRemovesExtension() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        var targets = new TargetPlatformVersion[] {
            TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"),
            TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "2.0.0")
        };
        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, targets);

        assertThat(versionExists("1.0.0")).isFalse();
        assertThat(versionExists("2.0.0")).isFalse();
        assertThat(extensionExists())
                .as("purging all versions of an extension must remove the extension record too")
                .isFalse();
    }

    /**
     * The dependency guard must also fire on the purge path when purging all active versions.
     */
    @Test
    void purgeExtension_runsDependencyCheckWhenPurgingAllActiveVersions() {
        persistVersion("0.9.0", TargetPlatform.NAME_UNIVERSAL, false, true); // pre-existing tombstone
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        // Simulate another extension depending on this one.
        doReturn(Streamable.of(dependantReference())).when(repositories).findDependenciesReference(any());

        var targets = TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0");
        assertThatThrownBy(() -> extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, targets))
                .as("purging all active versions of a depended-on extension must run the dependency check")
                .isInstanceOf(ErrorResultException.class);

        assertThat(versionExists("1.0.0"))
                .as("a rejected purge must leave the active version in place")
                .isTrue();
        assertThat(versionExists("0.9.0"))
                .as("a rejected purge must leave tombstones in place")
                .isTrue();
    }

    /**
     * Duplicate target versions must not inflate the "all active versions" check: purging the same
     * subset version twice must be treated as purging that single version, so the dependency guard
     * (which only applies when all active versions are removed) does not fire and the other version
     * survives. Regression test for the duplicate-driven miscount.
     */
    @Test
    void purgeExtension_deDuplicatesTargetsBeforeCounting() {
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);
        persistVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        // The extension is depended on: the guard would reject removing ALL active versions.
        doReturn(Streamable.of(dependantReference())).when(repositories).findDependenciesReference(any());

        var duplicate = new TargetPlatformVersion[] {
            TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0"),
            TargetPlatformVersion.of(TargetPlatform.NAME_UNIVERSAL, "1.0.0")
        };
        // Only a single distinct version is targeted, so this is not an "all versions" purge and must
        // succeed without tripping the dependency guard.
        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION, duplicate);

        assertThat(versionExists("1.0.0"))
                .as("the named version must be purged")
                .isFalse();
        assertThat(versionExists("2.0.0"))
                .as("a version that was not named must survive")
                .isTrue();
        assertThat(extensionExists())
                .as("purging a subset must not remove the extension record")
                .isTrue();
    }

    /**
     * A purge with no target versions purges nothing: versions must be named explicitly (the
     * whole-extension shortcut was removed).
     */
    @Test
    void purgeExtension_withEmptyTargetsIsNoOp() {
        persistVersion("0.9.0", TargetPlatform.NAME_UNIVERSAL, false, true); // pre-existing tombstone
        persistVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, true, false);

        extensionService.purgeExtensionNoWait(owner(), NAMESPACE, EXTENSION);

        assertThat(versionExists("1.0.0"))
                .as("an empty-target purge must not remove any version")
                .isTrue();
        assertThat(versionExists("0.9.0"))
                .as("an empty-target purge must not remove tombstones")
                .isTrue();
        assertThat(extensionExists())
                .as("an empty-target purge must not remove the extension record")
                .isTrue();
    }

    private ExtensionVersion dependantReference() {
        var namespace = new Namespace();
        namespace.setName("dependant-ns");
        var extension = new Extension();
        extension.setName("dependant-ext");
        extension.setNamespace(namespace);
        var extVersion = new ExtensionVersion();
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        return extVersion;
    }

    private void persistVersion(String version, String targetPlatform, boolean active, boolean removed) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var extension = em.find(Extension.class, extensionId);
            var extVersion = new ExtensionVersion();
            extVersion.setVersion(version);
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setExtension(extension);
            extVersion.setPublishedBy(em.getReference(UserData.class, ownerId));
            extVersion.setActive(active);
            extVersion.setRemoved(removed);
            if (removed) {
                extVersion.setActive(false);
                extVersion.setRemovedTimestamp(LocalDateTime.now());
                extVersion.setRemovedBy(em.getReference(UserData.class, ownerId));
            }
            em.persist(extVersion);
        });
    }

    private boolean versionExists(String version) {
        return count(
                "select ev.id from ExtensionVersion ev where ev.version = :version "
                        + "and ev.extension.namespace.name = :namespace",
                version) > 0;
    }

    private boolean versionRemoved(String version) {
        return count(
                "select ev.id from ExtensionVersion ev where ev.version = :version "
                        + "and ev.extension.namespace.name = :namespace and ev.removed = true and ev.active = false",
                version) > 0;
    }

    private boolean versionActive(String version) {
        return count(
                "select ev.id from ExtensionVersion ev where ev.version = :version "
                        + "and ev.extension.namespace.name = :namespace and ev.active = true and ev.removed = false",
                version) > 0;
    }

    private boolean extensionExists() {
        return Boolean.TRUE.equals(
                new TransactionTemplate(txManager).execute(
                        status -> !em.createQuery(
                                "select e.id from Extension e "
                                        + "where e.name = :name and e.namespace.name = :namespace")
                                .setParameter("name", EXTENSION)
                                .setParameter("namespace", NAMESPACE)
                                .getResultList()
                                .isEmpty()));
    }

    private Long removedByOf(String version) {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select ev.removedBy.id from ExtensionVersion ev where ev.version = :version "
                                + "and ev.extension.namespace.name = :namespace",
                        Long.class)
                        .setParameter("version", version)
                        .setParameter("namespace", NAMESPACE)
                        .getSingleResult());
    }

    private LocalDateTime removedTimestampOf(String version) {
        return new TransactionTemplate(txManager).execute(
                status -> em.createQuery(
                        "select ev.removedTimestamp from ExtensionVersion ev where ev.version = :version "
                                + "and ev.extension.namespace.name = :namespace",
                        LocalDateTime.class)
                        .setParameter("version", version)
                        .setParameter("namespace", NAMESPACE)
                        .getSingleResult());
    }

    private long count(String jpql, String version) {
        return new TransactionTemplate(txManager).execute(
                status -> (long) em.createQuery(jpql)
                        .setParameter("version", version)
                        .setParameter("namespace", NAMESPACE)
                        .getResultList()
                        .size());
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionVersion ev where ev.extension.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from PersistedLog pl where pl.user.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
            em.createQuery("delete from PersonalAccessToken t where t.user.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
            em.createQuery("delete from Namespace n where n.name = :namespace")
                    .setParameter("namespace", NAMESPACE).executeUpdate();
            em.createQuery("delete from UserData u where u.loginName = :login")
                    .setParameter("login", OWNER_LOGIN).executeUpdate();
        });
    }
}
