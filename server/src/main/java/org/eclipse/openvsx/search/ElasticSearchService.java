/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.search;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.util.ObjectBuilder;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.RelevanceService.SearchStats;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.cache.CacheService.CACHE_AVERAGE_REVIEW_RATING;

@Service
public class ElasticSearchService implements ISearchService {

    protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    protected final Logger logger = LoggerFactory.getLogger(ElasticSearchService.class);

    private final RepositoryService repositories;
    private final ElasticsearchOperations searchOperations;
    private final RelevanceService relevanceService;
    private final JobRequestScheduler scheduler;

    @Value("${ovsx.elasticsearch.enabled:true}")
    boolean enableSearch;
    @Value("${ovsx.elasticsearch.clear-on-start:false}")
    boolean clearOnStart;

    private long maxResultWindow;

    public ElasticSearchService(
            RepositoryService repositories,
            ElasticsearchOperations searchOperations,
            RelevanceService relevanceService,
            JobRequestScheduler scheduler
    ) {
        this.repositories = repositories;
        this.searchOperations = searchOperations;
        this.relevanceService = relevanceService;
        this.scheduler = scheduler;
    }

    public boolean isEnabled() {
        return enableSearch;
    }

    /**
     * Application start listener that initializes the search index. If the application property
     * {@code ovsx.elasticsearch.clear-on-start} is set to {@code true}, the index is cleared
     * and rebuilt from scratch. If the property is {@code false} and the search index does
     * not exist yet, it is created and initialized. Otherwise, nothing happens.
     */
    @EventListener
    @Retryable(retryFor = DataAccessResourceFailureException.class)
    @CacheEvict(value = CACHE_AVERAGE_REVIEW_RATING, allEntries = true)
    public void initSearchIndex(ApplicationStartedEvent event) {
        if (!isEnabled()) {
            scheduler.deleteRecurringJob("ElasticSearchUpdateIndex");
            return;
        }

        // schedule recurring job to update the search index
        scheduler.scheduleRecurrently(
                "ElasticSearchUpdateIndex",
                Cron.daily(4),
                ZoneId.of("UTC"),
                new HandlerJobRequest<>(ElasticSearchUpdateIndexJobRequestHandler.class)
        );

        if (clearOnStart || !searchOperations.indexOps(ExtensionSearch.class).exists()) {
            var stopWatch = new StopWatch();
            stopWatch.start();
            updateSearchIndex(clearOnStart);
            stopWatch.stop();
            logger.info("Initialized search index in {} ms", stopWatch.getTotalTimeMillis());
        }

        var settings = searchOperations.indexOps(ExtensionSearch.class).getSettings(true);
        maxResultWindow = Long.parseLong(settings.getOrDefault("index.max_result_window", "10000").toString());
    }

    /**
     * Soft-update the search index, because the relevance of index entries
     * consider the extension publishing timestamps in relation to the current
     * time or the extension rating.
     */
    @Retryable(retryFor = DataAccessResourceFailureException.class)
    @CacheEvict(value = CACHE_AVERAGE_REVIEW_RATING, allEntries = true)
    public void updateSearchIndex() {
        if (!isEnabled()) {
            return;
        }
        var stopWatch = new StopWatch();
        stopWatch.start();
        updateSearchIndex(false);
        stopWatch.stop();
        logger.info("Updated search index in {} ms", stopWatch.getTotalTimeMillis());
    }

    /**
     * Updating the search index has two modes:
     * <em>soft</em> ({@code clear} is set to {@code false}) means the index is created
     * if it does not exist yet, and
     * <em>hard</em> ({@code clear} is set to {@code true}) means the index is deleted
     * and then recreated.
     * In any case, this method scans all extensions in the database and indexes their
     * relevant metadata.
     */
    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void updateSearchIndex(boolean clear) {
        var locked = false;
        try {
            var indexOps = searchOperations.indexOps(ExtensionSearch.class);
            if (clear) {
                // Hard mode: delete the index if it exists, then recreate it
                rwLock.writeLock().lock();
                locked = true;
                if (indexOps.exists()) {
                    indexOps.delete();
                }
                indexOps.create();
            } else if (!indexOps.exists()) {
                // Soft mode: the index is created only when it does not exist yet
                rwLock.writeLock().lock();
                locked = true;
                indexOps.create();
            }
            
            // Scan all extensions and create index queries
            var allExtensions = repositories.findAllActiveExtensions();
            if (allExtensions.isEmpty()) {
                return;
            }
            var stats = new SearchStats(repositories);
            var indexQueries = allExtensions.map(extension ->
                new IndexQueryBuilder()
                    .withObject(relevanceService.toSearchEntry(extension, stats))
                    .build()
            ).toList();

            if (!locked) {
                // The write lock has not been acquired upfront, so do it just before submitting the index queries
                rwLock.writeLock().lock();
                locked = true;
            }
            searchOperations.bulkIndex(indexQueries, indexOps.getIndexCoordinates());
        } finally {
            if (locked) {
                rwLock.writeLock().unlock();
            }
        }
    }

