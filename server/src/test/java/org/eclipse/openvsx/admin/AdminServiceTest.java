/******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
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
package org.eclipse.openvsx.admin;

import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.UserAccessJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminService}.
 * <p>
 * Mainly {@link AdminService#purgeExtensionAndReferencingExtensions(UserData, String, String)}: the cascade
 * that purges an extension together with every extension that references it (packs bundling it, extensions
 * depending on it), walking the reverse-reference direction.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final String NAMESPACE = "n";

    @Mock
    RepositoryService repositories;

    @Mock
    ExtensionService extensions;

    @Mock
    EclipseService eclipse;

    @Mock
    LogService logs;

    @Mock
    UserService users;

    @Mock
    EntityManager entityManager;

    @InjectMocks
    AdminService adminService;

    private final UserData admin = new UserData();
    private long idSequence = 0;

    private Extension extension(String name) {
        var namespace = new Namespace();
        namespace.setName(NAMESPACE);
        var extension = new Extension();
        extension.setId(++idSequence);
        extension.setName(name);
        extension.setNamespace(namespace);
        return extension;
    }

    private ExtensionVersion version(Extension extension) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(++idSequence);
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        return extVersion;
    }

    private void mockNoReferences(Extension extension) {
        when(repositories.findBundledExtensionsReference(extension)).thenReturn(Streamable.empty());
        when(repositories.findDependenciesReference(extension)).thenReturn(Streamable.empty());
    }

    @Test
    void revokingPublisherContributionsRemovesEachMembershipThroughUserService() {
        var user = new UserData();
        user.setLoginName("amy");
        var namespace = new Namespace();
        namespace.setName(NAMESPACE);
        var membership = new NamespaceMembership();
        membership.setUser(user);
        membership.setNamespace(namespace);
        membership.setRole(NamespaceMembership.ROLE_OWNER);

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.empty());
        when(repositories.findMemberships(user)).thenReturn(Streamable.of(membership));

        var result = adminService.revokePublisherContributions("github", "amy", admin);

        // handing over the row we already hold is what keeps one unremovable membership from aborting the
        // whole revoke, and going through UserService is what deletes the trusted publishers with it
        verify(users).removeNamespaceMembership(membership);
        assertThat(result.getSuccess()).contains("removed 1 namespace memberships");
    }

    @Test
    void revokingPublisherContributionsRecordsWhenTheVersionsStoppedBeingVisible() {
        var user = new UserData();
        user.setLoginName("amy");
        var extVersion = version(extension("ext"));
        extVersion.setActive(true);

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.of(extVersion));

        adminService.revokePublisherContributions("github", "amy", admin);

        assertThat(extVersion.isActive()).isFalse();
        assertThat(extVersion.isRemoved()).isFalse();
        // The version stops being publicly visible without being removed. Unless that transition is
        // appended to the log, the changes feed would keep reporting the version as active and
        // consumers following the feed would never learn that it disappeared.
        verify(repositories).recordExtensionVersionChange(
                eq(extVersion),
                eq(ExtensionVersionState.INACTIVE),
                any());
    }

    // Regression: revokePublisherAgreement used to be called unguarded, so any exception it threw
    // (Eclipse API down, unexpected runtime error, ...) rolled back the whole revoke, including the
    // token/extension/membership cleanup that has nothing to do with Eclipse.
    @Test
    void revokingPublisherContributionsStillDeactivatesEverythingElseWhenTheEclipseRevokeFails() {
        var user = new UserData();
        user.setLoginName("amy");
        user.setEclipsePersonId("12345");
        var extVersion = version(extension("ext"));
        extVersion.setActive(true);

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.of(extVersion));
        when(eclipse.isActive()).thenReturn(true);
        when(eclipse.determinePublisherAgreementStatus(user)).thenReturn("signed");
        doThrow(new RuntimeException("Eclipse API is down")).when(eclipse).revokePublisherAgreement(user, admin);

        var result = adminService.revokePublisherContributions("github", "amy", admin);

        assertThat(extVersion.isActive()).isFalse();
        verify(repositories).recordExtensionVersionChange(
                eq(extVersion),
                eq(ExtensionVersionState.INACTIVE),
                any());
        // The failure is reported back rather than silently dropped or failing the whole call.
        assertThat(result.getError()).isNull();
        assertThat(result.getWarning()).contains("Eclipse API is down");
    }

    // Regression: the revoke used to be attempted whenever an Eclipse person ID was present,
    // even if the agreement status was 'none' or could not be determined at all - calling the
    // Eclipse API on a guess rather than a confirmed agreement.
    @Test
    void revokingPublisherContributionsDoesNotCallEclipseWhenTheAgreementStatusIsNotConfirmed() {
        var user = new UserData();
        user.setLoginName("amy");
        user.setEclipsePersonId("12345");

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.empty());
        when(eclipse.isActive()).thenReturn(true);
        when(eclipse.determinePublisherAgreementStatus(user)).thenReturn(null);

        var result = adminService.revokePublisherContributions("github", "amy", admin);

        verify(eclipse, never()).revokePublisherAgreement(any(), any());
        assertThat(result.getError()).isNull();
        assertThat(result.getWarning()).isNull();
    }

    @Test
    void forgetUserDeletesTheUserRowWhenNothingElseReferencesThem() {
        var user = new UserData();
        user.setLoginName("amy");

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, false)).thenReturn(Streamable.empty());
        when(repositories.findCustomerMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());

        adminService.forgetUser("github", "amy", admin);

        verify(entityManager).remove(user);
    }

    // Regression: extension_version.published_by_id is documented as permanent - it is never
    // cleared, not even once the version itself is soft-deleted - and has a foreign key with no ON
    // DELETE clause. A version this user published, but which was already removed by someone else
    // (e.g. an admin) before this call, is not touched by the delete-all-versions loop above (it
    // early-returns for already-removed versions without reassigning removedBy), so it must still
    // block a hard delete of the row, or entityManager.remove(user) would violate that FK.
    @Test
    void forgetUserAnonymizesInsteadOfDeletingWhenAVersionWasAlreadyRemovedBySomeoneElse() {
        var user = new UserData();
        user.setLoginName("amy");
        var admin2 = new UserData();
        admin2.setLoginName("staff");

        var extVersion = version(extension("ext"));
        extVersion.setPublishedBy(user);
        extVersion.setActive(false);
        extVersion.setRemoved(true);
        extVersion.setRemovedBy(admin2);

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, false)).thenReturn(Streamable.of(extVersion));
        when(repositories.findCustomerMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());

        adminService.forgetUser("github", "amy", admin);

        verify(entityManager, never()).remove(user);
        assertThat(user.getLoginName()).isEqualTo("deleted-user-" + user.getId());
        assertThat(user.getFullName()).isNull();
    }

    // A tombstone that keeps its permissions would still be an account holding admin access, and one
    // that no longer shows up under any role while doing so.
    @Test
    void forgetUserStripsPermissionsFromTheAnonymizedRow() {
        var user = new UserData();
        user.setLoginName("amy");
        user.setRole(UserData.Role.PRIVILEGED);
        user.getPermissions().add(Permission.MANAGE_PUBLISHERS);

        var extVersion = version(extension("ext"));
        extVersion.setPublishedBy(user);
        extVersion.setRemoved(true);
        extVersion.setRemovedBy(admin);

        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);
        when(repositories.findMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.empty());
        when(repositories.findVersionsByUser(user, false)).thenReturn(Streamable.of(extVersion));
        when(repositories.findCustomerMemberships(user)).thenReturn(Streamable.empty());
        when(repositories.findPersonalAccessTokens(user)).thenReturn(Streamable.empty());

        adminService.forgetUser("github", "amy", admin);

        verify(entityManager, never()).remove(user);
        assertThat(user.getRole()).isNull();
        assertThat(user.getPermissions()).isEmpty();
    }

    // The search goes through a jOOQ query that selects user_data columns only, so the users it
    // returns never carry permissions - they have to be loaded separately to reach the JSON.
    @Test
    void searchUsersReportsPermissionsTheJooqQueryDoesNotSelect() {
        var fromSearch = new UserData();
        fromSearch.setId(7);
        fromSearch.setLoginName("amy");
        fromSearch.setProvider("github");

        var withPermissions = new UserData();
        withPermissions.setId(7);
        withPermissions.setLoginName("amy");
        withPermissions.getPermissions().add(Permission.MANAGE_SCANS);

        var pageable = Pageable.ofSize(10);
        when(repositories.searchUsers(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(fromSearch), pageable, 1));
        when(repositories.findUsersById(List.of(7L))).thenReturn(List.of(withPermissions));
        when(repositories.findMemberships(fromSearch)).thenReturn(Streamable.empty());

        var page = adminService.searchUsers(null, null, pageable);

        assertThat(page.getContent()).singleElement()
                .extracting(json -> json.getUser().getPermissions())
                .isEqualTo(Set.of("manage_scans"));
    }

    @Test
    void purgesReferencingExtensionAsWholeWhenAllVersionsReference() {
        var target = extension("target");
        var referencing = extension("referencing");
        // The referencing extension has two versions, both bundling the target.
        var refV1 = version(referencing);
        var refV2 = version(referencing);

        when(extensions.lockExtension(NAMESPACE, "target")).thenReturn(target);
        when(repositories.findBundledExtensionsReference(target)).thenReturn(Streamable.of(refV1, refV2));
        when(repositories.findDependenciesReference(target)).thenReturn(Streamable.empty());
        when(repositories.countVersions(NAMESPACE, "referencing")).thenReturn(2);
        mockNoReferences(referencing);

        adminService.purgeExtensionAndReferencingExtensions(admin, NAMESPACE, "target");

        // The referencing extension is purged as a whole (not version-by-version) so nothing is orphaned.
        verify(extensions).purgeExtension(admin, referencing, false);
        verify(extensions).purgeExtension(admin, target, false);
        verify(extensions, never()).purgeExtensionVersion(any(), any());
    }

    @Test
    void purgesOnlyReferencingVersionsWhenSomeVersionsReference() {
        var target = extension("target");
        var referencing = extension("referencing");
        // Only one of the referencing extension's three versions bundles the target.
        var refV1 = version(referencing);

        when(extensions.lockExtension(NAMESPACE, "target")).thenReturn(target);
        when(repositories.findBundledExtensionsReference(target)).thenReturn(Streamable.of(refV1));
        when(repositories.findDependenciesReference(target)).thenReturn(Streamable.empty());
        when(repositories.countVersions(NAMESPACE, "referencing")).thenReturn(3);

        adminService.purgeExtensionAndReferencingExtensions(admin, NAMESPACE, "target");

        // Only the referencing version is purged; the extension keeps its other versions.
        verify(extensions).purgeExtensionVersion(admin, refV1);
        verify(extensions, never()).purgeExtension(admin, referencing, false);
        verify(extensions).purgeExtension(admin, target, false);
    }

    @Test
    void handlesReferenceCyclesWithoutInfiniteRecursion() {
        // a and b bundle each other; both are single-version extensions.
        var a = extension("a");
        var b = extension("b");
        var aV = version(a);
        var bV = version(b);

        when(extensions.lockExtension(NAMESPACE, "a")).thenReturn(a);
        // versions bundling a -> b's version; versions bundling b -> a's version
        when(repositories.findBundledExtensionsReference(a)).thenReturn(Streamable.of(bV));
        when(repositories.findDependenciesReference(a)).thenReturn(Streamable.empty());
        when(repositories.findBundledExtensionsReference(b)).thenReturn(Streamable.of(aV));
        when(repositories.findDependenciesReference(b)).thenReturn(Streamable.empty());
        when(repositories.countVersions(NAMESPACE, "a")).thenReturn(1);
        when(repositories.countVersions(NAMESPACE, "b")).thenReturn(1);

        assertThatCode(() -> adminService.purgeExtensionAndReferencingExtensions(admin, NAMESPACE, "a"))
                .doesNotThrowAnyException();

        verify(extensions).purgeExtension(admin, a, false);
        verify(extensions).purgeExtension(admin, b, false);
        verify(extensions, never()).purgeExtensionVersion(any(), any());
    }

    @Test
    void purgesDeepReferenceChainWithoutDepthLimit() {
        // Chain of 8 single-version extensions where each references the previous one; the previous
        // depth limit (> 5) would have aborted this legitimate chain, the visited-set does not.
        var chain = new Extension[8];
        var chainVersions = new ExtensionVersion[8];
        for (var i = 0; i < chain.length; i++) {
            chain[i] = extension("e" + i);
            chainVersions[i] = version(chain[i]);
        }

        when(extensions.lockExtension(NAMESPACE, "e0")).thenReturn(chain[0]);
        for (var i = 0; i < chain.length; i++) {
            // chain[i+1] references chain[i]
            when(repositories.findBundledExtensionsReference(chain[i]))
                    .thenReturn(i + 1 < chain.length ? Streamable.of(chainVersions[i + 1]) : Streamable.empty());
            when(repositories.findDependenciesReference(chain[i])).thenReturn(Streamable.empty());
            if (i > 0) {
                when(repositories.countVersions(NAMESPACE, "e" + i)).thenReturn(1);
            }
        }

        assertThatCode(() -> adminService.purgeExtensionAndReferencingExtensions(admin, NAMESPACE, "e0"))
                .doesNotThrowAnyException();

        for (var extension : chain) {
            verify(extensions).purgeExtension(admin, extension, false);
        }
    }

    @Test
    void updateUserAccessAppliesTheRoleAndPermissionsTogether() {
        var user = new UserData();
        user.setLoginName("amy");
        user.getPermissions().add(Permission.MANAGE_CACHES);
        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);

        var access = new UserAccessJson("privileged", List.of("manage_extensions"));
        var result = adminService.updateUserAccess("github", "amy", access, admin);

        assertThat(user.getRole()).isEqualTo(UserData.Role.PRIVILEGED);
        assertThat(user.getPermissions()).containsExactly(Permission.MANAGE_EXTENSIONS);
        assertThat(result.getSuccess())
                .contains("set the role to privileged", "granted manage_extensions", "revoked manage_caches");
    }

    /** The body is the state to end up in, so an absent list means "hold nothing", not "leave as is". */
    @Test
    void updateUserAccessRevokesEverythingWhenNoPermissionsAreGiven() {
        var user = new UserData();
        user.setLoginName("amy");
        user.getPermissions().add(Permission.MANAGE_EXTENSIONS);
        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);

        adminService.updateUserAccess("github", "amy", new UserAccessJson("none", null), admin);

        assertThat(user.getPermissions()).isEmpty();
    }

    @Test
    void updateUserAccessReportsWhenThereIsNothingToApply() {
        var user = new UserData();
        user.setLoginName("amy");
        user.setRole(UserData.Role.ADMIN);
        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);

        var access = new UserAccessJson("admin", List.of());
        assertThat(adminService.updateUserAccess("github", "amy", access, admin).getSuccess())
                .isEqualTo("No access changes for user github/amy.");
        verify(logs, never()).logAction(any(), any());
    }

    @Test
    void updateUserAccessRejectsAnUnknownPermissionName() {
        var user = new UserData();
        user.setLoginName("amy");
        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);

        var access = new UserAccessJson("none", List.of("not-a-permission"));
        assertThatThrownBy(() -> adminService.updateUserAccess("github", "amy", access, admin))
                .isInstanceOf(ErrorResultException.class);
    }

    /** A rejected permission must not leave the role already applied - they save as one change. */
    @Test
    void updateUserAccessLeavesTheRoleUntouchedWhenAPermissionIsRejected() {
        var user = new UserData();
        user.setLoginName("amy");
        when(repositories.findUserByLoginName("github", "amy")).thenReturn(user);

        var access = new UserAccessJson("admin", List.of("not-a-permission"));
        assertThatThrownBy(() -> adminService.updateUserAccess("github", "amy", access, admin))
                .isInstanceOf(ErrorResultException.class);

        assertThat(user.getRole()).isNull();
    }

    @Test
    void checkPermissionAllowsAUserIndividuallyGrantedIt() {
        var user = new UserData();
        user.getPermissions().add(Permission.MANAGE_CACHES);
        when(users.findLoggedInUser()).thenReturn(user);

        assertThat(adminService.checkPermission(Permission.MANAGE_CACHES)).isSameAs(user);
    }

    // Regression: ADMIN must keep implying every permission, including ones a currently-ADMIN user
    // was never individually granted - see UserData#hasPermission.
    @Test
    void checkPermissionAllowsAdminRegardlessOfIndividualGrants() {
        var user = new UserData();
        user.setRole(UserData.Role.ADMIN);
        when(users.findLoggedInUser()).thenReturn(user);

        assertThat(adminService.checkPermission(Permission.MANAGE_SETTINGS)).isSameAs(user);
    }

    @Test
    void checkPermissionRejectsAUserLackingThePermission() {
        var user = new UserData();
        when(users.findLoggedInUser()).thenReturn(user);

        assertThatThrownBy(() -> adminService.checkPermission(Permission.MANAGE_SETTINGS))
                .isInstanceOf(ErrorResultException.class);
    }
}
