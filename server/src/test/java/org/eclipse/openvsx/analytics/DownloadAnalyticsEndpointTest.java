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
package org.eclipse.openvsx.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.eclipse.openvsx.AbstractTimeseriesContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;
import org.eclipse.openvsx.storage.StorageUtilService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack proof of the enabled configuration: the TimescaleDB-backed repository on its own
 * database, queried through the public REST endpoint.
 * <p>
 * {@code @Isolated}: aggregates over the whole shared download_analytics table, colliding with unrelated
 * tests concurrently writing their own download events into it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Isolated
class DownloadAnalyticsEndpointTest extends AbstractTimeseriesContainerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DownloadAnalyticsRepository repository;

    @Autowired
    @Qualifier("timeseriesDataSource")
    javax.sql.DataSource dataSource;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    StorageUtilService storageUtilService;

    private Extension extension;

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        new JdbcTemplate(dataSource).execute("TRUNCATE download_event");
        if (extension != null) {
            inTransaction(() -> {
                var users = new ArrayList<>();
                var managed = entityManager.find(Extension.class, extension.getId());
                managed.getVersions().forEach(extVersion -> {
                    entityManager
                            .createQuery("delete from FileResource fr where fr.extension = :extVersion")
                            .setParameter("extVersion", extVersion)
                            .executeUpdate();
                    entityManager.remove(extVersion);
                    // FK extVersion -> userData; doing it last
                    users.add(extVersion.getPublishedBy());
                });
                users.forEach(user -> entityManager.remove(user));
                var namespace = managed.getNamespace();
                entityManager.remove(managed);
                entityManager.remove(namespace);
                return null;
            });
            extension = null;
        }
    }

    @Test
    void testDownloadSeriesEndToEnd() throws Exception {
        extension = seedExtension("e2ens", "e2e-ext");
        var today = LocalDate.now(ZoneOffset.UTC);
        var twoDaysAgo = today.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        repository.save(
                List.of(
                        event(twoDaysAgo.plus(10, ChronoUnit.HOURS), extension.getId(), 3),
                        event(twoDaysAgo.plus(18, ChronoUnit.HOURS), extension.getId(), 1),
                        event(today.atStartOfDay(ZoneOffset.UTC).toInstant(), extension.getId(), 5)));

        // 52 weeks of days, the last of them today's
        mockMvc.perform(get("/api/e2ens/e2e-ext/analytics/downloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(364))
                .andExpect(jsonPath("$.points[0].t").value(today.minusDays(363).toString()))
                .andExpect(jsonPath("$.points[361].count").value(4))
                .andExpect(jsonPath("$.points[362].count").value(0))
                .andExpect(jsonPath("$.points[363].t").value(today.toString()))
                .andExpect(jsonPath("$.points[363].count").value(5));
    }

    @Test
    void testUnknownExtensionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/nowhere/nothing/analytics/downloads")).andExpect(status().isNotFound());
    }

    /**
     * Without a log-based source covering the file, a request-path download produces an analytics
     * event alongside the counter update, with client data taken from the current HTTP request.
     */
    @Test
    void testRequestPathDownloadProducesAnalyticsEvent() throws Exception {
        var resource = seedExtensionWithResource("e2ereq", "e2e-req-ext", "e2ereq.e2e-req-ext-1.0.0.vsix");
        var extVersion = resource.getExtension();

        var request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "VSCode 1.90.2 (Microsoft Visual Studio Code)");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        inTransaction(() -> {
            storageUtilService.increaseDownloadCount(entityManager.find(FileResource.class, resource.getId()));
            return null;
        });

        // the counter committed in the registry database
        var downloadCount = inTransaction(
                () -> entityManager.find(Extension.class, extension.getId()).getDownloadCount());
        assertEquals(1, downloadCount);

        var jdbc = new JdbcTemplate(dataSource);
        var event = jdbc.queryForMap(
                "SELECT extension_id, extension_version_id, ip, user_agent, count FROM download_event");
        assertEquals(extension.getId(), event.get("extension_id"));
        assertEquals(extVersion.getId(), event.get("extension_version_id"));
        assertEquals("203.0.113.9", event.get("ip"));
        assertEquals("VSCode 1.90.2 (Microsoft Visual Studio Code)", event.get("user_agent"));
        assertEquals(1, event.get("count"));

        // and the event is visible through the endpoint, in the last point: today's (partial) bucket
        mockMvc.perform(get("/api/e2ereq/e2e-req-ext/analytics/downloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[363].count").value(1));
    }

    private DownloadEvent event(Instant time, long extensionId, int count) {
        return new DownloadEvent(
                time,
                extensionId,
                extensionId * 100,
                "e2ens",
                "e2e-ext",
                "1.0.0",
                "universal",
                "US",
                "9.9.9.9",
                "VSCode 1.90.2",
                count);
    }

    private FileResource seedExtensionWithResource(String namespaceName, String extensionName, String vsixFilename) {
        extension = seedExtension(namespaceName, extensionName);
        return inTransaction(() -> {
            var extVersion = entityManager.find(Extension.class, extension.getId()).getVersions().get(0);
            var resource = new FileResource();
            resource.setName(vsixFilename);
            resource.setType(FileResource.DOWNLOAD);
            resource.setStorageType(FileResource.STORAGE_LOCAL);
            resource.setExtension(extVersion);
            entityManager.persist(resource);
            return resource;
        });
    }

    private Extension seedExtension(String namespaceName, String extensionName) {
        return inTransaction(() -> {
            var namespace = new Namespace();
            namespace.setName(namespaceName);
            entityManager.persist(namespace);

            var seeded = new Extension();
            seeded.setName(extensionName);
            seeded.setNamespace(namespace);
            seeded.setActive(true);
            entityManager.persist(seeded);

            var extVersion = new ExtensionVersion();
            extVersion.setVersion("1.0.0");
            extVersion.setTargetPlatform("universal");
            extVersion.setExtension(seeded);
            extVersion.setActive(true);
            var publisher = new UserData();
            publisher.setLoginName("publisher");
            entityManager.persist(publisher);
            extVersion.setPublishedBy(publisher);
            entityManager.persist(extVersion);
            return seeded;
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
