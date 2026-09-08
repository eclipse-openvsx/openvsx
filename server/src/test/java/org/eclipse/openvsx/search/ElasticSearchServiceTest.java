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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.util.Streamable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.cache.LatestExtensionVersionCacheKeyGenerator;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(SpringExtension.class)
@MockitoBean(types = { JobRequestScheduler.class })
class ElasticSearchServiceTest {

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    ElasticsearchOperations searchOperations;

    @Autowired
    ElasticSearchService search;

    /**
     * What the text query actually asks Elasticsearch for.
     * <p>
     * Asserted on the serialized query because the bug it guards against is invisible in the calling
     * code: `boost` on a multi_match builder is the boost of the whole query rather than of the field
     * named before it, and `boolQuery.should(q).boost(n)` boosts the bool and not the clause. Both read
     * as per-field and per-clause weighting and were neither, so every field and every clause scored
     * alike - and a query only tells you which by being looked at.
     */
    @Test
    void weightsTheNameAboveTheDescriptionInTheTextQuery() {
        var query = capturedQueryFor("markdown");

        // The weights ride in the field names; anything else is not a weight.
        assertThat(query).contains("name^5", "displayName^5", "tags^3", "namespace^2", "description");
        assertThat(query).doesNotContain("\"fields\":[\"name\"],");
    }

    /**
     * An exact {@code namespace.name} gets its own heavily boosted clause, matched on the two fields that
     * hold the parts. The {@code extensionId} field this replaces is mapped {@code index = false} and has
     * no {@code .keyword} sub-field, so the term query that looked for one matched nothing at all.
     */
    @Test
    void matchesAnExactExtensionIdOnTheFieldsThatHoldIt() {
        var query = capturedQueryFor("yzhang.markdown-all-in-one");

        assertThat(query).contains("\"namespace.keyword\":{\"value\":\"yzhang\"");
        assertThat(query).contains("\"name.keyword\":{\"value\":\"markdown-all-in-one\"");
        assertThat(query).contains("\"boost\":10.0");
        // The field it used to look for cannot be matched, so nothing should be asking for it.
        assertThat(query).doesNotContain("extensionId");
    }

    // Both halves have to match the same document, or "yzhang.anything" would pull in every extension in
    // the namespace at a boost of ten.
    @Test
    void requiresBothHalvesOfAnExtensionIdToMatch() {
        var query = capturedQueryFor("yzhang.markdown-all-in-one");

        assertThat(query).contains("\"must\":[{\"term\":{\"namespace.keyword\"");
        assertThat(query).doesNotContain("\"should\":[{\"term\":{\"namespace.keyword\"");
    }

    // A plain word is not an extension id, and a clause looking for one would only cost a lookup.
    @Test
    void addsNoExtensionIdClauseForAQueryThatIsNotOne() {
        assertThat(capturedQueryFor("markdown")).doesNotContain("namespace.keyword");
        // Nor for the shapes that split on a dot without naming both halves.
        assertThat(capturedQueryFor("yzhang.")).doesNotContain("namespace.keyword");
        assertThat(capturedQueryFor(".markdown")).doesNotContain("namespace.keyword");
        assertThat(capturedQueryFor("a.b.c")).doesNotContain("namespace.keyword");
    }

    // The exact-phrase multi_match is meant to outscore the fuzzy one, which is a statement about that
    // clause and so has to sit on it.
    @Test
    void boostsTheExactMatchAboveTheFuzzyOne() {
        var query = capturedQueryFor("markdown");
        var multiMatches = query.split("\"multi_match\"", -1).length - 1;

        assertThat(multiMatches).isEqualTo(2);
        assertThat(query).contains("\"boost\":5.0");
        // The fuzzy clause is deliberately unboosted, so exactly one of the two carries the boost.
        assertThat(query.split("\"boost\":5.0", -1).length - 1).isEqualTo(1);
    }