    @Async
    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void updateSearchEntriesAsync(List<Extension> extensions) {
        updateSearchEntries(extensions);
    }

    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void updateSearchEntries(List<Extension> extensions) {
        if (!isEnabled() || extensions.isEmpty()) {
            return;
        }
        try {
            rwLock.writeLock().lock();
            var indexOps = searchOperations.indexOps(ExtensionSearch.class);
            var stats = new SearchStats(repositories);
            var indexQueries = extensions.stream().map(extension ->
                    new IndexQueryBuilder()
                            .withObject(relevanceService.toSearchEntry(extension, stats))
                            .build()
            ).collect(Collectors.toList());
            searchOperations.bulkIndex(indexQueries, indexOps.getIndexCoordinates());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void updateSearchEntry(Extension extension) {
        if (!isEnabled()) {
            return;
        }
        try {
            rwLock.writeLock().lock();
            var stats = new SearchStats(repositories);
            var indexQuery = new IndexQueryBuilder()
                    .withObject(relevanceService.toSearchEntry(extension, stats))
                    .build();
            var indexOps = searchOperations.indexOps(ExtensionSearch.class);
            searchOperations.index(indexQuery, indexOps.getIndexCoordinates());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void removeSearchEntries(Collection<Long> ids) {
        if (!isEnabled()) {
            return;
        }

        var queryBuilder = new NativeQueryBuilder();
        var query = queryBuilder.withQuery(builder -> builder.ids(idsBuilder -> idsBuilder.values(ids.stream().map(String::valueOf).collect(Collectors.toList())))).build();
        searchOperations.delete(DeleteQuery.builder(query).build(), ExtensionSearch.class);
    }


    @Retryable(retryFor = DataAccessResourceFailureException.class)
    public void removeSearchEntry(Extension extension) {
        if (!isEnabled()) {
            return;
        }
        try {
            rwLock.writeLock().lock();
            var indexOps = searchOperations.indexOps(ExtensionSearch.class);
            searchOperations.delete(Long.toString(extension.getId()), indexOps.getIndexCoordinates());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public SearchResult search(Options options) {
        var resultWindow = options.requestedOffset() + options.requestedSize();
        if (resultWindow > maxResultWindow) {
            return new SearchResult(0L, Collections.emptyList());
        }

        var queryBuilder = new NativeQueryBuilder();
        queryBuilder.withQuery(builder -> builder.bool(boolQuery -> createSearchQuery(boolQuery, options)));

        // Sort search results according to 'sortOrder' and 'sortBy' options
        sortResults(queryBuilder, options.sortOrder(), options.sortBy());

        var pages = new ArrayList<Pageable>();
        pages.add(PageRequest.of(options.requestedOffset() / options.requestedSize(), options.requestedSize()));
        if (options.requestedOffset() % options.requestedSize() > 0) {
            // size is not exact multiple of offset; this means we need to get two pages
            // e.g. when offset is 20 and size is 50, you want results 20 to 70 which span pages 0 and 1 of a 50 item page
            pages.add(pages.getFirst().next());
        }

        var searchHitsList = new ArrayList<SearchHits<ExtensionSearch>>(pages.size());
        for (var page : pages) {
            queryBuilder.withPageable(page);
            queryBuilder.withTrackTotalHits(true);
            try {
                rwLock.readLock().lock();
                var searchHits = searchOperations.search(queryBuilder.build(), ExtensionSearch.class, searchOperations.indexOps(ExtensionSearch.class).getIndexCoordinates());
                searchHitsList.add(searchHits);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        var firstSearchHitsPage = searchHitsList.get(0);
        List<SearchHit<ExtensionSearch>> searchHits = new ArrayList<>(firstSearchHitsPage.getSearchHits());
        if (searchHitsList.size() == 2) {
            var secondSearchHitsPage = searchHitsList.get(1);

            searchHits.addAll(secondSearchHitsPage.getSearchHits());
            var endIndex = Math.min(searchHits.size(), options.requestedOffset() + options.requestedSize());
            var startIndex = Math.min(endIndex, options.requestedOffset());
            searchHits = searchHits.subList(startIndex, endIndex);
        }

        var results = searchHits.stream().map(SearchHit::getContent).toList();
        return new SearchResult(firstSearchHitsPage.getTotalHits(), results);
    }

    private ObjectBuilder<BoolQuery> createSearchQuery(BoolQuery.Builder boolQuery, Options options) {
        if (!StringUtils.isEmpty(options.queryString())) {
            boolQuery.must(builder -> builder.bool(textBoolQuery -> createTextSearchQuery(textBoolQuery, options)));
        }

        if (!StringUtils.isEmpty(options.namespace())) {
            // Filter by namespace
            boolQuery.must(QueryBuilders.term(builder -> builder.field("namespace.keyword").value(options.namespace()).caseInsensitive(true)));
        }
        if (!StringUtils.isEmpty(options.category())) {
            // Filter by selected category
            boolQuery.must(QueryBuilders.matchPhrase(builder -> builder.field("categories").query(options.category())));
        }
        if (TargetPlatform.isValid(options.targetPlatform())) {
            // Filter by selected target platform
            boolQuery.must(QueryBuilders.matchPhrase(builder -> builder.field("targetPlatforms").query(options.targetPlatform())));
        }
        if (options.namespacesToExclude() != null) {
            // Exclude namespaces
            for(var namespaceToExclude : options.namespacesToExclude()) {
                boolQuery.mustNot(QueryBuilders.term(builder -> builder.field("namespace.keyword").value(namespaceToExclude)));
            }
        }

        return boolQuery;
    }

    private ObjectBuilder<BoolQuery> createTextSearchQuery(BoolQuery.Builder boolQuery, Options options) {
        boolQuery.should(QueryBuilders.term(builder ->
                builder.field("extensionId.keyword")
                        .value(options.queryString())
                        .caseInsensitive(true)
                        .boost(10f)
        ));

        // matching of search query in multiple fields with boost
        var multiMatchQuery = QueryBuilders.multiMatch(builder ->
                builder.query(options.queryString())
                        .fields("name").boost(5f)
                        .fields("displayName").boost(5f)
                        .fields("tags").boost(3f)
                        .fields("namespace").boost(2f)
                        .fields("description")
        );

        boolQuery.should(multiMatchQuery).boost(5f);

        // Fuzzy matching of search query in multiple fields without boost
        // Same as above except does not fuzzy match tags
        var fuzzyMultiMatchQuery = QueryBuilders.multiMatch(builder ->
                builder.query(options.queryString())
                        .fields("name")
                        .fields("displayName")
                        .fields("namespace")
                        .fields("description")
                        .fuzziness("AUTO")
                        .prefixLength(2)
        );

        boolQuery.should(fuzzyMultiMatchQuery);

        // Prefix matching of search query in display name and namespace
        var prefixString = options.queryString().trim().toLowerCase();
        var namePrefixQuery = QueryBuilders.prefix(builder -> builder.field("displayName").value(prefixString));
        boolQuery.should(namePrefixQuery).boost(2f);
        var namespacePrefixQuery = QueryBuilders.prefix(builder -> builder.field("namespace").value(prefixString));
        boolQuery.should(namespacePrefixQuery);

        return boolQuery;
    }

    private void sortResults(NativeQueryBuilder queryBuilder, String sortOrder, String sortBy) {
        sortOrder = sortOrder.toLowerCase();
        var orders = Map.of("asc", SortOrder.Asc, "desc", SortOrder.Desc);
        var order = orders.get(sortOrder);
        if (order == null) {
            throw new ErrorResultException("sortOrder parameter must be either 'asc' or 'desc'.");
        }

        var types = Map.of(
                SortBy.RELEVANCE, FieldType.Float,
                SortBy.RATING, FieldType.Float,
                SortBy.TIMESTAMP, FieldType.Long,
                SortBy.DOWNLOADS, FieldType.Integer
        );

        var type = types.get(sortBy);
        if (type == null) {
            throw new ErrorResultException("sortBy parameter must be " + SortBy.OPTIONS + ".");
        }

        var scoreSort = new SortOptions.Builder().score(builder -> builder.order(order)).build();
        var fieldSort = new SortOptions.Builder().field(builder -> builder.field(sortBy).unmappedType(type).order(order)).build();
        var sortOptions = sortBy.equals(SortBy.RELEVANCE) ? List.of(scoreSort, fieldSort) : List.of(fieldSort, scoreSort);
        queryBuilder.withSort(sortOptions);
    }
}
