/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.search;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.document.Explanation;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;

/**
 * Taking a search score apart. The score a result ranked on is the product of how well the query matched
 * its text and the relevance stored on its document, and only the product survives into the result list -
 * which is why a result list that looks wrong says nothing about which half to go and fix.
 */
@ExtendWith(MockitoExtension.class)
class SearchExplainServiceTest {

    @Mock
    ElasticSearchService elasticSearch;

    @Mock
    SearchUtilService search;

    @Mock
    RelevanceService relevanceService;

    @Mock
    RepositoryService repositories;

    SearchExplainService explainService;

    @BeforeEach
    void setUp() {
        explainService = new SearchExplainService(elasticSearch, search, relevanceService, repositories);
    }

    private static ExtensionSearch document(long id, String namespace, String name, double relevance) {
        var doc = new ExtensionSearch();
        doc.setId(id);
        doc.setNamespace(namespace);
        doc.setName(name);
        doc.setRelevance(relevance);
        doc.setDownloadCount(640718);
        doc.setTimestamp(LocalDateTime.parse("2024-01-16T04:14:26").toEpochSecond(ZoneOffset.UTC));
        return doc;
    }

    private void givenHits(SearchHit<ExtensionSearch>... hits) {
        Mockito.when(elasticSearch.isEnabled()).thenReturn(true);
        // No extension rows unless a test says otherwise, which is the purged case.
        Mockito.lenient().when(repositories.findExtensions(anyCollection())).thenReturn(Streamable.empty());
        SearchHits<ExtensionSearch> searchHits = new SearchHitsImpl<>(
                hits.length,
                TotalHitsRelation.EQUAL_TO,
                0f,
                Duration.ZERO,
                null,
                null,
                List.of(hits),
                null,
                null,
                null);
        Mockito.when(elasticSearch.searchWithScores(any())).thenReturn(searchHits);
    }

    @SuppressWarnings("unchecked")
    private static SearchHit<ExtensionSearch> hit(ExtensionSearch doc, float score) {
        return hit(doc, score, null);
    }

    @SuppressWarnings("unchecked")
    private static SearchHit<ExtensionSearch> hit(ExtensionSearch doc, float score, Explanation explanation) {
        return new SearchHit<>(
                null,
                String.valueOf(doc.getId()),
                null,
                score,
                null,
                null,
                null,
                null,
                explanation,
                null,
                doc);
    }

    private static Explanation step(String description, double value, Explanation... details) {
        return new Explanation(true, value, description, List.of(details));
    }

    /**
     * The query multiplies the text score by the stored relevance, so dividing the ranked score by the
     * relevance recovers the text score - the half that says whether the query matched this document well,
     * as distinct from whether the registry considers it a good extension.
     */
    @Test
    @SuppressWarnings("unchecked")
    void separatesTheTextScoreFromTheRelevanceItWasMultipliedBy() {
        var doc = document(1L, "yzhang", "markdown-all-in-one", 1.5);
        givenHits(hit(doc, 3.0f));

        var explained = explainService.explain("markdown", 20, 0, "relevance", "desc");

        var entry = explained.entries().getFirst();
        assertThat(entry.score()).isEqualTo(3.0);
        assertThat(entry.storedRelevance()).isEqualTo(1.5);
        assertThat(entry.textScore()).isCloseTo(2.0, within(0.0001));
    }

    // A relevance of zero makes the product zero whatever the query matched, so there is no text score
    // hiding in it to recover - and reporting one would be inventing a number.
    @Test
    @SuppressWarnings("unchecked")
    void reportsNoTextScoreWhenTheRelevanceIsZero() {
        givenHits(hit(document(1L, "foo", "bar", 0.0), 0.0f));

        assertThat(explainService.explain("markdown", 20, 0, "relevance", "desc").entries().getFirst().textScore())
                .isNull();
    }

    // The page's extensions are fetched in one query and matched back to their documents by id. Get that
    // pairing wrong and every row silently reports as purged, which is why this asserts a real breakdown
    // rather than only the absence of one.
    @Test
    @SuppressWarnings("unchecked")
    void reportsTheBreakdownOfTheExtensionBehindEachDocument() {
        var extension = new Extension();
        extension.setId(42L);
        givenHits(hit(document(42L, "yzhang", "markdown-all-in-one", 1.25), 2.5f));
        Mockito.when(repositories.findExtensions(anyCollection())).thenReturn(Streamable.of(extension));
        Mockito.when(relevanceService.explainRelevance(Mockito.eq(extension), any()))
                .thenReturn(new RelevanceService.RelevanceBreakdown(0.1, 0.2, 0.3, true, 0.5, false, 0.5, 0.3));

        var entry = explainService.explain("markdown", 20, 0, "relevance", "desc").entries().getFirst();

        assertThat(entry.currentRelevance()).isEqualTo(0.3);
        assertThat(entry.rating()).isEqualTo(0.1);
        assertThat(entry.downloads()).isEqualTo(0.2);
        assertThat(entry.recency()).isEqualTo(0.3);
        assertThat(entry.unverified()).isTrue();
        assertThat(entry.unverifiedFactor()).isEqualTo(0.5);
    }

