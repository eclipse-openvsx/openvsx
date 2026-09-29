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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.EnforcementState;
import org.eclipse.openvsx.entities.RefillStrategy;
import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.ratelimit.cache.ConfigurationChanged;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;

@Service
@ConditionalOnBean(RateLimitConfig.class)
public class RateLimitService {

    private final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final ProxyManager<byte[]> proxyManager;
    private final Map<Customer, BucketConfigurationWrapper> configurationByCustomer;
    private final Customer anonymousCustomer;

    public RateLimitService(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
        this.configurationByCustomer = new ConcurrentHashMap<>();

        // We need to set up a dummy customer for anonymous users as key for the configuration map
        // as ConcurrentHashMap does not allow null keys or values.
        var customer = new Customer();
        customer.setName("internalAnonymousCustomer");
        this.anonymousCustomer = customer;
    }

    public record MinimumBandwidth(long availableTokens, int index) {}

    /**
     * A wrapper for {@link BucketConfiguration} objects as {@link ConcurrentHashMap}
     * does not support {@code null} values.
     */
    public record BucketConfigurationWrapper(
            @Nullable BucketConfiguration configuration,
            @Nullable MinimumBandwidth minimumBandwidth
    ) {}

    public record BucketPair(@Nullable Bucket bucket, MinimumBandwidth minimumBandwidth) {
        public static BucketPair empty() {
            return new BucketPair(null, null);
        }

        public static BucketPair of(@NonNull Bucket bucket, MinimumBandwidth minimumBandwidth) {
            return new BucketPair(bucket, minimumBandwidth);
        }
    }

    @EventListener
    public void invalidateCustomerCache(ConfigurationChanged event) {
        logger.debug("Invalidating bucket configuration cache");
        configurationByCustomer.clear();
    }

    public BucketPair getBucket(ResolvedIdentity identity) {
        var wrapper = getBucketConfiguration(identity);
        var newConfig = wrapper.configuration;
        if (newConfig == null) {
            return BucketPair.empty();
        }

        var cacheKey = identity.cacheKey().getBytes(StandardCharsets.UTF_8);
        var currentConfiguration = proxyManager.getProxyConfiguration(cacheKey);
        var bucket = proxyManager.builder().build(cacheKey, () -> newConfig);
        if (currentConfiguration.isPresent() && !currentConfiguration.get().equals(newConfig)) {
            logger.debug("Replace configuration for bucket {}", identity.cacheKey());
            bucket.replaceConfiguration(newConfig, TokensInheritanceStrategy.AS_IS);
        }

        return BucketPair.of(bucket, wrapper.minimumBandwidth);
    }

    private BucketConfigurationWrapper getBucketConfiguration(ResolvedIdentity identity) {
        var customer = identity.customer() != null ? identity.customer() : anonymousCustomer;
        return configurationByCustomer.computeIfAbsent(
                customer,
                (_) -> {
                    var config = createBucketConfiguration(identity);
                    var minimumBandwidth = config != null ? calculateMinimumBandwidth(config) : null;
                    return new BucketConfigurationWrapper(config, minimumBandwidth);
                });
    }

    /**
     * Calculate the minimum bandwidth from a {@code BucketConfiguration}
     */
    private MinimumBandwidth calculateMinimumBandwidth(BucketConfiguration configuration) {
        // get the bandwidth with the minimum normalized capacity
        var bandwidths = configuration.getBandwidths();
        var minCapacity = Long.MAX_VALUE;
        var minIndex = -1;
        for (int i = 0; i < bandwidths.length; i++) {
            var bandwidth = bandwidths[i];
            var capacityPerNanos = bandwidth.getCapacity() / bandwidth.getRefillPeriodNanos();
            if (capacityPerNanos < minCapacity) {
                minCapacity = capacityPerNanos;
                minIndex = i;
            }
        }

        var availableTokens = minIndex != -1 ? bandwidths[minIndex].getCapacity() : 0;
        return new MinimumBandwidth(availableTokens, minIndex);
    }

    private @Nullable BucketConfiguration createBucketConfiguration(ResolvedIdentity identity) {
        var builder = BucketConfiguration.builder();

        var hasLimits = false;
        var useCustomerBandwidth = identity.cacheKey().startsWith("customer_");

        // if this request is coming from an identified customer
        if (identity.customer() != null && useCustomerBandwidth) {
            var customer = identity.customer();
            if (customer.getState() == EnforcementState.ENFORCEMENT) {
                builder.addLimit(getBandWidth(customer.getTier()));
                hasLimits = true;
            }
        }

        // add the free tier bandwidth for any other requests if available
        if (!useCustomerBandwidth && identity.freeTier() != null) {
            builder.addLimit(getBandWidth(identity.freeTier()));
            hasLimits = true;
        }

        // always add a safety bandwidth if available
        if (identity.safetyTier() != null) {
            builder.addLimit(getBandWidth(identity.safetyTier()));
            hasLimits = true;
        }

        return hasLimits ? builder.build() : null;
    }

    private Bandwidth getBandWidth(Tier tier) {
        var fillStage = Bandwidth.builder().capacity(tier.getCapacity());

        var buildStage = switch (tier.getRefillStrategy()) {
            case RefillStrategy.GREEDY -> fillStage.refillGreedy(tier.getCapacity(), tier.getDuration());
            case RefillStrategy.INTERVAL -> fillStage.refillIntervally(tier.getCapacity(), tier.getDuration());
        };

        return buildStage.build();
    }
}
