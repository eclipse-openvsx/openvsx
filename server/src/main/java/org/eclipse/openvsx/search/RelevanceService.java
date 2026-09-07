/********************************************************************************
 * Copyright (c) 2020-2021 TypeFox and others
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
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * Provides relevance for a given extension
 */
@Service
public class RelevanceService {

    protected final Logger logger = LoggerFactory.getLogger(RelevanceService.class);

    private final RepositoryService repositories;
    private final JsonMapper jsonMapper;

    @Value("${ovsx.search.relevance.rating:1.0}")
    double ratingRelevance;
    @Value("${ovsx.search.relevance.downloads:1.0}")
    double downloadsRelevance;
    @Value("${ovsx.search.relevance.timestamp:1.0}")
    double timestampRelevance;
    @Value("${ovsx.search.relevance.unverified:0.5}")
    double unverifiedRelevance;
    @Value("${ovsx.search.relevance.deprecated:0.5}")
    double deprecatedRelevance;

    public RelevanceService(RepositoryService repositories) {
        this.repositories = repositories;
        this.jsonMapper = JsonMapper.shared();
    }

    public @Nullable ExtensionSearch toSearchEntry(Extension extension, SearchStats stats) {
        var latest = repositories.findLatestVersion(extension, null, false, true);
        if (latest == null) {
            return null;
        }

        var targetPlatforms = repositories.findExtensionTargetPlatforms(extension);
        var entry = extension.toSearch(latest, targetPlatforms);
        entry.setRating(calculateRating(extension, stats));
        entry.setRelevance(calculateRelevance(extension, latest, stats));

        return entry;
    }

    private double calculateRating(Extension extension, SearchStats stats) {
        // IMDB rating formula, source: https://stackoverflow.com/a/1411268
        var padding = 100;
        var reviews = Optional.ofNullable(extension.getReviewCount()).orElse(0L);
        var averageRating = Optional.ofNullable(extension.getAverageRating()).orElse(0.0);
        // The amount of "smoothing" applied to the rating is based on reviews in relation to padding.
        return (averageRating * reviews + stats.averageReviewRating * padding) / (reviews + padding);
    }

    /**
     * What a relevance score is made of, term by term.
     * <p>
     * Returned by {@link #explainRelevance} so that the admin dashboard can show why a result sits where
     * it does. It is the same object {@link #calculateRelevance} reduces to a single number, rather than a
     * reconstruction of it: a debugging view that computes the score its own way is a view of something
     * other than what the index holds, and the first thing it would hide is the two of them disagreeing.
     *
     * @param rating           the rating term, already weighted and clamped
     * @param downloads        the downloads term, already weighted and clamped
     * @param timestamp        the recency term, already weighted and clamped
     * @param unverified       whether the unverified-publisher factor was applied
     * @param unverifiedFactor what that factor is, which is configurable and so not to be assumed
     * @param deprecated       whether the deprecated factor was applied
     * @param deprecatedFactor what that factor is, likewise
     * @param total            the relevance actually stored on the indexed document
     */
    public record RelevanceBreakdown(
            double rating,
            double downloads,
            double timestamp,
            boolean unverified,
            double unverifiedFactor,
            boolean deprecated,
            double deprecatedFactor,
            double total
    ) {}

    /**
     * The relevance of an extension, broken into the terms it is the sum of.
     * <p>
     * Recomputed against the statistics of the moment rather than read off the document, so that a
     * {@code total} differing from the stored value tells you the index is stale - which is a thing worth
     * being able to see.
     */
    public @Nullable RelevanceBreakdown explainRelevance(Extension extension, SearchStats stats) {
        var latest = repositories.findLatestVersion(extension, null, false, true);
        if (latest == null) {
            return null;
        }

        return breakdown(extension, latest, stats);
    }

    private double calculateRelevance(Extension extension, ExtensionVersion latest, SearchStats stats) {
        return breakdown(extension, latest, stats).total();
    }

