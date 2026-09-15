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
package org.eclipse.openvsx.cdn;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.eclipse.openvsx.web.SurrogateKey;

/**
 * Tells the CDN what changed, in the same terms the responses are tagged with.
 * <p>
 * Called from {@code CacheService} wherever this instance evicts its own caches: those are the
 * points at which something a cache is holding stopped being true, and a CDN is one more cache.
 * <p>
 * Two things it takes care of for the caller:
 * <ul>
 *     <li><b>After the commit, never inside it.</b> The eviction points run inside a transaction.
 *     A purge sent there races the commit: the CDN refetches, gets the row as it was, and caches
 *     that - leaving it staler than if nothing had been purged at all.</li>
 *     <li><b>One purge per transaction.</b> Publishing a version evicts the extension JSONs, the
 *     latest version and the namespace details separately, which is three calls naming two keys.
 *     They are collected and sent once.</li>
 * </ul>
 * The purge itself is a job, so a CDN that is briefly unreachable is retried rather than losing the
 * purge, and a slow API never holds up a publish.
 */
@Service
public class CdnPurgeService {

    private static final String COLLECTED_KEYS = CdnPurgeService.class.getName() + ".keys";

    private static final Logger logger = LoggerFactory.getLogger(CdnPurgeService.class);

    private final CdnPurgeConfig config;
    private final JobRequestScheduler scheduler;

    public CdnPurgeService(CdnPurgeConfig config, JobRequestScheduler scheduler) {
        this.config = config;
        this.scheduler = scheduler;
    }

    /** Everything cached about one extension, and about the namespace it belongs to. */
    public void purgeExtension(String namespace, String extension) {
        collect(SurrogateKey.extension(namespace, extension));
    }

    /** Everything cached about one namespace, including every extension in it. */
    public void purgeNamespace(String namespace) {
        collect(SurrogateKey.namespace(namespace));
    }

    private void collect(String keys) {
        if (!config.isEnabled()) {
            return;
        }
        // A key set is a header value, several keys separated by a space; the CDN is told them one
        // by one, so that a single key purged twice in a transaction costs one entry, not two.
        var collected = keys(keys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // outside a transaction there is nothing to wait for and nothing to coalesce with
            enqueue(collected);
            return;
        }
        var pending = pendingKeys();
        pending.addAll(collected);
    }

    @SuppressWarnings("unchecked")
    private Set<String> pendingKeys() {
        var pending = (Set<String>) TransactionSynchronizationManager.getResource(COLLECTED_KEYS);
        if (pending == null) {
            pending = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(COLLECTED_KEYS, pending);
            TransactionSynchronizationManager.registerSynchronization(new PurgeOnCommit());
        }
        return pending;
    }

    private void enqueue(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        logger.debug("Scheduling CDN purge of {}", keys);
        scheduler.enqueue(new CdnPurgeJobRequest(List.copyOf(keys)));
    }

    private static Set<String> keys(String headerValue) {
        return new LinkedHashSet<>(List.of(headerValue.split(" ")));
    }

    /**
     * Sends what the transaction collected, once it has committed. A rolled back transaction purges
     * nothing: whatever it was going to change did not happen, and the CDN is not stale for it.
     */
    private class PurgeOnCommit implements TransactionSynchronization {

        @Override
        public void afterCompletion(int status) {
            var pending = (Set<?>) TransactionSynchronizationManager.unbindResourceIfPossible(COLLECTED_KEYS);
            if (status != STATUS_COMMITTED || pending == null) {
                return;
            }
            enqueue(pending.stream().map(String::valueOf).toList());
        }
    }
}
