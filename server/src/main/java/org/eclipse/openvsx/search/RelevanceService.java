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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Provides relevance for a given extension
 */
@Component
public class RelevanceService {

    protected final Logger logger = LoggerFactory.getLogger(RelevanceService.class);

    private final RepositoryService repositories;

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
    }

    public ExtensionSearch toSearchEntry(Extension extension, SearchStats stats) {
        var latest = repositories.findLatestVersion(extension,  null, false, true);
        var targetPlatforms = repositories.findExtensionTargetPlatforms(extension);
        var entry = extension.toSearch(latest, targetPlatforms);
        entry.setRating(calculateRating(extension, stats));
        entry.setRelevance(calculateRelevance(extension, latest, stats, entry));

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

    private double calculateRelevance(Extension extension, ExtensionVersion latest, SearchStats stats, ExtensionSearch entry) {
        var extensionId = NamingUtil.toExtensionId(extension);
        logger.debug(">> [{}] CALCULATE RELEVANCE", extensionId);
        var ratingValue = calculateRating(extension, stats) / 5.0;
        var downloadsValue = entry.getDownloadCount() / stats.downloadRef;
        var timestamp = latest.getTimestamp();
        var timestampValue = Duration.between(stats.oldest, timestamp).toSeconds() / stats.timestampRef;
        var relevance = ratingRelevance * limit(ratingValue) + downloadsRelevance * limit(downloadsValue)
                + timestampRelevance * limit(timestampValue);
        logger.debug("[{}] RELEVANCE: {} = {} * {} + {} * {} + {} * {}", extensionId, relevance, ratingRelevance, limit(ratingValue), downloadsRelevance, limit(downloadsValue), timestampRelevance, limit(timestampValue));
        logger.debug("[{}] VALUES: {} | {} | {}", extensionId, ratingValue, downloadsValue, timestampValue);

        // Reduce the relevance value of unverified extensions
        if (!isVerified(latest)) {
            relevance *= unverifiedRelevance;
            logger.debug("[{}] UNVERIFIED: {} * {}", extensionId, relevance, unverifiedRelevance);
        }

        // Reduce the relevance value of deprecated extensions
        if (extension.isDeprecated()) {
            relevance *= deprecatedRelevance;
            logger.debug("[{}] DEPRECATED: {} * {}", extensionId, relevance, deprecatedRelevance);
        }

        if (Double.isNaN(relevance) || Double.isInfinite(relevance)) {
            logger.debug("[{}] INVALID RELEVANCE", extensionId);
            var message = "Invalid relevance for entry " + NamingUtil.toExtensionId(entry);
            try {
                message += " " + new ObjectMapper().writeValueAsString(stats);
            } catch (JsonProcessingException exc) {
                // Ignore exception
            }
            logger.error(message);
            relevance = 0.0;
        }

        logger.debug("<< [{}] CALCULATE RELEVANCE: {}", extensionId, relevance);
        return relevance;
    }

    private double limit(double value) {
        if (value < 0.0)
            return 0.0;
        else if (value > 1.0)
            return 1.0;
        else
            return value;
    }

    private boolean isVerified(ExtensionVersion extVersion) {
        if (extVersion.getPublishedWith() == null)
            return false;
        var user = extVersion.getPublishedWith().getUser();
        var namespace = extVersion.getExtension().getNamespace();
        return repositories.isVerified(namespace, user);
    }

    public static class SearchStats {
        protected final double downloadRef;
        protected final double timestampRef;
        protected final LocalDateTime oldest;
        protected final double averageReviewRating;

        public SearchStats(RepositoryService repositories) {
            var now = TimeUtil.getCurrentUTC();
            var oldestTimestamp = repositories.getOldestExtensionTimestamp();
            this.downloadRef = Math.max(repositories.getMaxExtensionDownloadCount(), 1);
            this.oldest = oldestTimestamp == null ? now : oldestTimestamp;
            this.timestampRef = Duration.between(this.oldest, now).toSeconds() + 60;
            this.averageReviewRating = repositories.getAverageReviewRating();
        }
    }
}