    private String capturedQueryFor(String queryString) {
        var indexOps = Mockito.mock(IndexOperations.class);
        Mockito.when(searchOperations.indexOps(ExtensionSearch.class)).thenReturn(indexOps);
        Mockito.when(indexOps.getIndexCoordinates()).thenReturn(IndexCoordinates.of("extensions"));

        SearchHits<ExtensionSearch> empty = new SearchHitsImpl<>(
                0L,
                TotalHitsRelation.EQUAL_TO,
                0f,
                Duration.ZERO,
                null,
                null,
                List.of(),
                null,
                null,
                null);
        var captor = ArgumentCaptor.forClass(NativeQuery.class);
        Mockito.when(searchOperations.search(captor.capture(), Mockito.eq(ExtensionSearch.class), any()))
                .thenReturn(empty);

        withMaxResultWindow(
                10_000L,
                () -> search.search(
                        new ISearchService.Options(queryString, null, null, 10, 0, "desc", "relevance", false, null)));

        return captor.getValue().getQuery().toString();
    }

    /**
     * Runs {@code body} with the result-window ceiling set, and puts back whatever was there before.
     * <p>
     * The field is only populated from the index settings during {@code initSearchIndex}, which no test
     * goes through, so it sits at zero unless a test says otherwise - and at zero every requested window
     * exceeds it and {@code search} returns before it builds a query at all. Leaving a value behind would
     * decide, by test ordering alone, whether {@link #testSearchResultWindowTooLarge()} exercises its
     * boundary or passes because everything exceeds a ceiling of nothing. The Spring context is shared,
     * so nothing else would put it back.
     */
    private void withMaxResultWindow(long window, Runnable body) {
        var previous = ReflectionTestUtils.getField(search, "maxResultWindow");
        ReflectionTestUtils.setField(search, "maxResultWindow", window);
        try {
            body.run();
        } finally {
            ReflectionTestUtils.setField(search, "maxResultWindow", previous);
        }
    }

    @Test
    void testRelevanceAverageRating() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n1", "u1", 3.0, 100, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        var ext2 = mockExtension("bar", "n2", "u2", 4.0, 100, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    @Test
    void testRelevanceReviewCount() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n1", "u1", 4.0, 2, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        var ext2 = mockExtension("bar", "n2", "u2", 4.0, 100, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    @Test
    void testRelevanceDownloadCount() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n1", "u1", 0.0, 0, 1, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        var ext2 = mockExtension("bar", "n2", "u2", 0.0, 0, 10, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    /**
     * What the download term is worth, rather than only that more downloads beat fewer.
     * <p>
     * Isolated so that relevance equals this one term: the oldest timestamp in the registry zeroes the
     * recency term, and the rating term is zeroed by the registry's average review rating rather than by
     * the extension's own lack of one - the formula smooths a rating towards that average, so an
     * extension with no reviews scores the average, not nothing. On a linear scale a hundred thousand
     * downloads against a registry maximum of a million is 0.1 - next to nothing beside a rating or a
     * recent release, and the reason the results in EclipseFdn/open-vsx.org#13014 bore no relation to
     * how popular anything was. Logarithmically it is 0.83.
     */
    @Test
    void weighsDownloadsOnALogScale() {
        var index = mockIndex(true);
        // After mockIndex, which stubs a maximum of its own.
        Mockito.when(repositories.getMaxExtensionDownloadCount()).thenReturn(1_000_000);
        // Stated rather than left to the mock's default, since it is what holds the rating term at zero.
        Mockito.when(repositories.getAverageReviewRating()).thenReturn(0.0);
        var oldest = LocalDateTime.parse("2020-01-01T00:00");
        var extension = mockExtension("foo", "n1", "u1", 0.0, 0, 100_000, oldest, false, false);

        search.updateSearchEntry(extension);

        var expected = Math.log1p(100_000) / Math.log1p(1_000_000);
        assertThat(index.entries).hasSize(1);
        assertThat(index.entries.getFirst().getRelevance()).isCloseTo(expected, within(0.001));
        // And the linear scale it replaces, so this fails rather than drifts if that comes back.
        assertThat(index.entries.getFirst().getRelevance()).isGreaterThan(0.5);
    }

