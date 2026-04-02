/********************************************************************************
 * Copyright (c) 2021 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.openvsx.search;

import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.RelevanceService.SearchStats;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.util.Streamable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.eclipse.openvsx.cache.CacheService.CACHE_AVERAGE_REVIEW_RATING;
import static org.eclipse.openvsx.cache.CacheService.CACHE_DATABASE_SEARCH;

/**
 * Alternative to ElasticSearch service using database search.
 */
@Component
public class DatabaseSearchService implements ISearchService {

    private final RelevanceService relevanceService;
    private final RepositoryService repositories;

    @Value("${ovsx.databasesearch.enabled:false}")
    boolean enableSearch;

    public DatabaseSearchService(RelevanceService relevanceService, RepositoryService repositories) {
        this.relevanceService = relevanceService;
        this.repositories = repositories;
    }

    public boolean isEnabled() {
        return enableSearch;
    }

    @Transactional
    @Cacheable(CACHE_DATABASE_SEARCH)
    @CacheEvict(value = CACHE_AVERAGE_REVIEW_RATING, allEntries = true)
    public SearchResult search(ISearchService.Options options) {
        var matchingExtensions = repositories.findAllActiveExtensions();
        matchingExtensions = includeByNamespace(options, matchingExtensions);
        matchingExtensions = excludeByNamespace(options, matchingExtensions);
        matchingExtensions = excludeByTargetPlatform(options, matchingExtensions);
        matchingExtensions = excludeByCategory(options, matchingExtensions);
        matchingExtensions = excludeByQueryString(options, matchingExtensions);
        matchingExtensions = filterByWebOnly(options, matchingExtensions);

        var sortedExtensions = sortExtensions(options, matchingExtensions);
        var totalHits = sortedExtensions.size();
        sortedExtensions = applyPaging(options, sortedExtensions);
        return new SearchResult(totalHits, sortedExtensions);
    }

    private List<ExtensionSearch> applyPaging(Options options, List<ExtensionSearch> sortedExtensions) {
        var endIndex = Math.min(sortedExtensions.size(), options.requestedOffset() + options.requestedSize());
        var startIndex = Math.min(endIndex, options.requestedOffset());
        return sortedExtensions.subList(startIndex, endIndex);
    }

    private List<ExtensionSearch> sortExtensions(Options options, Streamable<Extension> matchingExtensions) {
        Stream<ExtensionSearch> searchEntries;
        if(SortBy.RELEVANCE.equals(options.sortBy()) || SortBy.RATING.equals(options.sortBy())) {
            var searchStats = new SearchStats(repositories);
            searchEntries = matchingExtensions.stream().map(extension -> relevanceService.toSearchEntry(extension, searchStats));
        } else {
            searchEntries = matchingExtensions.stream().map(extension -> {
                var latest = repositories.findLatestVersion(extension, null, false, true);
                var targetPlatforms = repositories.findExtensionTargetPlatforms(extension);
                return extension.toSearch(latest, targetPlatforms);
            });
        }

        var comparators = Map.of(
                SortBy.RELEVANCE, new RelevanceComparator(),
                SortBy.TIMESTAMP, new TimestampComparator(),
                SortBy.RATING, new RatingComparator(),
                SortBy.DOWNLOADS, new DownloadedCountComparator()
        );

        var comparator = comparators.get(options.sortBy());
        if(comparator == null) {
            throw new ErrorResultException("sortBy parameter must be " + SortBy.OPTIONS + ".");
        }
        if ("desc".equals(options.sortOrder())) {
            comparator = comparator.reversed();
        }

        return searchEntries.sorted(comparator).toList();
    }

    private Streamable<Extension> excludeByQueryString(Options options, Streamable<Extension> matchingExtensions) {
        if(options.queryString() == null) {
            return matchingExtensions;
        }

        return matchingExtensions.filter(extension -> {
            var latest = repositories.findLatestVersion(extension, null, false, true);
            return extension.getName().toLowerCase().contains(options.queryString().toLowerCase())
                    || extension.getNamespace().getName().contains(options.queryString().toLowerCase())
                    || (latest.getDescription() != null && latest.getDescription()
                    .toLowerCase().contains(options.queryString().toLowerCase()))
                    || (latest.getDisplayName() != null && latest.getDisplayName()
                    .toLowerCase().contains(options.queryString().toLowerCase()));
        });
    }

