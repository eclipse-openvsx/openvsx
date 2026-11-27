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
package org.eclipse.openvsx.admin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.NameSimilarityAllow;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.NameSimilarityAllowRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Candidate;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.ExtensionNames;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.NameSimilarityCheckService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * The name squatting admin dashboard: looks up again the extensions the NAME_SIMILARITY check flagged and
 * those sharing a display name across namespaces, and keeps the allowlist of pairs that are not squatting
 * on each other.
 * <p>
 * Nothing about a match is stored: every page looks its candidates up live, so a look-alike that was
 * deleted, renamed or allowed since it was flagged no longer shows.
 */
@Service
public class NameSquattingService {

    /** Candidates are looked up in batches until a page is full, giving up after this many. */
    static final int MAX_BATCHES = 4;

    static final int MAX_REASON_LENGTH = 2048;

    /** The SQLState of a statement cancelled by statement_timeout, among other cancellations. */
    private static final String QUERY_CANCELED = "57014";

    /** The earliest publication date first; an unknown date counts as earliest. */
    static final Comparator<ExtensionNames> PUBLISHED_FIRST = Comparator
            .comparing(ExtensionNames::publishedDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingLong(ExtensionNames::id);

    private final NameSimilarityJooqRepository similarity;
    private final NameSimilarityAllowRepository allows;
    private final NameSimilarityCheckService check;
    private final RepositoryService repositories;
    private final LogService logs;
    private final EntityManager entityManager;

    public NameSquattingService(
            NameSimilarityJooqRepository similarity,
            NameSimilarityAllowRepository allows,
            NameSimilarityCheckService check,
            RepositoryService repositories,
            LogService logs,
            EntityManager entityManager
    ) {
        this.similarity = similarity;
        this.allows = allows;
        this.check = check;
        this.repositories = repositories;
        this.logs = logs;
        this.entityManager = entityManager;
    }

    /**
     * The look-alikes of the candidates from {@code cursor} on, grouped under the extension each resembles
     * that was published first. Groups follow the order of their most recently flagged candidate; a page
     * may hold more than {@code size} look-alikes, or fewer once candidates run out.
     * <p>
     * Deliberately not transactional: every lookup runs in a transaction of its own, so the statement
     * timeout one sets cannot outlive it, and one that is cancelled fails only itself.
     *
     * @throws ErrorResultException with 503 when a lookup ran out of its statement timeout
     */
    public NameSquattingJson.Collisions findCollisions(NameSquattingJson.Cursor cursor, int size)
            throws ErrorResultException {
        return findCollisions(cursor, size, check.getStatementTimeout());
    }

    /**
     * No candidate lookup starts once half the {@code budget} has passed, though every request looks at
     * least one up so pages advance. The look-alikes found are always anchored, as cutting that short would
     * list one under two anchors across pages.
     */
    NameSquattingJson.Collisions findCollisions(NameSquattingJson.Cursor cursor, int size, Duration budget)
            throws ErrorResultException {
        var deadline = System.nanoTime() + budget.toNanos() / 2;
        return stoppingAtTimeout(() -> lookUpCollisions(cursor, size, deadline));
    }

    private static boolean passed(long deadline) {
        return System.nanoTime() - deadline > 0;
    }

    /** Run the lookups, answering 503 when one ran out of its statement timeout. */
    private <T> T stoppingAtTimeout(Supplier<T> lookups) throws ErrorResultException {
        try {
            return lookups.get();
        } catch (RuntimeException exc) {
            if (!isStatementTimeout(exc)) {
                throw exc;
            }
            throw new ErrorResultException(
                    "Looking up look-alikes took longer than " + check.getStatementTimeout().toSeconds()
                            + " s and was stopped. Try again, or raise ovsx.scanning.name-similarity.statement-timeout.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private NameSquattingJson.Collisions lookUpCollisions(
            NameSquattingJson.Cursor from,
            int size,
            long deadline
    ) {
        var pairs = new LinkedHashMap<Long, Pair>();
        var probed = new HashSet<Long>();
        var offset = from.offset();
        var afterId = from.afterId();
        var hasMore = true;
        for (var batch = 0; batch < MAX_BATCHES && hasMore && pairs.size() < size; batch++) {
            if (batch > 0 && passed(deadline)) {
                break;
            }
            var candidates = similarity.findCandidates(
                    NameSimilarityCheckService.CHECK_TYPE,
                    offset,
                    afterId,
                    size,
                    check.getStatementTimeout());
            var lookedUp = collectPairs(candidates, pairs, probed, deadline);
            // Flagged candidates come first and are counted; those sharing a display name are keyed by id.
            for (var candidate : candidates.subList(0, lookedUp)) {
                if (candidate.flaggedAt() != null) {
                    offset++;
                } else {
                    afterId = candidate.extensionId();
                }
            }
            hasMore = lookedUp < candidates.size() || candidates.size() == size;
            if (lookedUp < candidates.size()) {
                break;
            }
        }
        anchorAtTheEarliest(pairs, probed);

        var lookalikeIds = pairs.keySet();
        var lookalikes = byId(similarity.findNames(lookalikeIds));
        var groups = new LinkedHashMap<Long, List<Pair>>();
        pairs.values().forEach(pair -> groups.computeIfAbsent(pair.anchor().id(), id -> new ArrayList<>()).add(pair));
        var json = groups.values().stream()
                .map(
                        group -> new NameSquattingJson.Group(
                                toJson(group.getFirst().anchor()),
                                group.stream()
                                        .sorted(
                                                Comparator.comparing(
                                                        Pair::closest,
                                                        NameSimilarityCheckService.CLOSEST_FIRST))
                                        .map(pair -> toJson(pair, lookalikes.get(pair.lookalikeId())))
                                        .toList()))
                .toList();
        return new NameSquattingJson.Collisions(json, new NameSquattingJson.Cursor(offset, afterId), hasMore);
    }

    /**
     * Look the candidates up and add their pairs to {@code pairs}, keyed by look-alike, which keeps each
     * look-alike under the earliest published extension it resembles. The candidates join {@code probed}.
     *
     * @return how many candidates were looked up, in order, before the deadline passed
     */
    private int collectPairs(List<Candidate> candidates, Map<Long, Pair> pairs, Set<Long> probed, long deadline) {
        if (candidates.isEmpty()) {
            return 0;
        }
        var ids = candidates.stream().map(Candidate::extensionId).toList();
        var names = byId(similarity.findNames(ids));
        var allowed = check.allowedPartners(ids);

        var found = new ArrayList<Found>();
        var lookedUp = 0;
        for (var candidate : candidates) {
            var candidateNames = names.get(candidate.extensionId());
            if (candidateNames != null && candidateNames.active()) {
                if (!probed.isEmpty() && passed(deadline)) {
                    break;
                }
                probed.add(candidate.extensionId());
                found.addAll(
                        lookUp(candidate, candidateNames, allowed.getOrDefault(candidate.extensionId(), Set.of())));
            }
            lookedUp++;
        }

        var others = byId(similarity.findNames(found.stream().map(Found::otherId).toList()));
        for (var match : found) {
            var other = others.get(match.otherId());
            if (other != null) {
                var pair = Pair.of(match, other);
                pairs.merge(pair.lookalikeId(), pair, Pair::earlierAnchor);
            }
        }
        return lookedUp;
    }

    /**
     * Look up from their own side the look-alikes that were only found from their anchor's, so each sits
     * under the earliest extension it resembles whichever page found it, and pages agree on its group.
     */
    private void anchorAtTheEarliest(Map<Long, Pair> pairs, Set<Long> probed) {
        var unprobed = pairs.keySet().stream().filter(id -> !probed.contains(id)).toList();
        if (unprobed.isEmpty()) {
            return;
        }
        var names = byId(similarity.findNames(unprobed));
        var allowed = check.allowedPartners(unprobed);

        var found = new ArrayList<Found>();
        for (var lookalikeId : unprobed) {
            var lookalikeNames = names.get(lookalikeId);
            if (lookalikeNames != null) {
                var candidate = new Candidate(lookalikeId, pairs.get(lookalikeId).flaggedAt());
                found.addAll(lookUp(candidate, lookalikeNames, allowed.getOrDefault(lookalikeId, Set.of())));
            }
        }

        var others = byId(similarity.findNames(found.stream().map(Found::otherId).toList()));
        for (var match : found) {
            var other = others.get(match.otherId());
            if (other == null) {
                continue;
            }
            var pair = Pair.of(match, other);
            // Extensions published after the look-alike resemble it, but are not on this page.
            if (pair.lookalikeId() == match.candidate().extensionId()) {
                pairs.merge(pair.lookalikeId(), pair, Pair::earlierAnchor);
            }
        }
    }

    /**
     * The candidate's matches, one {@link Found} per other extension. The earliest published are looked
     * up first, so the limit on matches never cuts off the extension the candidate should be grouped under.
     */
    private List<Found> lookUp(Candidate candidate, ExtensionNames names, Set<Long> allowedPartners) {
        var probe = check.probe(names.namespaceName(), names.name(), names.displayNames())
                .excluding(allowedPartners)
                .keepingEarliestFirst();
        return check.findMatches(probe).stream()
                .collect(Collectors.groupingBy(Match::extensionId, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> new Found(candidate, names, entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Allow the look-alike alongside the other extension, so the pair is no longer reported.
     * <p>
     * Deliberately not transactional, like {@link #findCollisions}: the lookups run in transactions of
     * their own, and the insert is a single statement.
     *
     * @throws ErrorResultException with 503 when a lookup ran out of its statement timeout
     */
    public ResultJson allow(NameSquattingJson.AllowRequest request, UserData admin) throws ErrorResultException {
        var reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new ErrorResultException(
                    "The reason must not be longer than " + MAX_REASON_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        }
        var lookalikeId = findExtension(request.namespace(), request.extension()).getId();
        var otherId = findExtension(request.otherNamespace(), request.otherExtension()).getId();
        if (lookalikeId == otherId) {
            throw new ErrorResultException("An extension cannot be allowed alongside itself", HttpStatus.BAD_REQUEST);
        }
        var names = byId(similarity.findNames(List.of(lookalikeId, otherId)));
        var pairName = toId(names.get(lookalikeId)) + " alongside " + toId(names.get(otherId));
        var alreadyAllowed = ResultJson.success("Already allowed " + pairName);
        if (allows.findPair(lookalikeId, otherId).isPresent()) {
            return alreadyAllowed;
        }

        var closest = stoppingAtTimeout(() -> closestEitherWay(names, lookalikeId, otherId));
        if (closest == null) {
            throw new ErrorResultException(
                    toId(names.get(lookalikeId)) + " no longer resembles " + toId(names.get(otherId))
                            + ", so there is nothing to allow",
                    HttpStatus.BAD_REQUEST);
        }

        var allow = new NameSimilarityAllow();
        allow.setExtensionId(lookalikeId);
        allow.setOtherExtensionId(otherId);
        allow.setField(closest.field());
        allow.setSimilarity(closest.similarity());
        allow.setExtensionValue(closest.lookalikeValue());
        allow.setOtherValue(closest.otherValue());
        allow.setReason(reason);
        allow.setDecidedById(admin.getId());
        allow.setDecidedAt(TimeUtil.getCurrentUTC());
        // Another admin may have allowed the pair since findPair.
        if (!similarity.insertAllow(allow)) {
            return alreadyAllowed;
        }

        var result = ResultJson.success("Allowed " + pairName);
        logs.logAction(admin, result);
        return result;
    }

    /**
     * The closest match of the pair, looked up from the look-alike's side and then from the other's: the
     * dashboard may have found it from either, and short values only match exactly when they are probed.
     */
    private @Nullable AllowedMatch closestEitherWay(Map<Long, ExtensionNames> names, long lookalikeId, long otherId) {
        var closest = closestMatch(names.get(lookalikeId), otherId);
        if (closest != null) {
            return new AllowedMatch(closest.field(), closest.similarity(), closest.probeValue(), closest.value());
        }
        closest = closestMatch(names.get(otherId), lookalikeId);
        return closest == null
                ? null
                : new AllowedMatch(closest.field(), closest.similarity(), closest.value(), closest.probeValue());
    }

    /** The closest match of the probed extension with the other, looked up for the other alone. */
    private @Nullable Match closestMatch(@Nullable ExtensionNames probed, long otherId) {
        if (probed == null) {
            return null;
        }
        var probe = check.probe(probed.namespaceName(), probed.name(), probed.displayNames())
                .onlyExtensions(List.of(otherId));
        return check.findMatches(probe).stream().min(NameSimilarityCheckService.CLOSEST_FIRST).orElse(null);
    }

    /** Remove an allow, so the pair is reported again while the two still resemble each other. */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson removeAllow(long id, UserData admin) throws ErrorResultException {
        var allow = allows.findById(id)
                .orElseThrow(() -> new ErrorResultException("Allowed pair not found: " + id, HttpStatus.NOT_FOUND));
        var names = byId(similarity.findNames(List.of(allow.getExtensionId(), allow.getOtherExtensionId())));
        allows.delete(allow);

        var result = ResultJson.success(
                "Removed the allow of " + toId(names.get(allow.getExtensionId()))
                        + " alongside " + toId(names.get(allow.getOtherExtensionId())));
        logs.logAction(admin, result);
        return result;
    }

    /** The allowed look-alikes, the most recently allowed first, each with every pair it was allowed in. */
    @Transactional
    public NameSquattingJson.Allowlist findAllowlist(int page, int size) {
        var slice = allows.findAllowedExtensionIds(PageRequest.of(page, size));
        var pageIds = slice.getContent();
        var hasMore = slice.hasNext();

        var pageAllows = allows.findByExtensionIdInOrderByDecidedAtDesc(pageIds);
        var involved = new HashSet<Long>(pageIds);
        pageAllows.forEach(allow -> involved.add(allow.getOtherExtensionId()));
        var names = byId(similarity.findNames(involved));
        var deciders = new HashMap<Long, @Nullable String>();

        var byExtension = pageAllows.stream().collect(Collectors.groupingBy(NameSimilarityAllow::getExtensionId));
        var groups = pageIds.stream()
                .filter(names::containsKey)
                .map(
                        extensionId -> new NameSquattingJson.AllowlistGroup(
                                toJson(names.get(extensionId)),
                                byExtension.getOrDefault(extensionId, List.of()).stream()
                                        .filter(allow -> names.containsKey(allow.getOtherExtensionId()))
                                        .map(allow -> toJson(allow, names, deciders))
                                        .toList()))
                .toList();
        return new NameSquattingJson.Allowlist(groups, page, hasMore);
    }

    private NameSquattingJson.Allow toJson(
            NameSimilarityAllow allow,
            Map<Long, ExtensionNames> names,
            Map<Long, @Nullable String> deciders
    ) {
        var extension = names.get(allow.getExtensionId());
        var other = names.get(allow.getOtherExtensionId());
        var renamed = !hasValue(extension, allow.getField(), allow.getExtensionValue())
                || !hasValue(other, allow.getField(), allow.getOtherValue());
        String decidedBy = null;
        if (allow.getDecidedById() != null) {
            decidedBy = deciders.computeIfAbsent(allow.getDecidedById(), id -> {
                var user = entityManager.find(UserData.class, id);
                return user != null ? user.getLoginName() : null;
            });
        }
        return new NameSquattingJson.Allow(
                allow.getId(),
                toJson(other),
                allow.getField().name(),
                allow.getSimilarity(),
                allow.getExtensionValue(),
                allow.getOtherValue(),
                renamed,
                allow.getReason(),
                decidedBy,
                TimeUtil.toUTCString(allow.getDecidedAt()));
    }

    /** Whether PostgreSQL cancelled the statement for running out of its statement timeout. */
    static boolean isStatementTimeout(Throwable exc) {
        for (var cause = exc; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the extension still has the value it had for the field when the pair was allowed. */
    static boolean hasValue(ExtensionNames names, NameSimilarityField field, String value) {
        return switch (field) {
            case NAME -> names.name().equalsIgnoreCase(value);
            case NAMESPACE -> names.namespaceName().equalsIgnoreCase(value);
            case DISPLAY_NAME -> {
                var normalized = NameSimilarityJooqRepository.normalizeDisplayName(value);
                yield names.displayNames().stream()
                        .map(NameSimilarityJooqRepository::normalizeDisplayName)
                        .anyMatch(normalized::equals);
            }
        };
    }

    private Extension findExtension(@Nullable String namespace, @Nullable String name) throws ErrorResultException {
        var extension = namespace == null || name == null ? null : repositories.findExtension(name, namespace);
        if (extension == null) {
            throw new ErrorResultException("Extension not found: " + namespace + "." + name, HttpStatus.NOT_FOUND);
        }
        return extension;
    }

    private static Map<Long, ExtensionNames> byId(List<ExtensionNames> names) {
        return names.stream().collect(Collectors.toMap(ExtensionNames::id, Function.identity()));
    }

    private static String toId(@Nullable ExtensionNames names) {
        return names == null ? "a removed extension" : names.namespaceName() + "." + names.name();
    }

    private static NameSquattingJson.Extension toJson(ExtensionNames names) {
        var displayName = names.displayNames().stream()
                .min(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .orElse(null);
        return new NameSquattingJson.Extension(
                names.namespaceName(),
                names.name(),
                displayName,
                names.downloadCount(),
                names.publishedDate() != null ? TimeUtil.toUTCString(names.publishedDate()) : null,
                names.activeVersionCount(),
                names.active());
    }

    private static NameSquattingJson.Lookalike toJson(Pair pair, ExtensionNames lookalike) {
        return new NameSquattingJson.Lookalike(
                toJson(lookalike),
                pair.matches(),
                pair.namespaceSimilarity(),
                pair.flaggedAt() != null ? TimeUtil.toUTCString(pair.flaggedAt()) : null);
    }

    /** How a pair being allowed compares, with each value on its own side. */
    private record AllowedMatch(
            NameSimilarityField field,
            float similarity,
            String lookalikeValue,
            String otherValue
    ) {}

    /** A candidate's matches with one other extension, as looked up from the candidate's side. */
    private record Found(Candidate candidate, ExtensionNames candidateNames, long otherId, List<Match> matches) {}

    /** A look-alike, the extension it resembles that was published first, and how they compare. */
    private record Pair(
            ExtensionNames anchor,
            long lookalikeId,
            List<NameSquattingJson.Match> matches,
            Match closest,
            float namespaceSimilarity,
            @Nullable LocalDateTime flaggedAt
    ) {
        static Pair of(Found found, ExtensionNames other) {
            var candidateFirst = PUBLISHED_FIRST.compare(found.candidateNames(), other) < 0;
            var sorted = found.matches().stream().sorted(NameSimilarityCheckService.CLOSEST_FIRST).toList();
            // Matches are looked up from the candidate's side: value is the other extension's.
            var matches = sorted.stream()
                    .map(
                            m -> candidateFirst
                                    ? new NameSquattingJson.Match(
                                            m.field().name(),
                                            m.probeValue(),
                                            m.value(),
                                            m.similarity(),
                                            m.exact())
                                    : new NameSquattingJson.Match(
                                            m.field().name(),
                                            m.value(),
                                            m.probeValue(),
                                            m.similarity(),
                                            m.exact()))
                    .toList();
            return new Pair(
                    candidateFirst ? found.candidateNames() : other,
                    candidateFirst ? other.id() : found.candidateNames().id(),
                    matches,
                    sorted.getFirst(),
                    sorted.getFirst().namespaceSimilarity(),
                    found.candidate().flaggedAt());
        }

        /** Of two pairs of one look-alike, the one with the earlier anchor, with the later of their flags. */
        Pair earlierAnchor(Pair other) {
            var earlier = PUBLISHED_FIRST.compare(anchor, other.anchor) <= 0 ? this : other;
            var flag = flaggedAt == null || (other.flaggedAt != null && other.flaggedAt.isAfter(flaggedAt))
                    ? other.flaggedAt
                    : flaggedAt;
            return new Pair(
                    earlier.anchor,
                    earlier.lookalikeId,
                    earlier.matches,
                    earlier.closest,
                    earlier.namespaceSimilarity,
                    flag);
        }
    }
}