    @Test
    void testRelevanceTimestamp() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n2", "u2", 0.0, 0, 0, LocalDateTime.parse("2020-02-01T00:00"), false, false);
        var ext2 = mockExtension("bar", "n1", "u1", 0.0, 0, 0, LocalDateTime.parse("2020-10-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    @Test
    void testRelevanceUnverified1() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n1", "u1", 4.0, 10, 10, LocalDateTime.parse("2020-10-01T00:00"), false, true);
        var ext2 = mockExtension("bar", "n2", "u2", 4.0, 10, 10, LocalDateTime.parse("2020-10-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    @Test
    void testRelevanceUnverified2() {
        var index = mockIndex(true);
        var ext1 = mockExtension("foo", "n1", "u1", 4.0, 10, 10, LocalDateTime.parse("2020-10-01T00:00"), true, false);
        var ext2 = mockExtension("bar", "n2", "u2", 4.0, 10, 10, LocalDateTime.parse("2020-10-01T00:00"), false, false);
        search.updateSearchEntry(ext1);
        search.updateSearchEntry(ext2);

        assertThat(index.entries).hasSize(2);
        assertThat(index.entries.get(0).getRelevance()).isLessThan(index.entries.get(1).getRelevance());
    }

    @Test
    void testSoftUpdateExists() {
        var index = mockIndex(true);
        mockExtensions();
        search.updateSearchIndex(false);

        assertThat(index.created).isFalse();
        assertThat(index.deleted).isFalse();
        assertThat(index.entries).hasSize(3);
    }

    @Test
    void testSoftUpdateNotExists() {
        var index = mockIndex(false);
        mockExtensions();
        search.updateSearchIndex(false);

        assertThat(index.created).isTrue();
        assertThat(index.deleted).isFalse();
        assertThat(index.entries).hasSize(3);
    }

    @Test
    void testHardUpdateExists() {
        var index = mockIndex(true);
        mockExtensions();
        search.updateSearchIndex(true);

        assertThat(index.created).isTrue();
        assertThat(index.deleted).isTrue();
        assertThat(index.entries).hasSize(3);
    }

    @Test
    void testHardUpdateNotExists() {
        var index = mockIndex(false);
        mockExtensions();
        search.updateSearchIndex(true);

        assertThat(index.created).isTrue();
        assertThat(index.deleted).isFalse();
        assertThat(index.entries).hasSize(3);
    }

    /**
     * The window is read from the index settings at startup, so before that has happened it is zero -
     * and a zero taken literally refuses every window there is. Since a refused window is an empty result
     * rather than an error, an instance in that state answers every search with nothing and says nothing
     * about why.
     */
    @Test
    void searchesAnOrdinaryWindowBeforeTheIndexSettingsHaveBeenRead() {
        mockIndex(true);
        SearchHits<ExtensionSearch> empty = new SearchHitsImpl<>(
                0L,
                TotalHitsRelation.EQUAL_TO,
                0f,
                Duration.ZERO,
                null,
                null,
                List.of(),
                null,
                null,
                null);
        Mockito.when(searchOperations.search(any(NativeQuery.class), Mockito.eq(ExtensionSearch.class), any()))
                .thenReturn(empty);

        var options = new ISearchService.Options("foo", null, null, 50, 0, "desc", "relevance", false, null);
        search.search(options);

        // Reaching the engine at all is the assertion. With the window at its uninitialised zero, every
        // window exceeded it and this returned an empty result without ever searching for anything.
        Mockito.verify(searchOperations)
                .search(any(NativeQuery.class), Mockito.eq(ExtensionSearch.class), any());
    }

    @Test
    void testSearchResultWindowTooLarge() {
        mockIndex(true);

        // Set explicitly, so this asserts the ceiling being exceeded rather than the field's untouched
        // zero, against which every window is too large and the check under test never has to work.
        var options = new ISearchService.Options("foo", "bar", "universal", 50, 10000, null, null, false, null);
        var searchHits = new SearchResult[1];
        withMaxResultWindow(10_000L, () -> searchHits[0] = search.search(options));

        assertThat(searchHits[0].getHits()).isEmpty();
        assertThat(searchHits[0].getTotalHits()).isZero();
    }

    //---------- UTILITY ----------//

    private void mockStats() {
        Mockito.when(repositories.getMaxExtensionDownloadCount())
                .thenReturn(10);
        Mockito.when(repositories.getOldestExtensionTimestamp())
                .thenReturn(LocalDateTime.parse("2020-01-01T00:00"));
    }

    @SuppressWarnings("unchecked")
    private MockIndex mockIndex(boolean exists) {
        mockStats();

        var index = new MockIndex();
        Mockito.when(searchOperations.index(any(IndexQuery.class), any(IndexCoordinates.class)))
                .then(invocation -> {
                    var query = invocation.getArgument(0, IndexQuery.class);
                    index.entries.add((ExtensionSearch) query.getObject());
                    return "test";
                });
        Mockito.doAnswer(invocation -> {
            var queries = (List<IndexQuery>) invocation.getArgument(0);
            queries.forEach(query -> index.entries.add((ExtensionSearch) query.getObject()));
            return null;
        }).when(searchOperations).bulkIndex(any(List.class), any(IndexCoordinates.class));

        var indexOps = Mockito.mock(IndexOperations.class);
        Mockito.when(searchOperations.indexOps(ExtensionSearch.class))
                .thenReturn(indexOps);
        Mockito.when(indexOps.getIndexCoordinates())
                .thenReturn(IndexCoordinates.of("extensions"));

        Mockito.when(indexOps.getSettings(true))
                .thenReturn(new Settings(Map.of("index.max_result_window", "10000")));

        Mockito.when(indexOps.exists())
                .thenReturn(exists);
        Mockito.when(indexOps.delete())
                .then(invocation -> {
                    if (!exists && !index.created) {
                        throw new IllegalStateException("Index does not exist.");
                    }
                    return index.deleted = true;
                });
        Mockito.when(indexOps.create())
                .then(invocation -> {
                    if (exists && !index.deleted) {
                        throw new IllegalStateException("Index already exists.");
                    }
                    return index.created = true;
                });
        return index;
    }

    private Extension mockExtension(
            String name,
            String namespaceName,
            String userName,
            double averageRating,
            long ratingCount,
            int downloadCount,
            LocalDateTime timestamp,
            boolean isUnverified,
            boolean isUnrelated
    ) {
        var extension = new Extension();
        extension.setName(name);
        extension.setId(name.hashCode());
        extension.setAverageRating(averageRating);
        extension.setReviewCount(ratingCount);
        extension.setDownloadCount(downloadCount);
        Mockito.when(entityManager.merge(extension)).thenReturn(extension);

        var namespace = new Namespace();
        namespace.setName(namespaceName);
        extension.setNamespace(namespace);
        var extVer = new ExtensionVersion();
        extVer.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVer.setTimestamp(timestamp);
        extVer.setActive(true);
        extVer.setExtension(extension);
        extension.getVersions().add(extVer);
        var user = new UserData();
        user.setLoginName(userName);
        extVer.setPublishedBy(user);
        Mockito.when(repositories.findLatestVersion(extension, null, false, true))
                .thenReturn(extVer);
        Mockito.when(repositories.isVerifiedPublisher(extVer))
                .thenReturn(!isUnverified && !isUnrelated);
        return extension;
    }

    private void mockExtensions() {
        var ext1 = mockExtension("foo", "n1", "u1", 3.0, 1, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        var ext2 = mockExtension("bar", "n2", "u2", 3.0, 1, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        var ext3 = mockExtension("baz", "n3", "u3", 3.0, 1, 0, LocalDateTime.parse("2020-01-01T00:00"), false, false);
        Mockito.when(repositories.findAllActiveExtensions())
                .thenReturn(Streamable.of(ext1, ext2, ext3));
    }

    static class MockIndex {
        final List<ExtensionSearch> entries = new ArrayList<>();
        boolean created;
        boolean deleted;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ElasticSearchService searchService(
                RepositoryService repositories,
                ElasticsearchOperations searchOperations,
                RelevanceService relevanceService,
                JobRequestScheduler scheduler
        ) {
            return new ElasticSearchService(repositories, searchOperations, relevanceService, scheduler);
        }

        @Bean
        RelevanceService relevanceService(RepositoryService repositories) {
            return new RelevanceService(repositories);
        }

        @Bean
        LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKeyGenerator() {
            return new LatestExtensionVersionCacheKeyGenerator();
        }
    }
}
