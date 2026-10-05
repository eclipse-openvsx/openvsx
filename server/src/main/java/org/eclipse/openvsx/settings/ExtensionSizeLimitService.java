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
package org.eclipse.openvsx.settings;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;

@Service
@CacheConfig(cacheManager = "localCacheManager")
public class ExtensionSizeLimitService {

    private static final String CACHE_KEY_CEILING = "'extension-size-ceiling'";

    private final SettingsService settings;
    private final ExtensionSizeOverrideRepository overrides;
    private final RepositoryService repositories;

    public ExtensionSizeLimitService(
            SettingsService settings,
            ExtensionSizeOverrideRepository overrides,
            RepositoryService repositories
    ) {
        this.settings = settings;
        this.overrides = overrides;
        this.repositories = repositories;
    }

    public long getDefaultLimit() {
        return settings.getMaxExtensionSize();
    }

    /**
     * Largest package any namespace may publish. Used as the stream-time cap, before the namespace is
     * known. Evicted by {@link SettingsCache#clear()}, which every settings write triggers.
     */
    @Cacheable(value = CacheService.CACHE_SETTING, key = CACHE_KEY_CEILING)
    public long getCeiling() {
        var defaultLimit = getDefaultLimit();
        var highest = overrides.findHighestMaxSize();
        return highest == null ? defaultLimit : Math.max(defaultLimit, highest);
    }

    /** Applicable limit for one package: extension override, else namespace override, else default. */
    public long resolveLimit(String namespaceName, String extensionName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            return getDefaultLimit();
        }

        var extension = repositories.findExtension(extensionName, namespace);
        var extensionId = extension == null ? null : extension.getId();

