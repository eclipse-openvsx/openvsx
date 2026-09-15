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
package org.eclipse.openvsx.json;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Why a search returned what it returned, in the order it returned it.
 *
 * @param query      the query string that produced this
 * @param totalHits  how many documents matched, not how many are listed here
 * @param references the values every score is measured against, which are properties of the registry
 *                   rather than of any extension and are the usual reason a term contributes nothing
 * @param entries    the results, in the order the search put them
 */
@Schema(description = "A search result set with the score of each entry broken down")
public record SearchExplainJson(
        String query,
        long totalHits,
        SearchReferencesJson references,
        List<SearchExplainEntryJson> entries
) {

    /**
     * The registry-wide values the relevance terms are measured against.
     *
     * @param maxDownloadCount    the divisor of the downloads term: an extension's downloads count for
     *                            nothing unless they are an appreciable fraction of this
     * @param oldestTimestamp     the epoch of the recency term
     * @param averageReviewRating the value an extension's own rating is smoothed towards, which decides
     *                            how much a handful of reviews can move it
     */
    @Schema(description = "Registry-wide values the relevance terms are measured against")
    public record SearchReferencesJson(
            long maxDownloadCount,
            @Nullable String oldestTimestamp,
            double averageReviewRating
    ) {}

    /**
     * One step of Elasticsearch's account of a score: what it was worth, what produced it, and the steps
     * that in turn produced that.
     *
     * @param description Elasticsearch's own wording, which names the clause or the term
     * @param value       what this step contributed
     * @param details     the steps it is composed of, empty at a leaf or where the depth limit was reached
     * @param truncated   whether children were dropped to keep the response a readable size
     */
    @Schema(description = "One step of the search engine's account of how a score was arrived at")
    public record SearchScoreDetailJson(
            String description,
            double value,
            List<SearchScoreDetailJson> details,
            boolean truncated
    ) {}

    /**
     * One result, and where its score came from.
     *
     * @param score            the score the search ranked on
     * @param textScore        how well the query matched this document's text, derived as
     *                         {@code score / storedRelevance} because the query multiplies the two
     * @param storedRelevance  the relevance held on the indexed document, which is what actually ranked it
     * @param currentRelevance the relevance recomputed now; differing from {@code storedRelevance} means
     *                         the index has not been rebuilt since the inputs moved
     * @param rating           the rating term of {@code currentRelevance}, weighted and clamped
     * @param downloads        the downloads term, likewise
     * @param recency          the recency term, likewise
     * @param unverified       whether the unverified-publisher factor was applied
     * @param unverifiedFactor what that factor is; it is configurable, so how much it costs is not a
     *                         constant the reader can assume
     * @param deprecated       whether the deprecated factor was applied
     * @param deprecatedFactor what that factor is, likewise
     */
    @Schema(description = "A single search result and the parts its score is made of")
    public record SearchExplainEntryJson(
            int position,
            String namespace,
            String name,
            long downloadCount,
            @Nullable Double averageRating,
            @Nullable String timestamp,
            double score,
            @Nullable Double textScore,
            double storedRelevance,
            @Nullable Double currentRelevance,
            @Nullable Double rating,
            @Nullable Double downloads,
            @Nullable Double recency,
            boolean unverified,
            @Nullable Double unverifiedFactor,
            boolean deprecated,
            @Nullable Double deprecatedFactor,
            @Nullable SearchScoreDetailJson scoreDetail
    ) {}
}
