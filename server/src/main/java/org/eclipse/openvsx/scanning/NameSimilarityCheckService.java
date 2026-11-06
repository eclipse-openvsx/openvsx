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
package org.eclipse.openvsx.scanning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.repositories.NameSimilarityAllowRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Probe;
import org.eclipse.openvsx.repositories.RepositoryService;

/**
 * Flags a version whose extension name or display name matches or resembles another namespace's active
 * extension, for the name squatting admin dashboard to look up again. It never blocks publication: the
 * flag is a validation failure that is not enforced, and an error is not required to pass.
 */
@Service
@Order(4) // After the checks that can block publication, so a rejected package is not looked up.
public class NameSimilarityCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "NAME_SIMILARITY";

    /** Exact matches first, then the most similar. */
    public static final Comparator<Match> CLOSEST_FIRST = Comparator.comparing(Match::exact)
            .thenComparing(Match::similarity)
            .reversed();

    private static final int MAX_VALUE_LENGTH = 120;

    private final NameSimilarityCheckConfig config;
    private final NameSimilarityJooqRepository similarity;
    private final NameSimilarityAllowRepository allows;
    private final RepositoryService repositories;

    public NameSimilarityCheckService(
            NameSimilarityCheckConfig config,
            NameSimilarityJooqRepository similarity,
            NameSimilarityAllowRepository allows,
            RepositoryService repositories
    ) {
        this.config = config;
        this.similarity = similarity;
        this.allows = allows;
        this.repositories = repositories;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public boolean isEnforced() {
        return false;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    /** How long one lookup may run before the database cancels it. */
    public java.time.Duration getStatementTimeout() {
        return config.getStatementTimeout();
    }

    @Override
    public Result check(Context context) {
        var scan = context.scan();
        var namespaceName = scan.getNamespaceName();
        var extensionName = scan.getExtensionName();

        // The scan falls back to the extension name for a package that declares no display name, and
        // comparing that against other display names would report matches the package never claimed.
        var displayName = scan.getExtensionDisplayName();
        var displayNames = displayName == null || displayName.equals(extensionName)
                ? List.<String>of()
                : List.of(displayName);

        var matches = findMatches(namespaceName, extensionName, displayNames);
        var existing = repositories.findExtension(extensionName, namespaceName);
        if (existing != null) {
            var allowed = allowedPartners(List.of(existing.getId())).getOrDefault(existing.getId(), Set.of());
            matches.removeIf(match -> allowed.contains(match.extensionId()));
        }

        var closest = closestPerExtension(matches);
        if (closest.isEmpty()) {
            return Result.pass();
        }
        return Result.fail(closest.stream().map(NameSimilarityCheckService::toFailure).toList());
    }

    /** Matches of the given names against other namespaces' active extensions, in no particular order. */
    public List<Match> findMatches(String namespaceName, String extensionName, Collection<String> displayNames) {
        var probe = new Probe(
                namespaceName,
                extensionName,
                displayNames,
                config.getNameThreshold(),
                config.getDisplayNameThreshold(),
                config.getMinFuzzyLength(),
                config.getMaxMatches(),
                config.getStatementTimeout());
        return new ArrayList<>(similarity.findSimilar(probe));
    }

    /** For each of the given extensions, the extensions it has been allowed alongside. */
    public Map<Long, Set<Long>> allowedPartners(Collection<Long> extensionIds) {
        var partners = new HashMap<Long, Set<Long>>();
        for (var allow : allows.findInvolving(extensionIds)) {
            partners.computeIfAbsent(allow.getExtensionId(), id -> new HashSet<>()).add(allow.getOtherExtensionId());
            partners.computeIfAbsent(allow.getOtherExtensionId(), id -> new HashSet<>()).add(allow.getExtensionId());
        }
        return partners;
    }

    /** The closest match with each extension, closest first. */
    public static List<Match> closestPerExtension(Collection<Match> matches) {
        var closest = new LinkedHashMap<Long, Match>();
        matches.stream()
                .sorted(CLOSEST_FIRST)
                .forEach(match -> closest.putIfAbsent(match.extensionId(), match));
        return List.copyOf(closest.values());
    }

    private static Failure toFailure(Match match) {
        var field = match.field() == NameSimilarityField.NAME ? "Extension name" : "Display name";
        var other = match.namespaceName() + "." + match.extensionName();
        var reason = match.exact()
                ? String.format("%s \"%s\" is also used by %s", field, shorten(match.probeValue()), other)
                : String.format(
                        "%s \"%s\" resembles %s (\"%s\", %d%% similar)",
                        field,
                        shorten(match.probeValue()),
                        other,
                        shorten(match.value()),
                        Math.round(match.similarity() * 100));
        return new Failure(match.field().name(), reason);
    }

    private static String shorten(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "...";
    }
}
