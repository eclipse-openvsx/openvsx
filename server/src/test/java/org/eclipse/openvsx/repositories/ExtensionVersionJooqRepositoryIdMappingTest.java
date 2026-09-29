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

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queries that read a version out of a {@code CROSS JOIN LATERAL} remap the static field constants
 * onto the derived table. {@link org.jooq.Table#field(org.jooq.Field)} resolves by name rather than by
 * lineage, so a field of another table that happens to share a name with one of the derived table's -
 * {@code namespace.id} and {@code extension.id} against the version's own {@code id} - is answered with
 * the derived table's column, and the caller silently reads a version id where it wanted a namespace id.
 * <p>
 * Every id here is deliberately forced to a different value. Written naively they all land on the same
 * sequence number in a fresh database, and the assertions pass whether the mapping is right or wrong.
 */
@SpringBootTest
class ExtensionVersionJooqRepositoryIdMappingTest extends AbstractPostgresContainerTest {

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    private record Ids(long user, long namespace, long extension, long version) {}

    @Test
    void findLatestByUserReportsTheRealNamespaceAndExtensionIds() {
        var ids = persistOneVersionWithDistinctIds();

        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                var user = em.find(UserData.class, ids.user());
                var found = repositories.findLatestVersions(user);

                assertThat(found).hasSize(1);
                var version = found.getFirst();
                assertThat(version.getId()).isEqualTo(ids.version());
                // these two used to come back as the version's id, which made UserAPI's namespace membership
                // filter match nothing and emptied the user's extension list
                assertThat(version.getExtension().getId()).isEqualTo(ids.extension());
                assertThat(version.getExtension().getNamespace().getId()).isEqualTo(ids.namespace());
            });
        } finally {
            cleanUp(ids);
        }
    }

    @Test
    void findLatestByExtensionIdReportsTheRealNamespaceAndExtensionIds() {
        var ids = persistOneVersionWithDistinctIds();

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var found = repositories.findLatestVersions(java.util.List.of(ids.extension()));

            assertThat(found).hasSize(1);
            var version = found.getFirst();
            // the search path keys its results by extension id; a version id here made every hit look
            // absent from the database, and each search purged those entries from the index
            assertThat(version.getExtension().getId()).isEqualTo(ids.extension());
            assertThat(version.getExtension().getNamespace().getId()).isEqualTo(ids.namespace());
        });

        cleanUp(ids);
    }

    /**
     * Pads each sequence by a different amount, so a namespace id can never be mistaken for an extension
     * id or a version id.
     */
    private Ids persistOneVersionWithDistinctIds() {
        return new TransactionTemplate(txManager).execute(status -> {
            for (var i = 0; i < 3; i++) {
                var filler = new Namespace();
                filler.setName("id-mapping-filler-ns-" + i);
                em.persist(filler);
            }

            var user = new UserData();
            user.setLoginName("id-mapping-user");
            user.setProvider("github");
            em.persist(user);

            var namespace = new Namespace();
            namespace.setName("id-mapping-ns");
            em.persist(namespace);
            em.flush();

            for (var i = 0; i < 7; i++) {
                var filler = new Extension();
                filler.setName("id-mapping-filler-ext-" + i);
                filler.setNamespace(namespace);
                filler.setActive(true);
                em.persist(filler);
            }

            var extension = new Extension();
            extension.setName("id-mapping-ext");
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);
            em.flush();

            var version = new ExtensionVersion();
            version.setVersion("1.0.0");
            version.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            version.setExtension(extension);
            version.setPublishedBy(user);
            version.setActive(true);
            version.setTimestamp(TimeUtil.getCurrentUTC());
            em.persist(version);
            em.flush();

            var ids = new Ids(user.getId(), namespace.getId(), extension.getId(), version.getId());
            assertThat(java.util.Set.of(ids.namespace(), ids.extension(), ids.version()))
                    .describedAs("the ids have to differ or this test cannot tell a wrong mapping from a right one")
                    .hasSize(3);
            return ids;
        });
    }

    /** The container is shared with every other test, so leave nothing of this one behind. */
    private void cleanUp(Ids ids) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createQuery("delete from ExtensionVersion v where v.id = :id").setParameter("id", ids.version())
                    .executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.id = :id").setParameter("id", ids.namespace())
                    .executeUpdate();
            em.createQuery("delete from Namespace n where n.id = :nsId or n.name like 'id-mapping-filler-ns-%'")
                    .setParameter("nsId", ids.namespace())
                    .executeUpdate();
            em.createQuery("delete from UserData u where u.id = :id").setParameter("id", ids.user()).executeUpdate();
        });
    }
}
