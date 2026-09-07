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

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.util.ObjectBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.jspecify.annotations.Nullable;
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
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.RelevanceService.SearchStats;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TargetPlatform;

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

    /** Elasticsearch's own default, and what {@link #initSearchIndex} falls back to when the index is silent. */
    private static final long DEFAULT_MAX_RESULT_WINDOW = 10_000;

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
    @Retryable(includes = DataAccessResourceFailureException.class)
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
                new HandlerJobRequest<>(ElasticSearchUpdateIndexJobRequestHandler.class));

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
    @Retryable(includes = DataAccessResourceFailureException.class)
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
    @Retryable(includes = DataAccessResourceFailureException.class)
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
            var indexQueries = allExtensions
                    .map(extension -> buildIndexQuery(extension, stats))
                    .filter(Objects::nonNull)
                    .toList();

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

    /**
     * Reports on the index itself rather than on what a search returns. The document count next to the
     * number of extensions the index is built from is the useful part: the two drifting apart is how a
     * half-populated or partly purged index shows itself, which searching for something and not finding
     * it does not distinguish from the extension simply not existing.
     */
    public SearchIndexStats getIndexStats() {
        var activeExtensions = repositories.countActiveExtensions();
        if (!isEnabled()) {
            return new SearchIndexStats(false, SearchIndexStats.ELASTICSEARCH, false, null, activeExtensions, null);
        }

        var indexOps = searchOperations.indexOps(ExtensionSearch.class);
        if (!indexOps.exists()) {
            return new SearchIndexStats(true, SearchIndexStats.ELASTICSEARCH, false, null, activeExtensions, null);
        }

        var documents = searchOperations.count(Query.findAll(), ExtensionSearch.class);
        return new SearchIndexStats(
                true,
                SearchIndexStats.ELASTICSEARCH,
                true,
                documents,
                activeExtensions,
                getMaxResultWindow());
    }

    @Async
    @Retryable(includes = DataAccessResourceFailureException.class)
    public void updateSearchEntriesAsync(List<Extension> extensions) {
        updateSearchEntries(extensions);
    }

    @Retryable(includes = DataAccessResourceFailureException.class)
    public void updateSearchEntries(List<Extension> extensions) {
        if (!isEnabled() || extensions.isEmpty()) {
            return;
        }
        try {
            rwLock.writeLock().lock();
            var indexOps = searchOperations.indexOps(ExtensionSearch.class);
            var stats = new SearchStats(repositories);
            var indexQueries = extensions.stream()
                    .map(extension -> buildIndexQuery(extension, stats))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            searchOperations.bulkIndex(indexQueries, indexOps.getIndexCoordinates());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Retryable(includes = DataAccessResourceFailureException.class)
    public void updateSearchEntry(Extension extension) {
        if (!isEnabled()) {
            return;
        }

        try {
            rwLock.writeLock().lock();
            var stats = new SearchStats(repositories);
            var indexQuery = buildIndexQuery(extension, stats);
            if (indexQuery != null) {
                var indexOps = searchOperations.indexOps(ExtensionSearch.class);
                searchOperations.index(indexQuery, indexOps.getIndexCoordinates());
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private @Nullable IndexQuery buildIndexQuery(Extension extension, SearchStats stats) {
        var searchEntry = relevanceService.toSearchEntry(extension, stats);
        if (searchEntry != null) {
            return new IndexQueryBuilder()
                    .withObject(searchEntry)
                    .build();
        } else {
            logger.warn(
                    "Trying to update search entry for inactive extension '{}'",
                    NamingUtil.toExtensionId(extension));
            return null;
        }
    }

    @Retryable(includes = DataAccessResourceFailureException.class)
    public void removeSearchEntries(Collection<Long> ids) {
        if (!isEnabled()) {
            return;
        }

        var queryBuilder = new NativeQueryBuilder();
        var query = queryBuilder
                .withQuery(
                        builder -> builder.ids(
                                idsBuilder -> idsBuilder
                                        .values(ids.stream().map(String::valueOf).collect(Collectors.toList()))))
                .build();
        searchOperations.delete(DeleteQuery.builder(query).build(), ExtensionSearch.class);
    }

    @Retryable(includes = DataAccessResourceFailureException.class)
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

    /**
     * The same search the registry answers, with the scores kept.
     * <p>
     * {@link #search} throws them away - a caller wanting results does not care - but they are the whole
     * point when the question is why a result sits where it does. Built through the same
     * {@code createQuery} and {@code sortResults} as the real search rather than a copy of them, because a
     * debugging view assembled separately is a view of a query nobody runs, and the first thing it would
     * hide is the two having drifted apart.
     * <p>
     * One page only: this answers an admin looking at a result list, not a client paging through one.
     */
    public SearchHits<ExtensionSearch> searchWithScores(Options options) {
        // The same ceiling the real search enforces. Without it a deep enough offset reaches Elasticsearch
        // and comes back as an engine error about the result window, which says nothing about what to do.
        var resultWindow = options.requestedOffset() + options.requestedSize();
        if (resultWindow > getMaxResultWindow()) {
            throw new ErrorResultException(
                    "Cannot look past result " + getMaxResultWindow() + "; the index will not serve a deeper window.");
        }

        var queryBuilder = new NativeQueryBuilder();
        createQuery(queryBuilder, options);
        sortResults(queryBuilder, options.sortOrder(), options.sortBy());
        // Whole pages only, which is all the one caller asks for - a partial page would need the
        // two-page dance search() does, for an offset nothing produces.
        queryBuilder.withPageable(
                PageRequest.of(options.requestedOffset() / options.requestedSize(), options.requestedSize()));
        queryBuilder.withTrackTotalHits(true);
        // Elasticsearch's own account of how it arrived at each score, which is the only way to see what
        // the text half is made of - the clause that matched, and what it was worth. It makes the search
        // materially more expensive, which is why only this caller asks for it.
        queryBuilder.withExplain(true);

        try {
            rwLock.readLock().lock();
            return searchOperations.search(
                    queryBuilder.build(),
                    ExtensionSearch.class,
                    searchOperations.indexOps(ExtensionSearch.class).getIndexCoordinates());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * The deepest window the index will serve.
     * <p>
     * The field behind this is read from the index settings by {@link #initSearchIndex}, which runs on
     * {@code ApplicationStartedEvent} - so until that has happened, or if it failed short of reading them,
     * the field is zero. Taken literally that refuses every window there is, and since a refused window is
     * an empty result rather than an error, an instance in that state answers every search with nothing
     * and says nothing about why. Fall back to the engine's own default instead, which is what the index
     * would almost certainly have said.
     */
    private long getMaxResultWindow() {
        return maxResultWindow > 0 ? maxResultWindow : DEFAULT_MAX_RESULT_WINDOW;
    }

    public SearchResult search(Options options) {
        var resultWindow = options.requestedOffset() + options.requestedSize();
        if (resultWindow > getMaxResultWindow()) {
            return new SearchResult(0L, Collections.emptyList());
        }

        var queryBuilder = new NativeQueryBuilder();
        createQuery(queryBuilder, options);

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
                var searchHits = searchOperations.search(
                        queryBuilder.build(),
                        ExtensionSearch.class,
                        searchOperations.indexOps(ExtensionSearch.class).getIndexCoordinates());
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
            boolQuery.must(
                    QueryBuilders.term(
                            builder -> builder.field("namespace.keyword").value(options.namespace())
                                    .caseInsensitive(true)));
        }
        if (!StringUtils.isEmpty(options.category())) {
            // Filter by selected category
            boolQuery.must(QueryBuilders.matchPhrase(builder -> builder.field("categories").query(options.category())));
        }
        if (TargetPlatform.isValid(options.targetPlatform())) {
            // Filter by selected target platform
            boolQuery.must(
                    QueryBuilders
                            .matchPhrase(builder -> builder.field("targetPlatforms").query(options.targetPlatform())));
        }
        if (options.namespacesToExclude() != null) {
            // Exclude namespaces
            for (var namespaceToExclude : options.namespacesToExclude()) {
                boolQuery.mustNot(
                        QueryBuilders.term(builder -> builder.field("namespace.keyword").value(namespaceToExclude)));
            }
        }

        return boolQuery;
    }

    private ObjectBuilder<BoolQuery> createTextSearchQuery(BoolQuery.Builder boolQuery, Options options) {
        boolQuery.should(
                QueryBuilders.term(
                        builder -> builder.field("extensionId.keyword")
                                .value(options.queryString())
                                .caseInsensitive(true)
                                .boost(10f)));

        // Matching of the search query in multiple fields, weighted so that what an extension is called
        // counts for more than what it says about itself.
        //
        // The weights belong in the field names. `boost` on a multi_match builder is the boost of the
        // whole query - there is one of them, inherited from QueryBase - so a chain of `.fields(x)
        // .boost(n)` calls does not weight x by n; each call overwrites the previous query boost and the
        // fields end up weighted equally. Which is how a match in `description` came to count for as much
        // as a match in `name`.
        var multiMatchQuery = QueryBuilders.multiMatch(
                builder -> builder.query(options.queryString())
                        .fields("name^5", "displayName^5", "tags^3", "namespace^2", "description")
                        .boost(5f));

        boolQuery.should(multiMatchQuery);

        // Fuzzy matching of search query in multiple fields without boost
        // Same as above except does not fuzzy match tags
        var fuzzyMultiMatchQuery = QueryBuilders.multiMatch(
                builder -> builder.query(options.queryString())
                        .fields("name", "displayName", "namespace", "description")
                        .fuzziness("AUTO")
                        .prefixLength(2));

        boolQuery.should(fuzzyMultiMatchQuery);

        // Prefix matching of search query in display name and namespace.
        //
        // On the clause and not on the bool query, for the same reason as above: `boolQuery.should(q)`
        // returns the bool builder, so `.boost(n)` after it set the boost of the bool - once per call,
        // each overwriting the last. Every clause in here therefore scored alike, and the surviving
        // boost scaled the whole text query uniformly, which changes no ordering at all.
        var prefixString = options.queryString().trim().toLowerCase();
        var namePrefixQuery = QueryBuilders
                .prefix(builder -> builder.field("displayName").value(prefixString).boost(2f));
        boolQuery.should(namePrefixQuery);
        var namespacePrefixQuery = QueryBuilders.prefix(builder -> builder.field("namespace").value(prefixString));
        boolQuery.should(namespacePrefixQuery);

        return boolQuery;
    }

    private void createQuery(NativeQueryBuilder queryBuilder, Options options) {
        if (SortBy.RELEVANCE.equals(options.sortBy())) {
            queryBuilder.withQuery(
                    builder -> builder.functionScore(
                            scoreQuery -> scoreQuery.query(
                                    sortQueryBuilder -> sortQueryBuilder
                                            .bool(boolQuery -> createSearchQuery(boolQuery, options)))
                                    .functions(
                                            functionBuilder -> functionBuilder.fieldValueFactor(
                                                    factor -> factor.field("relevance").factor(1.0)))));
        } else {
            queryBuilder.withQuery(builder -> builder.bool(boolQuery -> createSearchQuery(boolQuery, options)));
        }
    }

    private void sortResults(NativeQueryBuilder queryBuilder, String sortOrder, String sortBy) {
        sortOrder = sortOrder.toLowerCase();
        var orders = Map.of("asc", SortOrder.Asc, "desc", SortOrder.Desc);
        var order = orders.get(sortOrder);
        if (order == null) {
            throw new ErrorResultException("sortOrder parameter must be either 'asc' or 'desc'.");
        }

        var types = Map.of(
                SortBy.RELEVANCE,
                FieldType.Float,
                SortBy.RATING,
                FieldType.Float,
                SortBy.TIMESTAMP,
                FieldType.Long,
                SortBy.DOWNLOADS,
                FieldType.Integer);

        var type = types.get(sortBy);
        if (type == null) {
            throw new ErrorResultException("sortBy parameter must be " + SortBy.OPTIONS + ".");
        }

        var scoreSort = new SortOptions.Builder().score(builder -> builder.order(order)).build();
        var fieldSort = new SortOptions.Builder()
                .field(builder -> builder.field(sortBy).unmappedType(type).order(order)).build();
        var sortOptions = sortBy.equals(SortBy.RELEVANCE) ? List.of(scoreSort) : List.of(fieldSort, scoreSort);
        queryBuilder.withSort(sortOptions);
    }
}
