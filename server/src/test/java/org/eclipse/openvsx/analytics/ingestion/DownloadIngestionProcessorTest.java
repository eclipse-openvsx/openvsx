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
package org.eclipse.openvsx.analytics.ingestion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.analytics.DownloadEvent;
import org.eclipse.openvsx.analytics.DownloadSeriesRequest;
import org.eclipse.openvsx.analytics.DownloadSeriesRow;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.DownloadAnalyticsRepository;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DownloadIngestionProcessorTest extends AbstractPostgresContainerTest {

    private static final LocalDateTime PROCESSED_ON = LocalDateTime.of(2026, 7, 1, 15, 0);

    @Autowired
    DownloadIngestionProcessor processor;

    @Autowired
    RecordingAnalyticsRepository analyticsRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    RepositoryService repositories;

    private final List<Object> seededEntities = new CopyOnWriteArrayList<>();

    private long seededVersionId;

    @AfterEach
    void cleanUp() {
        analyticsRepository.failing = false;
        analyticsRepository.saved.clear();
        runInTransaction(() -> {
            seededEntities.reversed().forEach(entity -> {
                var merged = entityManager.merge(entity);
                entityManager.remove(merged);
            });
            entityManager.createQuery("delete from DownloadIngestion i where i.name like 'analytics-test%'")
                    .executeUpdate();
        });
        seededEntities.clear();
    }

    @Test
    void testProcessAggregatesAndDefersAnalytics() {
        var extension = seedExtension("proc1", "proc1.ext-1.0.0.vsix");

        var hour1 = Instant.parse("2026-07-01T14:00:00Z");
        var records = List.of(
                new RawDownloadRecord(hour1.plusSeconds(60), "PROC1.EXT-1.0.0.VSIX", "US", "9.9.9.9", "VSCode 1.90.2"),
                new RawDownloadRecord(hour1.plusSeconds(120), "PROC1.EXT-1.0.0.VSIX", "US", "9.9.9.9", "VSCode 1.90.2"),
                new RawDownloadRecord(hour1.plusSeconds(180), "PROC1.EXT-1.0.0.VSIX", null, null, null),
                new RawDownloadRecord(
                        hour1.plusSeconds(3660),
                        "PROC1.EXT-1.0.0.VSIX",
                        "US",
                        "9.9.9.9",
                        "VSCode 1.90.2"));

        var processed = processor.process(FileResource.STORAGE_AWS, "analytics-test-1.gz", PROCESSED_ON, 5, records);

        assertEquals(1, processed.extensions().size());
        assertEquals(extension.getId(), processed.extensions().get(0).getId());

        // the events are handed back rather than stored, so the analytics write happens after
        // the registry transaction committed
        assertTrue(analyticsRepository.saved.isEmpty());
        processor.saveEvents(processed.events());

        // micro-batch aggregation by (hour, extension-version, country, ip, user agent)
        assertEquals(3, analyticsRepository.saved.size());
        var aggregated = findEvent(hour1, "US", "VSCode 1.90.2");
        assertEquals(2, aggregated.count());
        assertEquals(extension.getId(), aggregated.extensionId());
        assertEquals(seededVersionId, aggregated.extensionVersionId());
        assertEquals("proc1", aggregated.namespace());
        assertEquals("proc1-ext", aggregated.extensionName());
        assertEquals("1.0.0", aggregated.version());
        assertEquals("universal", aggregated.targetPlatform());
        assertEquals("9.9.9.9", aggregated.ip());
        assertEquals("VSCode 1.90.2", aggregated.userAgent());
        var withoutUserAgent = findEvent(hour1, null, null);
        assertEquals(1, withoutUserAgent.count());
        var laterHour = findEvent(hour1.plusSeconds(3600), "US", "VSCode 1.90.2");
        assertEquals(1, laterHour.count());

        // the download counter is incremented by the total record count
        assertEquals(4, freshDownloadCount(extension.getId()));

        // and the ingestion entry is written
        assertEquals(
                List.of("analytics-test-1.gz"),
                repositories.findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
                        FileResource.STORAGE_AWS,
                        List.of("analytics-test-1.gz")));
    }

    @Test
    void testUnknownFileIsSkipped() {
        var extension = seedExtension("proc2", "proc2.ext-1.0.0.vsix");

        var records = List.of(
                new RawDownloadRecord(Instant.parse("2026-07-01T14:00:00Z"), "NO.SUCH-1.0.0.VSIX", null, null, null));
        var processed = processor
                .process(FileResource.STORAGE_AWS, "analytics-test-2.gz", PROCESSED_ON, 5, records);
        processor.saveEvents(processed.events());

        assertTrue(analyticsRepository.saved.isEmpty());
        assertEquals(0, freshDownloadCount(extension.getId()));
        // the file is still marked as processed
        assertEquals(
                List.of("analytics-test-2.gz"),
                repositories.findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
                        FileResource.STORAGE_AWS,
                        List.of("analytics-test-2.gz")));
    }

    @Test
    void testFilenameResolutionIsCached() {
        seedExtension("proc3", "proc3.ext-1.0.0.vsix");

        var record = new RawDownloadRecord(
                Instant.parse("2026-07-01T14:00:00Z"),
                "PROC3.EXT-1.0.0.VSIX",
                null,
                null,
                null);
        processor.process(FileResource.STORAGE_AWS, "analytics-test-3a.gz", PROCESSED_ON, 5, List.of(record));
        processor.process(FileResource.STORAGE_AWS, "analytics-test-3b.gz", PROCESSED_ON, 5, List.of(record));

        Mockito.verify(repositories, Mockito.times(1))
                .findDownloadsByStorageTypeAndName(FileResource.STORAGE_AWS, List.of("PROC3.EXT-1.0.0.VSIX"));
    }

    @Test
    void testInducedFailureRollsBackWholeTransaction() {
        var extension = seedExtension("proc4", "proc4.ext-1.0.0.vsix");

        var records = List.of(
                new RawDownloadRecord(
                        Instant.parse("2026-07-01T14:00:00Z"),
                        "PROC4.EXT-1.0.0.VSIX",
                        "US",
                        "9.9.9.9",
                        null));
        // the download ingestion entry's name column is varchar(255); an overlong name fails the transaction
        // after the events were saved and the counter was incremented
        var overlongName = "analytics-test-" + "x".repeat(300);
        assertThrows(
                Exception.class,
                () -> processor.process(FileResource.STORAGE_AWS, overlongName, PROCESSED_ON, 5, records));

        assertEquals(0, freshDownloadCount(extension.getId()));
        assertTrue(
                repositories.findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
                        FileResource.STORAGE_AWS,
                        List.of(overlongName)).isEmpty());
    }

    @Test
    void testAnalyticsFailureLeavesIngestionIntact() {
        var extension = seedExtension("proc5", "proc5.ext-1.0.0.vsix");

        var records = List.of(
                new RawDownloadRecord(
                        Instant.parse("2026-07-01T14:00:00Z"),
                        "PROC5.EXT-1.0.0.VSIX",
                        "US",
                        "9.9.9.9",
                        null));
        var processed = processor
                .process(FileResource.STORAGE_AWS, "analytics-test-5.gz", PROCESSED_ON, 5, records);

        // the analytics store is a separate database; losing it under-counts, nothing more
        analyticsRepository.failing = true;
        assertDoesNotThrow(() -> processor.saveEvents(processed.events()));

        assertEquals(1, freshDownloadCount(extension.getId()));
        assertEquals(
                List.of("analytics-test-5.gz"),
                repositories.findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
                        FileResource.STORAGE_AWS,
                        List.of("analytics-test-5.gz")));
    }

    private DownloadEvent findEvent(Instant time, String country, String userAgent) {
        return analyticsRepository.saved.stream()
                .filter(event -> event.time().equals(time))
                .filter(event -> userAgent == null ? event.userAgent() == null : userAgent.equals(event.userAgent()))
                .filter(event -> country == null ? event.country() == null : country.equals(event.country()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event for " + time + "/" + country + "/" + userAgent));
    }

    private int freshDownloadCount(long extensionId) {
        return inTransaction(() -> entityManager.find(Extension.class, extensionId).getDownloadCount());
    }

    private Extension seedExtension(String namespaceName, String vsixFilename) {
        return inTransaction(() -> {
            var namespace = new Namespace();
            namespace.setName(namespaceName);
            entityManager.persist(namespace);

            var extension = new Extension();
            extension.setName(namespaceName + "-ext");
            extension.setNamespace(namespace);
            extension.setActive(true);
            entityManager.persist(extension);

            var extVersion = new ExtensionVersion();
            extVersion.setVersion("1.0.0");
            extVersion.setTargetPlatform("universal");
            extVersion.setExtension(extension);
            extVersion.setActive(true);
            entityManager.persist(extVersion);
            seededVersionId = extVersion.getId();

            var resource = new FileResource();
            resource.setName(vsixFilename);
            resource.setType(FileResource.DOWNLOAD);
            resource.setStorageType(FileResource.STORAGE_AWS);
            resource.setExtension(extVersion);
            entityManager.persist(resource);

            seededEntities.addAll(List.of(namespace, extension, extVersion, resource));
            return extension;
        });
    }

    private void runInTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @TestConfiguration
    static class RecordingRepositoryConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        RecordingAnalyticsRepository recordingAnalyticsRepository() {
            return new RecordingAnalyticsRepository();
        }
    }

    /** Overrides everything the processor calls, so the inherited DSLContext is never touched. */
    static class RecordingAnalyticsRepository extends DownloadAnalyticsRepository {
        RecordingAnalyticsRepository() {
            super(null);
        }

        final List<DownloadEvent> saved = new CopyOnWriteArrayList<>();

        volatile boolean failing;

        @Override
        public void save(List<DownloadEvent> events) {
            if (failing) {
                throw new IllegalStateException("analytics database is unreachable");
            }

            saved.addAll(events);
        }

        @Override
        public List<DownloadSeriesRow> findSeries(DownloadSeriesRequest request) {
            return List.of();
        }
    }
}