        long namespaceWide = -1;
        for (var override : overrides.findByScope(namespace.getId(), extensionId)) {
            if (override.getScopeExtension() != null) {
                return override.getMaxSize();
            }
            namespaceWide = override.getMaxSize();
        }
        return namespaceWide >= 0 ? namespaceWide : getDefaultLimit();
    }

    /**
     * Drops the cached ceiling across the cluster. For workflows that remove overrides through the
     * database cascade rather than {@link #deleteOverride}, which is otherwise the only deletion path
     * that invalidates.
     */
    public void invalidateCeiling() {
        settings.invalidateCache();
    }

    public List<ExtensionSizeOverride> listOverrides() {
        return overrides.findAllByOrderByIdAsc();
    }

    // The scope check below and the insert are a check-then-act pair with no serialization point
    // between them, so two concurrent requests for the same scope can both pass the check under READ
    // COMMITTED. extension_size_override_scope_idx closes that at the database level; a losing request
    // retries once, re-reads the now-committed sibling row and reports "already exists" rather than
    // failing with a 500 the caller cannot act on.
    @Retryable(
        includes = DataIntegrityViolationException.class,
        predicate = DuplicateScopePredicate.class,
        maxRetries = 1,
        delay = 100
    )
    @Transactional(rollbackOn = ErrorResultException.class)
    public ExtensionSizeOverride createOverride(
            String namespaceName,
            @Nullable String extensionName,
            long maxSize
    ) {
        var namespace = requireNamespace(namespaceName);
        // Creation only: an existing override deliberately survives the namespace's verification
        // lapsing, so resolution must not apply this check.
        if (!repositories.isVerified(namespace)) {
            throw new ErrorResultException(
                    "Namespace is not verified: " + namespaceName,
                    HttpStatus.BAD_REQUEST);
        }
        var extension = extensionName == null ? null : requireExtension(namespace, extensionName);
        requireValidSize(maxSize);

        var extensionId = extension == null ? null : extension.getId();
        for (var existing : overrides.findByScope(namespace.getId(), extensionId)) {
            var sameScope = extension == null
                    ? existing.getScopeExtension() == null
                    : existing.getScopeExtension() != null;
            if (sameScope) {
                throw new ErrorResultException(
                        "A size override already exists for " + describeScope(namespaceName, extensionName) + ".",
                        HttpStatus.BAD_REQUEST);
            }
        }

        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setScopeExtension(extension);
        override.setMaxSize(maxSize);
        var saved = overrides.save(override);
        settings.invalidateCache();
        return saved;
    }

    /** The updated override together with the size it replaced, so the change can be audited. */
    public record UpdatedOverride(ExtensionSizeOverride override, long previousMaxSize) {}

    /**
     * Transactional so the row is loaded and written in one persistence context: the entity stays
     * managed, the setter is what persists it, and the size this reports as the previous one is the
     * one the write actually replaced. Loading and saving in separate transactions made the write a
     * merge of a detached copy, which rewrites every column from a snapshot another request may
     * already have moved on from - see the rule on {@code EntityManager.merge} in {@code AGENTS.md}.
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public UpdatedOverride updateOverride(long id, long maxSize) {
        var override = requireOverride(id);
        requireValidSize(maxSize);
        // Captured before the setter, which is what persists the new value.
        var previousMaxSize = override.getMaxSize();
        override.setMaxSize(maxSize);
        settings.invalidateCache();
        return new UpdatedOverride(override, previousMaxSize);
    }

    /** Transactional for the same reason as {@link #updateOverride}: the row is removed as loaded. */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ExtensionSizeOverride deleteOverride(long id) {
        var override = requireOverride(id);
        overrides.delete(override);
        settings.invalidateCache();
        return override;
    }

    /**
     * Carries overrides across a namespace change, which moves every extension to {@code newNamespace}
     * without touching this table.
     * <p>
     * An extension-scoped override has to follow its extension: the two scope ids would otherwise
     * disagree and {@link #resolveLimit} would never find it again - silently, since an unreachable
     * override reads exactly like no override. No conflict is possible there, because the extension
     * was not in the new namespace to have one.
     * <p>
     * The namespace-wide override is the old namespace's own, so it only moves when that identity is
     * going away and the new namespace has none of its own; otherwise the new namespace's own setting
     * stands, exactly as memberships are merged. Anything left behind goes with the old row.
     * <p>
     * {@code MANDATORY} because the re-pointing below is a bare setter: it persists through dirty
     * checking on entities the caller's transaction keeps managed, and would be discarded in silence
     * without one. Joining the caller's transaction is also what makes the move and the namespace
     * change it belongs to commit together.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void moveOverridesToNamespace(Namespace oldNamespace, Namespace newNamespace, boolean oldNamespaceRemoved) {
        var existing = overrides.findByScopeNamespace(oldNamespace);
        if (existing.isEmpty()) {
            return;
        }

        var newHasNamespaceWide = overrides.findByScopeNamespace(newNamespace).stream()
                .anyMatch(override -> override.getScopeExtension() == null);
        for (var override : existing) {
            var isNamespaceWide = override.getScopeExtension() == null;
            if (!isNamespaceWide || (oldNamespaceRemoved && !newHasNamespaceWide)) {
                override.setScopeNamespace(newNamespace);
            }
        }

        // Removing the old namespace cascades whatever stayed behind, which can lower the ceiling.
        settings.invalidateCache();
    }

    private Namespace requireNamespace(String namespaceName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Unknown namespace: " + namespaceName, HttpStatus.BAD_REQUEST);
        }
        return namespace;
    }

    private Extension requireExtension(Namespace namespace, String extensionName) {
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null) {
            throw new ErrorResultException(
                    "Unknown extension: " + describeScope(namespace.getName(), extensionName),
                    HttpStatus.BAD_REQUEST);
        }
        return extension;
    }

    private ExtensionSizeOverride requireOverride(long id) {
        return overrides.findById(id)
                .orElseThrow(() -> new ErrorResultException("Unknown size override: " + id, HttpStatus.NOT_FOUND));
    }

    private void requireValidSize(long maxSize) {
        if (maxSize <= 0) {
            throw new ErrorResultException("The size override must be greater than zero.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Retries only the concurrent-create collision, leaving every other integrity failure to fail. */
    public static class DuplicateScopePredicate implements MethodRetryPredicate {

        // Unique index on extension_size_override(scope_namespace_id, COALESCE(scope_extension_id, 0)),
        // see the V1_79 migration.
        private static final String UNIQUE_SCOPE = "extension_size_override_scope_idx";

        private static final Logger logger = LoggerFactory.getLogger(DuplicateScopePredicate.class);

        @Override
        public boolean shouldRetry(Method method, Throwable exception) {
            for (var cause = exception; cause != null; cause = cause.getCause()) {
                var isDuplicateScope = cause instanceof ConstraintViolationException violation
                        && UNIQUE_SCOPE.equals(violation.getConstraintName());
                // The constraint name is not always available, so fall back to the reported message.
                isDuplicateScope |= cause.getMessage() != null
                        && cause.getMessage().contains('"' + UNIQUE_SCOPE + '"');

                if (isDuplicateScope) {
                    logger.warn("Size override was created concurrently, retrying to report the duplicate", exception);
                    return true;
                }
            }

            return false;
        }
    }

    private static String describeScope(String namespaceName, @Nullable String extensionName) {
        return extensionName == null ? namespaceName : namespaceName + "." + extensionName;
    }
}
