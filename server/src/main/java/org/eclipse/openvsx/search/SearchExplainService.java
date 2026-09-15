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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.elasticsearch.core.document.Explanation;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.json.SearchExplainJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.RelevanceService.SearchStats;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * Answers "why is this result here, and why is that one above it".
 * <p>
 * A search score is two numbers multiplied: how well the query matched the document's text, and a
 * relevance computed from the extension's rating, downloads and age when the document was indexed. Only
 * the product is visible from outside, so a result list that looks wrong gives no clue as to which half
 * is responsible - and the two want entirely different fixes. This takes them apart.
 */
@Component
public class SearchExplainService {

    private final ElasticSearchService elasticSearch;
    private final SearchUtilService search;
    private final RelevanceService relevanceService;
    private final RepositoryService repositories;

    public SearchExplainService(
            ElasticSearchService elasticSearch,
            SearchUtilService search,
            RelevanceService relevanceService,
            RepositoryService repositories
    ) {
        this.elasticSearch = elasticSearch;
        this.search = search;
        this.relevanceService = relevanceService;
        this.repositories = repositories;
    }

    public SearchExplainJson explain(String query, int size, int offset, String sortBy, String sortOrder) {
        // Only elasticsearch scores anything. The database engine orders rows, so there is no score to
        // take apart and reporting one would be inventing it.
        if (!elasticSearch.isEnabled()) {
            throw new ErrorResultException(
                    "There are no search scores to explain: searches are answered by '"
                            + search.getIndexStats().implementation() + "'.");
        }

        if (offset % size != 0) {
            // The caller pages in whole steps of its own size; anything else would need the two-page
            // read that search() does, and would report positions that do not line up with the pages
            // either side of it.
            throw new ErrorResultException("offset must be a multiple of size.");
        }

        var options = new ISearchService.Options(query, null, null, size, offset, sortOrder, sortBy, false, null);
        var hits = elasticSearch.searchWithScores(options);

        // One SearchStats for the whole listing, as an indexing run would use: recomputing it per entry
        // would let the reference values drift between rows of the same table.
        var stats = new SearchStats(repositories);

        // One query for the whole page rather than one per row. An id with no extension behind it has
        // been purged since the document was indexed, which toEntry reports rather than hides.
        var extensions = repositories
                .findExtensions(hits.getSearchHits().stream().map(hit -> hit.getContent().getId()).toList())
                .stream()
                .collect(Collectors.toMap(Extension::getId, Function.identity()));

        var entries = new ArrayList<SearchExplainJson.SearchExplainEntryJson>(hits.getSearchHits().size());
        var position = offset;
        for (var hit : hits.getSearchHits()) {
            var document = hit.getContent();
            entries.add(
                    toEntry(
                            position++,
                            hit.getScore(),
                            hit.getExplanation(),
                            document,
                            extensions.get(document.getId()),
                            stats));
        }

        return new SearchExplainJson(
                query,
                hits.getTotalHits(),
                new SearchExplainJson.SearchReferencesJson(
                        (long) stats.downloadRef,
                        stats.oldest == null ? null : TimeUtil.toUTCString(stats.oldest),
                        stats.averageReviewRating),
                entries);
    }

    private SearchExplainJson.SearchExplainEntryJson toEntry(
            int position,
            float score,
            @Nullable Explanation explanation,
            ExtensionSearch document,
            @Nullable Extension extension,
            SearchStats stats
    ) {
        // Recomputed from the extension rather than read off the document, which holds only the product.
        // Where the two disagree the index is stale, and saying so is more use than hiding it.
        var breakdown = extension == null ? null : relevanceService.explainRelevance(extension, stats);

        var storedRelevance = document.getRelevance();
        // The query multiplies the text score by the stored relevance, so dividing recovers the other
        // half. A relevance of zero leaves nothing to recover - the product is zero whatever matched.
        var textScore = storedRelevance == 0.0 ? null : score / storedRelevance;

        return new SearchExplainJson.SearchExplainEntryJson(
                position,
                document.getNamespace(),
                document.getName(),
                document.getDownloadCount(),
                document.getRating(),
                TimeUtil.toUTCString(LocalDateTime.ofEpochSecond(document.getTimestamp(), 0, ZoneOffset.UTC)),
                score,
                textScore,
                storedRelevance,
                breakdown == null ? null : breakdown.total(),
                breakdown == null ? null : breakdown.rating(),
                breakdown == null ? null : breakdown.downloads(),
                breakdown == null ? null : breakdown.timestamp(),
                breakdown != null && breakdown.unverified(),
                breakdown == null ? null : breakdown.unverifiedFactor(),
                breakdown != null && breakdown.deprecated(),
                breakdown == null ? null : breakdown.deprecatedFactor(),
                toScoreDetail(explanation, 0));
    }

    /**
     * The maximum depth of the score account carried back.
     * <p>
     * Elasticsearch explains a score all the way down to the term frequencies and field lengths behind
     * every clause, which is a great deal of tree for a question that is answered several levels above it:
     * which clause matched, and what it was worth. Deeper steps are dropped and the step that lost them
     * says so, rather than the response silently being a partial account of itself.
     */
    private static final int MAX_DETAIL_DEPTH = 4;

    private static SearchExplainJson.@Nullable SearchScoreDetailJson toScoreDetail(
            @Nullable Explanation explanation,
            int depth
    ) {
        if (explanation == null) {
            return null;
        }

        var children = explanation.getDetails();
        var atLimit = depth >= MAX_DETAIL_DEPTH;
        List<SearchExplainJson.SearchScoreDetailJson> details;
        if (atLimit || children == null || children.isEmpty()) {
            details = Collections.emptyList();
        } else {
            details = children.stream().map(child -> toScoreDetail(child, depth + 1)).filter(d -> d != null).toList();
        }

        return new SearchExplainJson.SearchScoreDetailJson(
                explanation.getDescription() == null ? "" : explanation.getDescription(),
                explanation.getValue() == null ? 0.0 : explanation.getValue(),
                details,
                atLimit && children != null && !children.isEmpty());
    }
}
