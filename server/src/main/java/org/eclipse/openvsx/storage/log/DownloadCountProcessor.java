/********************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage.log;

import com.google.common.collect.Lists;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.DownloadCountProcessedItem;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DownloadCountProcessor {

    protected final Logger logger = LoggerFactory.getLogger(DownloadCountProcessor.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final CacheService cache;
    private final SearchUtilService search;
    private final ObservationRegistry observations;

    public DownloadCountProcessor(
            EntityManager entityManager,
            RepositoryService repositories,
            CacheService cache,
            SearchUtilService search,
            ObservationRegistry observations
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.cache = cache;
        this.search = search;
        this.observations = observations;
    }

    @Transactional
    public void persistProcessedItem(String name, String storageType, LocalDateTime processedOn, int executionTime, boolean success) {
        Observation.createNotStarted("DownloadCountProcessor#persistProcessedItem", observations).observe(() -> {
            var processedItem = new DownloadCountProcessedItem();
            processedItem.setName(name);
            processedItem.setStorageType(storageType);
            processedItem.setProcessedOn(processedOn);
            processedItem.setExecutionTime(executionTime);
            processedItem.setSuccess(success);
            entityManager.persist(processedItem);
        });
    }

    public Map<Long, Integer> processDownloadCounts(String storageType, Map<String, Integer> files) {
        return Observation.createNotStarted("DownloadCountProcessor#processDownloadCounts", observations).observe(() -> repositories.findDownloadsByStorageTypeAndName(storageType, files.keySet()).stream()
                .map(fileResource -> Map.entry(fileResource, files.get(fileResource.getName().toUpperCase())))
                .filter(fileResource -> {
                    var ev = fileResource.getKey().getExtension();
                    if (ev == null) {
                        logger.warn("no extension version found for download {}, skipping", fileResource.getKey().getName());
                        return false;
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.groupingBy(
                        e -> e.getKey().getExtension().getExtension().getId(),
                        Collectors.summingInt(Map.Entry::getValue)
                )));
    }

    @Transactional
    public List<Extension> increaseDownloadCounts(Map<Long, Integer> extensionDownloads) {
        return Observation.createNotStarted("DownloadCountProcessor#increaseDownloadCounts", observations).observe(() -> {
            var extensions = repositories.findExtensions(extensionDownloads.keySet()).toList();
            extensions.forEach(extension -> {
                var downloads = extensionDownloads.get(extension.getId());
                extension.setDownloadCount(extension.getDownloadCount() + downloads);
            });

            return extensions;
        });
    }

    @Transactional // needs transaction for lazy-loading versions
    public void evictCaches(Extension extension) {
        Observation.createNotStarted("DownloadCountProcessor#evictCaches", observations).observe(() -> {
            var mergedExtension = entityManager.merge(extension);
            cache.evictExtensionJsons(mergedExtension);
            cache.evictLatestExtensionVersion(mergedExtension);
        });
    }

    public void updateSearchEntries(List<Extension> extensions) {
        Observation.createNotStarted("DownloadCountProcessor#updateSearchEntries", observations).observe(() -> {
            logger.info("[DownloadCountProcessor] >> updateSearchEntries");
            var activeExtensions = extensions.stream()
                    .filter(Extension::isActive)
                    .collect(Collectors.toList());

            logger.info("[DownloadCountProcessor] total active extensions: {}", activeExtensions.size());
            var parts = Lists.partition(activeExtensions, 100);
            logger.info("[DownloadCountProcessor] partitions: {} | partition size: 100", parts.size());

            parts.forEach(search::updateSearchEntriesAsync);
            logger.info("[DownloadCountProcessor] << updateSearchEntries");
        });
    }

    public List<String> processedItems(String storageType, List<String> blobNames) {
        return Observation.createNotStarted("DownloadCountProcessor#processedItems", observations).observe(() ->
                repositories.findAllSucceededDownloadCountProcessedItemsByStorageTypeAndNameIn(storageType, blobNames)
        );
    }

    public List<String> failedItems(String storageType, List<String> blobNames) {
        return Observation.createNotStarted("DownloadCountProcessor#failedItems", observations).observe(() ->
                repositories.findAllFailedDownloadCountProcessedItemsByStorageTypeAndNameIn(storageType, blobNames)
        );
    }
}
