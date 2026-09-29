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
package org.eclipse.openvsx.ratelimit;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.entities.TierType;
import org.eclipse.openvsx.ratelimit.cache.RateLimitCacheService;
import org.eclipse.openvsx.repositories.RepositoryService;

@Service
public class TierService {

    private final RepositoryService repositories;

    public TierService(RepositoryService repositories) {
        this.repositories = repositories;
    }

    @Cacheable(
        value = RateLimitCacheService.CACHE_TIER,
        key = "'#' + #root.methodName",
        cacheManager = RateLimitCacheService.CACHE_MANAGER
    )
    public Optional<Tier> getFreeTier() {
        var freeTiers = repositories.findTiersByTierType(TierType.FREE);
        if (!freeTiers.isEmpty()) {
            return Optional.of(freeTiers.getFirst());
        } else {
            return Optional.empty();
        }
    }

    @Cacheable(
        value = RateLimitCacheService.CACHE_TIER,
        key = "'#' + #root.methodName",
        cacheManager = RateLimitCacheService.CACHE_MANAGER
    )
    public Optional<Tier> getSafetyTier() {
        var safetyTiers = repositories.findTiersByTierType(TierType.SAFETY);
        if (!safetyTiers.isEmpty()) {
            return Optional.of(safetyTiers.getFirst());
        } else {
            return Optional.empty();
        }
    }
}
