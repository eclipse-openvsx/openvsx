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
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.NameSimilarityAllow;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.NameSimilarityAllowRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Candidate;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.ExtensionNames;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.NamespaceSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.NameSimilarityCheckConfig;
import org.eclipse.openvsx.scanning.NameSimilarityCheckService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * The name squatting admin dashboard: looks up again the extensions the NAME_SIMILARITY check flagged and
 * those sharing a display name across namespaces, lists the namespaces whose names resemble each other,
 * and keeps the allowlist of pairs that are not squatting on each other.
 * <p>
 * Nothing about a match is stored: every page looks its candidates up live, so a look-alike that was
 * deleted, renamed or allowed since it was flagged no longer shows. Namespace pairs are the exception:
 * finding them reads every namespace, so they are kept for {@link #NAMESPACE_PAIRS_TTL}, though allows
 * still apply at once.
 */
@Service
public class NameSquattingService {

    /** Candidates are looked up in batches until a page is full, giving up after this many. */
    static final int MAX_BATCHES = 4;

    static final int MAX_REASON_LENGTH = 2048;

    /**
     * How long the namespace pairs are reused before they are looked up again: a namespace created since
     * shows up at most this late, while allows apply at once.
     */
    static final Duration NAMESPACE_PAIRS_TTL = Duration.ofMinutes(1);

    /** How the namespaces view orders its pairs. */
    public enum NamespaceSort {
        /** The pairs whose target has the most downloads first. */
        DOWNLOADS,
        /** The pairs whose look-alike first published most recently first; look-alikes without any last. */
        NEWEST
    }

    /** The SQLState of a statement cancelled by statement_timeout, among other cancellations. */
    private static final String QUERY_CANCELED = "57014";

    /** The earliest publication date first; an unknown date counts as earliest. */
    static final Comparator<ExtensionNames> PUBLISHED_FIRST = Comparator
            .comparing(ExtensionNames::publishedDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingLong(ExtensionNames::id);

    private final NameSimilarityJooqRepository similarity;
    private final NamespaceSimilarityJooqRepository namespaceSimilarity;
    private final NameSimilarityAllowRepository allows;
    private final NameSimilarityCheckService check;
    private final NameSimilarityCheckConfig config;
    private final RepositoryService repositories;
    private final LogService logs;
    private final EntityManager entityManager;

    /**
     * Every namespace pair, allowed or not, under a single constant key. Local to this instance, so each
     * server looks the pairs up for itself; allows are filtered out per request instead.
     */
    private final Cache<Boolean, List<NamespaceSimilarityJooqRepository.Pair>> namespacePairs = Caffeine
            .newBuilder()
            .expireAfterWrite(NAMESPACE_PAIRS_TTL)
            .maximumSize(1)
            .build();

    public NameSquattingService(
            NameSimilarityJooqRepository similarity,
            NamespaceSimilarityJooqRepository namespaceSimilarity,
            NameSimilarityAllowRepository allows,
            NameSimilarityCheckService check,
            NameSimilarityCheckConfig config,
            RepositoryService repositories,
            LogService logs,
            EntityManager entityManager
    ) {
        this.similarity = similarity;
        this.namespaceSimilarity = namespaceSimilarity;
        this.allows = allows;
        this.check = check;
        this.config = config;
        this.repositories = repositories;
        this.logs = logs;
        this.entityManager = entityManager;
    }

    /**
     * The look-alikes of the candidates from {@code offset} on, grouped under the extension each resembles
     * that was published first. Groups follow the order of their most recently flagged candidate; a page
     * may hold more than {@code size} look-alikes, or fewer once candidates run out.
     * <p>
     * Deliberately not transactional: every lookup runs in a transaction of its own, so the statement
     * timeout one sets cannot outlive it, and one that is cancelled fails only itself.
     *
     * @throws ErrorResultException with 503 when a lookup ran out of its statement timeout
     */
    public NameSquattingJson.Collisions findCollisions(int offset, int size) throws ErrorResultException {
        try {
            return lookUpCollisions(offset, size);
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

    private NameSquattingJson.Collisions lookUpCollisions(int offset, int size) {
        var pairs = new LinkedHashMap<Long, Pair>();
        var cursor = offset;
        var hasMore = true;
        for (var batch = 0; batch < MAX_BATCHES && hasMore && pairs.size() < size; batch++) {
            var candidates = similarity.findCandidates(
                    NameSimilarityCheckService.CHECK_TYPE,
                    cursor,
                    size,
                    check.getStatementTimeout());
            cursor += candidates.size();
            hasMore = candidates.size() == size;
            collectPairs(candidates, pairs);
        }

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
        return new NameSquattingJson.Collisions(json, cursor, hasMore);
    }

    /**
     * Look the candidates up and add their pairs to {@code pairs}, keyed by look-alike, which keeps each
     * look-alike under the earliest published extension it resembles.
     */
    private void collectPairs(List<Candidate> candidates, Map<Long, Pair> pairs) {
        if (candidates.isEmpty()) {
            return;
        }
        var ids = candidates.stream().map(Candidate::extensionId).toList();
        var names = byId(similarity.findNames(ids));
        var allowed = check.allowedPartners(ids);

        var found = new ArrayList<Found>();
        for (var candidate : candidates) {
            var candidateNames = names.get(candidate.extensionId());
            if (candidateNames == null || !candidateNames.active()) {
                continue;
            }
            var partners = allowed.getOrDefault(candidate.extensionId(), Set.of());
            check.findMatches(candidateNames.namespaceName(), candidateNames.name(), candidateNames.displayNames())
                    .stream()
                    .filter(match -> !partners.contains(match.extensionId()))
                    .collect(Collectors.groupingBy(Match::extensionId))
                    .forEach((otherId, matches) -> found.add(new Found(candidate, candidateNames, otherId, matches)));
        }

        var others = byId(similarity.findNames(found.stream().map(Found::otherId).toList()));
        for (var match : found) {
            var other = others.get(match.otherId());
            if (other != null) {
                var pair = Pair.of(match, other);
                pairs.merge(pair.lookalikeId(), pair, Pair::earlierAnchor);
            }
        }
    }

    /** Allow the look-alike alongside the other extension, so the pair is no longer reported. */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson allow(NameSquattingJson.AllowRequest request, UserData admin) throws ErrorResultException {
        var reason = validReason(request.reason());
        var lookalike = findExtension(request.namespace(), request.extension());
        var other = findExtension(request.otherNamespace(), request.otherExtension());
        if (lookalike.getId() == other.getId()) {
            throw new ErrorResultException("An extension cannot be allowed alongside itself", HttpStatus.BAD_REQUEST);
        }
        if (allows.findPair(lookalike.getId(), other.getId()).isPresent()) {
            return ResultJson.success("Already allowed " + toId(lookalike) + " alongside " + toId(other));
        }

        var lookalikeNames = similarity.findNames(List.of(lookalike.getId())).getFirst();
        var closest = check
                .findMatches(lookalikeNames.namespaceName(), lookalikeNames.name(), lookalikeNames.displayNames())
                .stream()
                .filter(match -> match.extensionId() == other.getId())
                .min(NameSimilarityCheckService.CLOSEST_FIRST)
                .orElseThrow(
                        () -> new ErrorResultException(
                                toId(lookalike) + " no longer resembles " + toId(other)
                                        + ", so there is nothing to allow",
                                HttpStatus.BAD_REQUEST));

        var allow = new NameSimilarityAllow();
        allow.setExtensionId(lookalike.getId());
        allow.setOtherExtensionId(other.getId());
        allow.setField(closest.field());
        allow.setSimilarity(closest.similarity());
        allow.setLookalikeValue(closest.probeValue());
        allow.setOtherValue(closest.value());
        allow.setReason(reason);
        allow.setDecidedById(admin.getId());
        allow.setDecidedAt(TimeUtil.getCurrentUTC());
        allows.save(allow);

        var result = ResultJson.success("Allowed " + toId(lookalike) + " alongside " + toId(other));
        logs.logAction(admin, result);
        return result;
    }

    /**
     * Remove an allow of extensions or of namespaces, so the pair is reported again while the two still
     * resemble each other.
     *
     * @throws ErrorResultException with 404 when there is no allow with the id
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson removeAllow(long id, UserData admin) throws ErrorResultException {
        var allow = allows.findById(id)
                .orElseThrow(() -> new ErrorResultException("Allowed pair not found: " + id, HttpStatus.NOT_FOUND));
        if (allow.getField() == NameSimilarityField.NAMESPACE) {
            allows.delete(allow);
            var result = ResultJson.success(
                    "Removed the allow of namespace " + allow.getLookalikeValue() + " alongside "
                            + allow.getOtherValue());
            logs.logAction(admin, result);
            return result;
        }
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
        // One more than asked tells whether there is a next page.
        var extensionIds = allows.findAllowedExtensionIds(PageRequest.of(page, size + 1));
        var hasMore = extensionIds.size() > size;
        var pageIds = hasMore ? extensionIds.subList(0, size) : extensionIds;

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
        var renamed = !hasValue(extension, allow.getField(), allow.getLookalikeValue())
                || !hasValue(other, allow.getField(), allow.getOtherValue());
        return new NameSquattingJson.Allow(
                allow.getId(),
                toJson(other),
                allow.getField().name(),
                allow.getSimilarity(),
                allow.getLookalikeValue(),
                allow.getOtherValue(),
                renamed,
                allow.getReason(),
                decidedBy(allow, deciders),
                TimeUtil.toUTCString(allow.getDecidedAt()));
    }

    /** The login name of the admin who allowed the pair, looked up once per admin. */
    private @Nullable String decidedBy(NameSimilarityAllow allow, Map<Long, @Nullable String> deciders) {
        if (allow.getDecidedById() == null) {
            return null;
        }
        return deciders.computeIfAbsent(allow.getDecidedById(), id -> {
            var user = entityManager.find(UserData.class, id);
            return user != null ? user.getLoginName() : null;
        });
    }

    /**
     * A page of the namespace pairs no admin allowed, each oriented with the namespace with more downloads
     * as target (see {@link #TARGET_FIRST}). A pair counts as popular when its target has at least
     * {@code ovsx.scanning.name-similarity.popular-namespace-downloads}, and
     * {@code popular-namespace-ratio} times the look-alike's.
     * <p>
     * The pairs come from a lookup reused for {@link #NAMESPACE_PAIRS_TTL}, but the allowlist is read on
     * every call, so a pair allowed or no longer allowed shows the change on the next page.
     *
     * @param popularOnly whether to leave out the pairs that do not count as popular; the totals of the
     *                    result count both
     * @param sort        the order of the pairs, ties broken by target and then look-alike name
     * @param offset      how many pairs of that order to skip
     * @param size        the most pairs to return
     * @throws ErrorResultException with 503 when the lookup ran out of its statement timeout
     */
    public NameSquattingJson.NamespacePairs findNamespacePairs(
            boolean popularOnly,
            NamespaceSort sort,
            int offset,
            int size
    ) throws ErrorResultException {
        List<NamespaceSimilarityJooqRepository.Pair> pairs;
        try {
            pairs = namespacePairs.get(
                    Boolean.TRUE,
                    key -> namespaceSimilarity.findSimilarPairs(check.getStatementTimeout()));
        } catch (RuntimeException exc) {
            if (!isStatementTimeout(exc)) {
                throw exc;
            }
            throw new ErrorResultException(
                    "Looking up namespace pairs took longer than " + check.getStatementTimeout().toSeconds()
                            + " s and was stopped. Try again, or raise ovsx.scanning.name-similarity.statement-timeout.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        var allowed = allows.findByField(NameSimilarityField.NAMESPACE).stream()
                .map(allow -> pairKey(allow.getNamespaceId(), allow.getOtherNamespaceId()))
                .collect(Collectors.toSet());
        var shown = pairs.stream()
                .filter(pair -> !allowed.contains(pairKey(pair.first().id(), pair.second().id())))
                .map(this::orient)
                .toList();
        var popular = (int) shown.stream().filter(OrientedPair::popular).count();
        var page = shown.stream()
                .filter(pair -> !popularOnly || pair.popular())
                .sorted(sort == NamespaceSort.NEWEST ? NEWEST_LOOKALIKE_FIRST : MOST_DOWNLOADED_FIRST)
                .skip(offset)
                .limit(size + 1L)
                .map(NameSquattingService::toJson)
                .toList();
        var hasMore = page.size() > size;
        return new NameSquattingJson.NamespacePairs(
                hasMore ? page.subList(0, size) : page,
                shown.size(),
                popular,
                offset,
                hasMore);
    }

    /**
     * Forget the namespace pairs, so the next page looks them up again rather than waiting out
     * {@link #NAMESPACE_PAIRS_TTL}. For tests that change namespaces between pages.
     */
    void forgetNamespacePairs() {
        namespacePairs.invalidateAll();
    }

    /**
     * Allow two namespaces alongside each other, so the pair is no longer reported in either view. The
     * names are stored as they are now, with their similarity, so the allowlist still reads after a rename.
     * Allowing a pair already allowed, either way round, succeeds without storing it again.
     *
     * @throws ErrorResultException with 404 when either namespace does not exist, or with 400 when they
     *                              are the same namespace, do not resemble each other by the rule of
     *                              {@link NamespaceSimilarityJooqRepository#compare}, or the reason is
     *                              longer than {@link #MAX_REASON_LENGTH}
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson allowNamespaces(NameSquattingJson.NamespaceAllowRequest request, UserData admin)
            throws ErrorResultException {
        var reason = validReason(request.reason());
        var lookalike = findNamespace(request.namespace());
        var other = findNamespace(request.otherNamespace());
        if (lookalike.getId() == other.getId()) {
            throw new ErrorResultException("A namespace cannot be allowed alongside itself", HttpStatus.BAD_REQUEST);
        }
        if (allows.findNamespacePair(lookalike.getId(), other.getId()).isPresent()) {
            return ResultJson.success(
                    "Already allowed namespace " + lookalike.getName() + " alongside " + other.getName());
        }
        var comparison = namespaceSimilarity.compare(lookalike.getName(), other.getName());
        if (!comparison.resembles()) {
            throw new ErrorResultException(
                    "Namespace " + lookalike.getName() + " does not resemble " + other.getName()
                            + ", so there is nothing to allow",
                    HttpStatus.BAD_REQUEST);
        }

        var allow = new NameSimilarityAllow();
        allow.setNamespaceId(lookalike.getId());
        allow.setOtherNamespaceId(other.getId());
        allow.setField(NameSimilarityField.NAMESPACE);
        allow.setSimilarity(comparison.similarity());
        allow.setLookalikeValue(lookalike.getName());
        allow.setOtherValue(other.getName());
        allow.setReason(reason);
        allow.setDecidedById(admin.getId());
        allow.setDecidedAt(TimeUtil.getCurrentUTC());
        allows.save(allow);

        var result = ResultJson.success(
                "Allowed namespace " + lookalike.getName() + " alongside " + other.getName());
        logs.logAction(admin, result);
        return result;
    }

    /**
     * The allowed pairs of namespaces, the most recently allowed first, under the names they had when
     * allowed. Unlike the extension allowlist they are not grouped: each pair is one entry.
     *
     * @param page the page, from 0
     * @param size the most pairs per page; one more is fetched to tell whether another page follows
     */
    @Transactional
    public NameSquattingJson.NamespaceAllowlist findNamespaceAllowlist(int page, int size) {
        // One more than asked tells whether there is a next page.
        var found = allows.findByFieldOrderByDecidedAtDescIdDesc(
                NameSimilarityField.NAMESPACE,
                PageRequest.of(page, size + 1));
        var hasMore = found.size() > size;
        var deciders = new HashMap<Long, @Nullable String>();
        var json = (hasMore ? found.subList(0, size) : found).stream()
                .map(
                        allow -> new NameSquattingJson.NamespaceAllow(
                                allow.getId(),
                                allow.getLookalikeValue(),
                                allow.getOtherValue(),
                                allow.getSimilarity(),
                                allow.getReason(),
                                decidedBy(allow, deciders),
                                TimeUtil.toUTCString(allow.getDecidedAt())))
                .toList();
        return new NameSquattingJson.NamespaceAllowlist(json, page, hasMore);
    }

    /**
     * A namespace pair with the likelier target picked out.
     *
     * @param pair    the pair as looked up, for the measures of how the names compare
     * @param popular whether the target counts as popular next to the look-alike
     */
    private record OrientedPair(
            NamespaceSimilarityJooqRepository.Side target,
            NamespaceSimilarityJooqRepository.Side lookalike,
            NamespaceSimilarityJooqRepository.Pair pair,
            boolean popular
    ) {}

    /** Pick out the target of the pair by {@link #TARGET_FIRST} and tell whether it counts as popular. */
    private OrientedPair orient(NamespaceSimilarityJooqRepository.Pair pair) {
        var firstIsTarget = TARGET_FIRST.compare(pair.first(), pair.second()) <= 0;
        var target = firstIsTarget ? pair.first() : pair.second();
        var lookalike = firstIsTarget ? pair.second() : pair.first();
        var popular = target.downloads() >= config.getPopularNamespaceDownloads()
                && target.downloads() >= config.getPopularNamespaceRatio() * lookalike.downloads();
        return new OrientedPair(target, lookalike, pair, popular);
    }

    private static NameSquattingJson.NamespacePair toJson(OrientedPair oriented) {
        var pair = oriented.pair();
        return new NameSquattingJson.NamespacePair(
                toJson(oriented.target()),
                toJson(oriented.lookalike()),
                pair.editDistance(),
                pair.similarity(),
                pair.separatorOnly(),
                pair.sharedMember(),
                oriented.popular());
    }

    private static NameSquattingJson.NamespaceSummary toJson(NamespaceSimilarityJooqRepository.Side side) {
        return new NameSquattingJson.NamespaceSummary(
                side.name(),
                side.downloads(),
                side.activeExtensions(),
                side.firstPublished() != null ? TimeUtil.toUTCString(side.firstPublished()) : null);
    }

    /**
     * The likelier target of a pair first: more downloads, then the earlier first publication, a namespace
     * without any last, then the lower id so the order is total. The id says nothing of age: namespace ids
     * are handed out in blocks per server instance.
     */
    static final Comparator<NamespaceSimilarityJooqRepository.Side> TARGET_FIRST = Comparator
            .comparingLong(NamespaceSimilarityJooqRepository.Side::downloads)
            .reversed()
            .thenComparing(
                    NamespaceSimilarityJooqRepository.Side::firstPublished,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(NamespaceSimilarityJooqRepository.Side::id);

    /** {@link NamespaceSort#DOWNLOADS}: the most downloaded target first, then by names ignoring case. */
    private static final Comparator<OrientedPair> MOST_DOWNLOADED_FIRST = Comparator
            .comparingLong((OrientedPair pair) -> pair.target().downloads())
            .reversed()
            .thenComparing(pair -> pair.target().name().toLowerCase(Locale.ROOT))
            .thenComparing(pair -> pair.lookalike().name().toLowerCase(Locale.ROOT));

    /**
     * {@link NamespaceSort#NEWEST}: the look-alike whose first active extension was published most recently
     * first, look-alikes without one last, then as {@link #MOST_DOWNLOADED_FIRST}.
     */
    private static final Comparator<OrientedPair> NEWEST_LOOKALIKE_FIRST = Comparator
            .comparing(
                    (OrientedPair pair) -> pair.lookalike().firstPublished(),
                    Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(MOST_DOWNLOADED_FIRST);

    /**
     * Identifies a pair of namespaces whichever way round it is given. Both ids are set on a namespace
     * allow, which the database checks.
     */
    private static String pairKey(@Nullable Long first, @Nullable Long second) {
        return Math.min(first, second) + ":" + Math.max(first, second);
    }

    /**
     * The reason stripped of surrounding whitespace, {@code null} when blank.
     *
     * @throws ErrorResultException with 400 when it is longer than {@link #MAX_REASON_LENGTH}
     */
    private static @Nullable String validReason(@Nullable String reason) throws ErrorResultException {
        var stripped = reason == null || reason.isBlank() ? null : reason.strip();
        if (stripped != null && stripped.length() > MAX_REASON_LENGTH) {
            throw new ErrorResultException(
                    "The reason must not be longer than " + MAX_REASON_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        }
        return stripped;
    }

    /**
     * The namespace of the name, ignoring case as {@link RepositoryService#findNamespace} does.
     *
     * @throws ErrorResultException with 404 when there is none
     */
    private Namespace findNamespace(@Nullable String name) throws ErrorResultException {
        var namespace = name == null ? null : repositories.findNamespace(name);
        if (namespace == null) {
            throw new ErrorResultException("Namespace not found: " + name, HttpStatus.NOT_FOUND);
        }
        return namespace;
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

    private static String toId(Extension extension) {
        return extension.getNamespace().getName() + "." + extension.getName();
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
