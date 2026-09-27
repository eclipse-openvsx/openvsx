/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;

public interface ExtensionRepository extends Repository<Extension, Long> {

    Streamable<Extension> findByNamespace(Namespace namespace);

    Streamable<Extension> findByNamespaceAndActiveTrueOrderByNameAsc(Namespace namespace);

    Extension findByNameIgnoreCaseAndNamespace(String name, Namespace namespace);

    Extension findByNameIgnoreCaseAndNamespaceNameIgnoreCase(String name, String namespace);

    // Publish takes this lock (waiting) before adding a version. We use a native FOR UPDATE rather
    // than @Lock(PESSIMISTIC_WRITE) on purpose: on Hibernate 6 + PostgreSQL that maps to the weaker
    // FOR NO KEY UPDATE, which does NOT conflict with the FOR KEY SHARE a version insert takes, so a
    // concurrent publish could still insert a row. FOR UPDATE conflicts with it and blocks the insert.
    // "OF e" scopes the lock to the extension row only (not the joined namespace) — also not
    // expressible via @Lock.
    @Query(
        value = "select e.* from extension e join namespace n on n.id = e.namespace_id"
                + " where lower(e.name) = lower(:name) and lower(n.name) = lower(:namespace) for update of e",
        nativeQuery = true
    )
    Extension findByNameIgnoreCaseAndNamespaceNameIgnoreCaseForUpdate(
            @Param("name") String name,
            @Param("namespace") String namespace
    );

    // Delete takes this NOWAIT variant so it fails fast (instead of blocking) when a publish holds
    // the lock, letting us surface a "retry" error rather than removing the extension under a publish.
    // Native for the same reason as above (FOR UPDATE strength + OF e); NOWAIT could also be done via
    // a @QueryHint lock-timeout of 0, but we keep both clauses in one explicit native query.
    @Query(
        value = "select e.* from extension e join namespace n on n.id = e.namespace_id"
                + " where lower(e.name) = lower(:name) and lower(n.name) = lower(:namespace) for update of e nowait",
        nativeQuery = true
    )
    Extension findByNameIgnoreCaseAndNamespaceNameIgnoreCaseForUpdateNoWait(
            @Param("name") String name,
            @Param("namespace") String namespace
    );

    Streamable<Extension> findByActiveTrue();

    Streamable<Extension> findByIdIn(Collection<Long> extensionIds);

    Streamable<Extension> findDistinctByVersionsPublishedBy(UserData user);

    long count();

    @Query("select coalesce(max(e.downloadCount), 0) from Extension e")
    int getMaxDownloadCount();

    @Query("select e from Extension e where concat(e.namespace.name, '.', e.name) not in(?1)")
    Streamable<Extension> findAllNotMatchingByExtensionId(List<String> extensionIds);

    Streamable<Extension> findByReplacement(Extension replacement);

    // Extensions where `active` disagrees with whether any of their versions actually is. Repairs the
    // fallout of the cross-transaction lost-update race @DynamicUpdate on Extension now prevents going
    // forward; used by ExtensionActiveFlagReconciler to fix rows already affected.
    @Query(
        value = "select e.* from extension e where e.active <> exists"
                + " (select 1 from extension_version v where v.extension_id = e.id and v.active = true)",
        nativeQuery = true
    )
    Streamable<Extension> findExtensionsWithInconsistentActiveFlag();

    // Extensions whose last_updated_date - what the sitemap reports as `lastmod` - is older than their
    // own latest active version. Same cross-transaction lost-update race as
    // findExtensionsWithInconsistentActiveFlag; used by LastUpdatedDateCheck to fix rows already
    // affected. See issue #2229.
    @Query(
        value = "select e.* from extension e where e.active = true and e.last_updated_date <"
                + " (select max(v.timestamp) from extension_version v"
                + " where v.extension_id = e.id and v.active = true)",
        nativeQuery = true
    )
    Streamable<Extension> findExtensionsWithStaleLastUpdatedDate();
}