    // The extension behind a document can have been purged since it was indexed. That is worth reporting
    // as an unknown breakdown rather than failing the whole listing.
    @Test
    @SuppressWarnings("unchecked")
    void survivesAnExtensionThatIsNoLongerThere() {
        givenHits(hit(document(7L, "gone", "away", 1.0), 1.0f));

        var entry = explainService.explain("markdown", 20, 0, "relevance", "desc").entries().getFirst();

        assertThat(entry.namespace()).isEqualTo("gone");
        assertThat(entry.currentRelevance()).isNull();
        assertThat(entry.rating()).isNull();
    }

    /**
     * The engine's own account of the text half, which is the part no amount of arithmetic over the
     * stored values can reconstruct: which clause matched, and what it was worth.
     */
    @Test
    @SuppressWarnings("unchecked")
    void carriesTheEngineSAccountOfTheScore() {
        var doc = document(1L, "yzhang", "markdown-all-in-one", 1.5);
        givenHits(
                hit(
                        doc,
                        3.0f,
                        step(
                                "function score, product of:",
                                3.0,
                                step("sum of:", 2.0, step("weight(name:markdown in 1)", 2.0)),
                                step("field value function: relevance", 1.5))));

        var detail = explainService.explain("markdown", 20, 0, "relevance", "desc").entries().getFirst().scoreDetail();

        assertThat(detail).isNotNull();
        assertThat(detail.description()).isEqualTo("function score, product of:");
        assertThat(detail.value()).isEqualTo(3.0);
        assertThat(detail.details()).hasSize(2);
        assertThat(detail.details().getFirst().details().getFirst().description())
                .isEqualTo("weight(name:markdown in 1)");
    }

    /**
     * Elasticsearch explains a score down to term frequencies and field lengths, which is far more tree
     * than the question needs. The step that loses its children says so, so a reader can tell a leaf from
     * a truncation.
     */
    @Test
    @SuppressWarnings("unchecked")
    void trimsTheAccountAndSaysWhereItDidSo() {
        var deep = step("d5", 1.0);
        for (var i = 4; i >= 0; i--) {
            deep = step("d" + i, 1.0, deep);
        }
        givenHits(hit(document(1L, "foo", "bar", 1.0), 1.0f, deep));

        var detail = explainService.explain("markdown", 20, 0, "relevance", "desc").entries().getFirst().scoreDetail();

        var depth = 0;
        var node = detail;
        while (!node.details().isEmpty()) {
            node = node.details().getFirst();
            depth++;
        }
        assertThat(depth).isEqualTo(4);
        assertThat(node.truncated()).isTrue();
    }

    // Positions have to be the ones the search gave, not the ones this page happens to be showing:
    // "it is 767th" is the answer, and a second page restarting at 1 would report it as 17th.
    @Test
    @SuppressWarnings("unchecked")
    void numbersResultsFromTheOffsetTheyStartAt() {
        givenHits(hit(document(1L, "foo", "bar", 1.0), 1.0f));

        var entry = explainService.explain("markdown", 25, 50, "relevance", "desc").entries().getFirst();

        assertThat(entry.position()).isEqualTo(50);
    }

    // The caller pages in whole steps of its own size; a partial page would report positions that do not
    // line up with the pages either side of it.
    @Test
    void refusesAnOffsetThatIsNotAWholeNumberOfPages() {
        Mockito.when(elasticSearch.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> explainService.explain("markdown", 25, 30, "relevance", "desc"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("multiple of size");
    }

    // Only elasticsearch scores anything; the database engine orders rows. Reporting a breakdown of a
    // score that was never computed would be describing a search nobody ran.
    @Test
    void refusesWhenElasticsearchIsNotTheEngine() {
        Mockito.when(elasticSearch.isEnabled()).thenReturn(false);
        Mockito.when(search.getIndexStats())
                .thenReturn(new SearchIndexStats(true, SearchIndexStats.DATABASE, false, null, 0L, null));

        assertThatThrownBy(() -> explainService.explain("markdown", 20, 0, "relevance", "desc"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("database");
    }
}