    private Streamable<Extension> excludeByCategory(Options options, Streamable<Extension> matchingExtensions) {
        if(options.category() == null) {
            return matchingExtensions;
        }

        return matchingExtensions.filter(extension -> {
            var latest = repositories.findLatestVersion(extension, null, false, true);
            return latest.getCategories().stream().anyMatch(category -> category.equalsIgnoreCase(options.category()));
        });
    }

    private Streamable<Extension> excludeByTargetPlatform(Options options, Streamable<Extension> matchingExtensions) {
        if(!TargetPlatform.isValid(options.targetPlatform())) {
            return matchingExtensions;
        }

        return matchingExtensions.filter(extension -> extension.getVersions().stream().anyMatch(ev -> ev.getTargetPlatform().equals(options.targetPlatform())));
    }

    private Streamable<Extension> includeByNamespace(Options options, Streamable<Extension> matchingExtensions) {
        if(options.namespace() == null) {
            return matchingExtensions;
        }

        return matchingExtensions.filter(extension -> extension.getNamespace().getName().equalsIgnoreCase(options.namespace()));
    }

    private Streamable<Extension> excludeByNamespace(Options options, Streamable<Extension> matchingExtensions) {
        if(options.namespacesToExclude() == null) {
            return matchingExtensions;
        }

        var namespacesToExclude = List.of(options.namespacesToExclude());
        return matchingExtensions.filter(extension -> !namespacesToExclude.contains(extension.getNamespace().getName()));
    }

    private Streamable<Extension> filterByWebOnly(Options options, Streamable<Extension> matchingExtensions) {
        if(options.webOnly() == null || !options.webOnly()) {
            return matchingExtensions;
        }

        return matchingExtensions.filter(extension -> {
            var latest = repositories.findLatestVersion(extension, null, false, true);
            return latest.getExtensionKind() != null && latest.getExtensionKind().contains("web");
        });
    }

    @Override
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void updateSearchIndex(boolean clear) {
        // The @CacheEvict annotation clears the cache when asked to update the search index
    }

    @Override
    @Async
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void updateSearchEntriesAsync(List<Extension> extensions) {
        // The @CacheEvict annotation clears the cache when asked to update search entries
    }

    @Override
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void updateSearchEntries(List<Extension> extensions) {
        // The @CacheEvict annotation clears the cache when asked to update search entries
    }

    @Override
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void updateSearchEntry(Extension extension) {
        // The @CacheEvict annotation clears the cache when asked to update a search entry
    }

    @Override
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void removeSearchEntries(Collection<Long> ids) {
        // The @CacheEvict annotation clears the cache when asked to removes search entries
    }

    @Override
    @CacheEvict(value = CACHE_DATABASE_SEARCH, allEntries = true)
    public void removeSearchEntry(Extension extension) {
        // The @CacheEvict annotation clears the cache when asked to remove a search entry
    }

    /**
     * Sort by download count
     */
    class DownloadedCountComparator implements Comparator<ExtensionSearch> {

        @Override
        public int compare(ExtensionSearch ext1, ExtensionSearch ext2) {
            // download count
            return Integer.compare(ext1.getDownloadCount(), ext2.getDownloadCount());
        }
    }

    /**
     * Sort by averageRating
     */
    class RatingComparator implements Comparator<ExtensionSearch> {

        @Override
        public int compare(ExtensionSearch ext1, ExtensionSearch ext2) {
            if (ext1.getRating() == null) {
                return -1;
            } else if (ext2.getRating() == null) {
                return 1;
            }
            // rating
            return Double.compare(ext1.getRating(), ext2.getRating());
        }
    }

    /**
     * Sort by timestamp
     */
    class TimestampComparator implements Comparator<ExtensionSearch> {

        @Override
        public int compare(ExtensionSearch ext1, ExtensionSearch ext2) {
            // timestamp
            return Long.compare(ext1.getTimestamp(), ext2.getTimestamp());
        }
    }

    /**
     * Sort by relevance
     */
    class RelevanceComparator implements Comparator<ExtensionSearch> {

        @Override
        public int compare(ExtensionSearch ext1, ExtensionSearch ext2) {
            return Double.compare(ext1.getRelevance(), ext2.getRelevance());
        }
    }
}
