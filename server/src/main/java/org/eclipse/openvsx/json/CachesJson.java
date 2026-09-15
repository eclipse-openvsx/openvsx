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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The caches registered in the application, for the admin dashboard.
 */
@JsonInclude(Include.NON_NULL)
public class CachesJson extends ResultJson {

    public static CachesJson error(String message) {
        var result = new CachesJson();
        result.setError(message);
        return result;
    }

    @Schema(
        description = "Whether the caches were built to count hits and misses. When false every"
                + " statistic below is absent, because nothing is counting rather than because"
                + " nothing is happening."
    )
    private boolean statisticsEnabled;

    @Schema(description = "Whether a CDN is configured that this registry can purge")
    private boolean cdnPurgeEnabled;

    @Schema(description = "Every cache of every cache manager, ordered by name")
    private List<CacheJson> caches;

    public boolean isCdnPurgeEnabled() {
        return cdnPurgeEnabled;
    }

    public void setCdnPurgeEnabled(boolean cdnPurgeEnabled) {
        this.cdnPurgeEnabled = cdnPurgeEnabled;
    }

    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    public List<CacheJson> getCaches() {
        return caches;
    }

    public void setCaches(List<CacheJson> caches) {
        this.caches = caches;
    }

    /**
     * One cache. A measurement is absent rather than zero when the implementation behind the cache
     * cannot report it, so "not measured" stays distinguishable from "nothing there".
     */
    @JsonInclude(Include.NON_NULL)
    public static class CacheJson {

        @Schema(
            description = "Bean name of the cache manager this cache belongs to. Cache names are unique only"
                    + " within a manager, so the manager and the name together are what identify a cache."
        )
        private String manager;

        @Schema(description = "Cache name, unique only within its manager")
        private String name;

        @Schema(
            description = "What backs the cache, which decides what can be measured",
            allowableValues = { "caffeine", "jcache", "redis" }
        )
        private String implementation;

        @Schema(description = "Entries held; absent when the implementation cannot report it")
        @Nullable
        private Long entries;

        @Schema(description = "Lookups served from the cache; absent when statistics are not recorded")
        @Nullable
        private Long hits;

        @Schema(description = "Lookups that missed; absent when statistics are not recorded")
        @Nullable
        private Long misses;

        @Schema(description = "Hits over lookups, 0 to 1; absent when nothing has been looked up yet")
        @Nullable
        private Double hitRate;

        @Schema(description = "Entries evicted by size or expiry; absent when statistics are not recorded")
        @Nullable
        private Long evictions;

        public String getManager() {
            return manager;
        }

        public void setManager(String manager) {
            this.manager = manager;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public @Nullable Long getEntries() {
            return entries;
        }

        public void setEntries(@Nullable Long entries) {
            this.entries = entries;
        }

        public @Nullable Long getHits() {
            return hits;
        }

        public void setHits(@Nullable Long hits) {
            this.hits = hits;
        }

        public @Nullable Long getMisses() {
            return misses;
        }

        public void setMisses(@Nullable Long misses) {
            this.misses = misses;
        }

        public @Nullable Double getHitRate() {
            return hitRate;
        }

        public void setHitRate(@Nullable Double hitRate) {
            this.hitRate = hitRate;
        }

        public @Nullable Long getEvictions() {
            return evictions;
        }

        public void setEvictions(@Nullable Long evictions) {
            this.evictions = evictions;
        }
    }
}
