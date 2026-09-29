/********************************************************************************
 * Copyright (c) 2021 Red Hat, Inc. and others
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
package org.eclipse.openvsx.search;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;

/**
 * Wrap all available implementations and redirect to the implementation pickup
 * from configuration
 */
@Service
public class SearchUtilService implements ISearchService {

    private final DatabaseSearchService databaseSearchService;
    private final ElasticSearchService elasticSearchService;
    private final RepositoryService repositories;

    public SearchUtilService(
            DatabaseSearchService databaseSearchService,
            ElasticSearchService elasticSearchService,
            RepositoryService repositories
    ) {
        this.databaseSearchService = databaseSearchService;
        this.elasticSearchService = elasticSearchService;
        this.repositories = repositories;
    }

    public boolean isEnabled() {
        return this.databaseSearchService.isEnabled() || this.elasticSearchService.isEnabled();
    }

    /**
     * Take the implementation being enabled. If two are defined, it's a
     * configuration error.
     */
    protected ISearchService getImplementation() {
        if (databaseSearchService.isEnabled() && elasticSearchService.isEnabled()) {
            throw new IllegalStateException(
                    "Only one search engine can be enabled at a time. Here both elasticsearch and database search are enabled.");
        }

        if (this.databaseSearchService.isEnabled()) {
            return this.databaseSearchService;
        }

        // return default implementation which is elastic search
        return this.elasticSearchService;

    }

    public SearchResult search(ISearchService.Options options) {
        return options.requestedSize() > 0 ? getImplementation().search(options) : new SearchResult();
    }

    /**
     * Reports on the search index for the engine that is actually answering searches.
     */
    public SearchIndexStats getIndexStats() {
        if (elasticSearchService.isEnabled()) {
            return elasticSearchService.getIndexStats();
        }
        // the database engine searches the tables directly, so there is no index to report on
        var implementation = databaseSearchService.isEnabled() ? SearchIndexStats.DATABASE : SearchIndexStats.NONE;
        return new SearchIndexStats(
                isEnabled(),
                implementation,
                false,
                null,
                repositories.countActiveExtensions(),
                null);
    }

    public void updateSearchIndex(boolean clear) {
        getImplementation().updateSearchIndex(clear);
    }

    @Override
    public void updateSearchEntries(List<Extension> extensions) {
        getImplementation().updateSearchEntries(extensions);
    }

    @Override
    public void updateSearchEntriesAsync(List<Extension> extensions) {
        getImplementation().updateSearchEntriesAsync(extensions);
    }

    @Override
    public void updateSearchEntry(Extension extension) {
        getImplementation().updateSearchEntry(extension);
    }

    @Override
    public void removeSearchEntries(Collection<Long> ids) {
        getImplementation().removeSearchEntries(ids);
    }

    @Override
    public void removeSearchEntry(Extension extension) {
        getImplementation().removeSearchEntry(extension);
    }
}
