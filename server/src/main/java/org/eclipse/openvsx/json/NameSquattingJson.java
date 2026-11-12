/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** The bodies of the name squatting admin endpoints. */
public final class NameSquattingJson {

    private NameSquattingJson() {
    }

    @Schema(name = "NameSquattingExtension", description = "An extension as the name squatting dashboard shows it")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Extension(
            String namespace,
            String name,
            @Schema(description = "A display name of one of its active versions") @Nullable String displayName,
            int downloadCount,
            @Schema(description = "UTC timestamp of its first publication") @Nullable String publishedDate,
            int activeVersionCount,
            boolean active
    ) {}

    @Schema(
        name = "NameSquattingMatch",
        description = "How a look-alike's name compares with the extension published first"
    )
    public record Match(
            @Schema(allowableValues = { "NAME", "DISPLAY_NAME" }) String field,
            @Schema(description = "The value of the extension published first") String value,
            @Schema(description = "The look-alike's value") String lookalikeValue,
            @Schema(description = "Trigram similarity, from 0 to 1") float similarity,
            @Schema(description = "Whether the values are equal ignoring case and surrounding whitespace") boolean exact
    ) {}

    @Schema(name = "NameSquattingLookalike")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lookalike(
            Extension extension,
            @Schema(description = "Closest first") List<Match> matches,
            @Schema(
                description = "Trigram similarity of the two namespace names, from 0 to 1"
            ) float namespaceSimilarity,
            @Schema(
                description = "UTC timestamp of the latest NAME_SIMILARITY flag of either extension, absent for a display name only shared"
            ) @Nullable String flaggedAt
    ) {}

    @Schema(name = "NameSquattingGroup", description = "An extension and the later extensions that resemble it")
    public record Group(Extension anchor, List<Lookalike> lookalikes) {}

    @Schema(name = "NameSquattingCollisions")
    public record Collisions(
            List<Group> groups,
            @Schema(description = "Pass as offset to load the next groups") int nextOffset,
            boolean hasMore
    ) {}

    @Schema(
        name = "NameSquattingAllowRequest",
        description = "A look-alike to allow alongside an extension it resembles"
    )
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AllowRequest(
            String namespace,
            String extension,
            String otherNamespace,
            String otherExtension,
            @Nullable String reason
    ) {}

    @Schema(name = "NameSquattingAllow", description = "A pair an admin allowed")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Allow(
            long id,
            Extension other,
            @Schema(allowableValues = { "NAME", "DISPLAY_NAME", "NAMESPACE" }) String field,
            float similarity,
            @Schema(description = "The allowed extension's value when it was allowed") String value,
            @Schema(description = "The other extension's value when it was allowed") String otherValue,
            @Schema(description = "Whether either value has changed since") boolean renamed,
            @Nullable String reason,
            @Schema(description = "Login name of the admin, absent once that user is gone") @Nullable String decidedBy,
            String decidedAt
    ) {}

    @Schema(name = "NameSquattingAllowlistGroup", description = "An allowed extension and the pairs it was allowed in")
    public record AllowlistGroup(Extension extension, List<Allow> allows) {}

    @Schema(name = "NameSquattingAllowlist")
    public record Allowlist(List<AllowlistGroup> groups, int page, boolean hasMore) {}

    @Schema(name = "NameSquattingNamespace", description = "A namespace as the name squatting dashboard shows it")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamespaceSummary(
            String name,
            @Schema(description = "Downloads of its active extensions") long downloads,
            @Schema(description = "How many active extensions it has") int activeExtensions,
            @Schema(
                description = "UTC timestamp of its earliest active extension's publication, absent without any"
            ) @Nullable String firstPublished
    ) {}

    @Schema(
        name = "NameSquattingNamespacePair",
        description = "Two namespaces whose names resemble each other, the one with more downloads as target"
    )
    public record NamespacePair(
            NamespaceSummary target,
            NamespaceSummary lookalike,
            @Schema(description = "Levenshtein distance of the names, ignoring case") int editDistance,
            @Schema(description = "Trigram similarity of the names, from 0 to 1") float similarity,
            @Schema(
                description = "Whether the names only differ in characters other than letters and digits"
            ) boolean separatorOnly,
            @Schema(description = "Whether a user is a member of both namespaces") boolean sharedMember,
            @Schema(description = "Whether the target counts as popular next to the look-alike") boolean popular
    ) {}

    @Schema(name = "NameSquattingNamespacePairs")
    public record NamespacePairs(
            List<NamespacePair> pairs,
            @Schema(description = "Pairs not allowed, popular or not") int total,
            @Schema(description = "Of those, the pairs with a popular target") int popularTotal,
            @Schema(description = "The offset the page starts at") int offset,
            @Schema(description = "Whether more pairs follow the page") boolean hasMore
    ) {}

    @Schema(name = "NameSquattingNamespaceAllowRequest", description = "Two namespaces to allow alongside each other")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamespaceAllowRequest(
            @Schema(description = "Name of the look-alike") String namespace,
            @Schema(description = "Name of the namespace it resembles") String otherNamespace,
            @Schema(description = "Why the pair is not squatting, at most 2048 characters") @Nullable String reason
    ) {}

    @Schema(name = "NameSquattingNamespaceAllow", description = "A pair of namespaces an admin allowed")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamespaceAllow(
            long id,
            @Schema(description = "The look-alike's name when it was allowed") String namespace,
            @Schema(description = "The other namespace's name when it was allowed") String otherNamespace,
            @Schema(description = "Trigram similarity of the names when allowed, from 0 to 1") float similarity,
            @Nullable String reason,
            @Schema(description = "Login name of the admin, absent once that user is gone") @Nullable String decidedBy,
            @Schema(description = "UTC timestamp of the allow") String decidedAt
    ) {}

    @Schema(name = "NameSquattingNamespaceAllowlist")
    public record NamespaceAllowlist(
            @Schema(description = "The most recently allowed first") List<NamespaceAllow> allows,
            int page,
            @Schema(description = "Whether another page follows") boolean hasMore
    ) {}
}