    private RelevanceBreakdown breakdown(Extension extension, ExtensionVersion latest, SearchStats stats) {
        var extensionId = NamingUtil.toExtensionId(extension);
        logger.debug(">> [{}] CALCULATE RELEVANCE", extensionId);
        var ratingValue = calculateRating(extension, stats) / 5.0;
        // Log scale, not linear. Both scales measure this against the largest download count in the
        // registry, but download counts span several orders of magnitude, and dividing by the largest of
        // them leaves everything outside the top few percent indistinguishable from nothing: against a
        // maximum of twenty million, an extension with six hundred thousand downloads scores 0.03 of the
        // one this term can contribute, so its popularity is worth roughly nothing next to a rating or a
        // recent release. Taken logarithmically the same extension scores 0.79, which is what anyone
        // comparing two extensions by download count would expect it to mean. See
        // EclipseFdn/open-vsx.org#13014, where the download counts of the results bore no relation to
        // what was being searched for.
        var downloadsValue = Math.log1p(extension.getDownloadCount()) / stats.downloadRefLog;
        var timestamp = latest.getTimestamp();
        var timestampValue = Duration.between(stats.oldest, timestamp).toSeconds() / stats.timestampRef;
        var ratingTerm = ratingRelevance * limit(ratingValue);
        var downloadsTerm = downloadsRelevance * limit(downloadsValue);
        var timestampTerm = timestampRelevance * limit(timestampValue);
        var relevance = ratingTerm + downloadsTerm + timestampTerm;
        logger.debug(
                "[{}] RELEVANCE: {} = {} * {} + {} * {} + {} * {}",
                extensionId,
                relevance,
                ratingRelevance,
                limit(ratingValue),
                downloadsRelevance,
                limit(downloadsValue),
                timestampRelevance,
                limit(timestampValue));
        logger.debug("[{}] VALUES: {} | {} | {}", extensionId, ratingValue, downloadsValue, timestampValue);

        // Reduce the relevance value of unverified extensions
        var unverified = !repositories.isVerifiedPublisher(latest);
        if (unverified) {
            relevance *= unverifiedRelevance;
            logger.debug("[{}] UNVERIFIED: {} * {}", extensionId, relevance, unverifiedRelevance);
        }

        // Reduce the relevance value of deprecated extensions
        var deprecated = extension.isDeprecated();
        if (deprecated) {
            relevance *= deprecatedRelevance;
            logger.debug("[{}] DEPRECATED: {} * {}", extensionId, relevance, deprecatedRelevance);
        }

        if (Double.isNaN(relevance) || Double.isInfinite(relevance)) {
            logger.debug("[{}] INVALID RELEVANCE", extensionId);
            var message = "Invalid relevance for entry " + extensionId;
            try {
                message += " " + jsonMapper.writeValueAsString(stats);
            } catch (JacksonException exc) {
                // Ignore exception
            }
            logger.error(message);
            relevance = 0.0;
        }

        logger.debug("<< [{}] CALCULATE RELEVANCE: {}", extensionId, relevance);
        return new RelevanceBreakdown(
                ratingTerm,
                downloadsTerm,
                timestampTerm,
                unverified,
                unverifiedRelevance,
                deprecated,
                deprecatedRelevance,
                relevance);
    }

    private double limit(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    public static class SearchStats {
        protected final double downloadRef;

        /**
         * The download reference on the scale the relevance formula compares against. Kept alongside the
         * raw maximum rather than replacing it: the raw one is what appears in the diagnostics written
         * when a relevance comes out invalid, where a log would say much less about the registry.
         */
        protected final double downloadRefLog;

        protected final double timestampRef;
        protected final LocalDateTime oldest;
        protected final double averageReviewRating;

        public SearchStats(RepositoryService repositories) {
            var now = TimeUtil.getCurrentUTC();
            var oldestTimestamp = repositories.getOldestExtensionTimestamp();
            this.downloadRef = Math.max(repositories.getMaxExtensionDownloadCount(), 1);
            this.downloadRefLog = Math.log1p(this.downloadRef);
            this.oldest = oldestTimestamp == null ? now : oldestTimestamp;
            this.timestampRef = Duration.between(this.oldest, now).toSeconds() + 60;
            this.averageReviewRating = repositories.getAverageReviewRating();
        }
    }
}
