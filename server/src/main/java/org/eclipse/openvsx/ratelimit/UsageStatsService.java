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

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Nonnull;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.DailyUsageStats;
import org.eclipse.openvsx.entities.UsageStats;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBean(RateLimitConfig.class)
public class UsageStatsService {

    private final static String USAGE_DATA_KEY  = "usage.customer";
    private final static int    WINDOW_MINUTES  = 5;
    private final static int    WINDOWS_PER_DAY = 288;
    private final static int    PERCENTILE      = 95;

    private final Logger logger = LoggerFactory.getLogger(UsageStatsService.class);

    private final RepositoryService repositories;
    private final CustomerService customerService;
    private final Cache<Object, Object> usageCache;
    private final JedisCluster jedisCluster;

    public UsageStatsService(
            RepositoryService repositories,
            CustomerService customerService,
            Cache<Object, Object> usageCache,
            JedisCluster jedisCluster
    ) {
        this.repositories = repositories;
        this.customerService = customerService;
        this.usageCache = usageCache;
        this.jedisCluster = jedisCluster;
    }

    public void incrementUsage(Customer customer) {
        var key = customer.getId();
        var window = getCurrentUsageWindow();

        var count = usageCache.asMap().compute(key + ":" + window, (_, v) -> v == null ? 1 : ((Long) v) + 1);
        logger.debug("Local usage count for {}: {}", customer.getName(), count);
    }

    @Scheduled(cron = "*/30 * * * * *")
    public void syncDistributedUsageData() {
        logger.debug("Updating distributed usage data from local cache");
        var cacheMap = usageCache.asMap();
        for (var key : cacheMap.keySet()) {
            cacheMap.compute(key, (_, v) -> {
                if (v != null) {
                    var value = (Long) v;
                    jedisCluster.hincrBy(USAGE_DATA_KEY, key.toString(), value.intValue());
                    return 0L;
                } else {
                    return null;
                }
            });
        }
    }

    public void persistUsageStats() {
        var currentWindow = getCurrentUsageWindow();

        String cursor = ScanParams.SCAN_POINTER_START;
        ScanResult<Map.Entry<String, String>> results;

        do {
            results = jedisCluster.hscan(USAGE_DATA_KEY, cursor);

            for (var result : results.getResult()) {
                var key = result.getKey();
                var value = result.getValue();

                logger.debug("Usage stats: {} - {}", key, value);

                var component = key.split(":");
                var customerId = Long.parseLong(component[0]);
                var window = Long.parseLong(component[1]);

                if (window < currentWindow) {
                    try {
                        var customer = customerService.getCustomerById(customerId);
                        if (customer.isEmpty()) {
                            logger.warn("Failed to find customer with id {}", customerId);
                        } else {
                            UsageStats stats = new UsageStats();

                            stats.setCustomer(customer.get());
                            stats.setWindowStart(LocalDateTime.ofInstant(Instant.ofEpochSecond(window * 60), ZoneOffset.UTC));
                            stats.setCount(Long.parseLong(value));
                            stats.setDuration(Duration.ofMinutes(WINDOW_MINUTES));

                            try {
                                repositories.saveUsageStats(stats);
                            } catch (DataAccessException exc) {
                                logger.warn("failed to save usage stats for customer {}: {}", customer.get().getName(), exc.getMessage());
                            }
                        }
                    } finally {
                        jedisCluster.hdel(USAGE_DATA_KEY, key);
                    }
                }
            }

            cursor = results.getCursor();
        } while (!results.isCompleteIteration());
    }

    private long getCurrentUsageWindow() {
        var instant = Instant.now();
        var epochMinute = instant.getEpochSecond() / 60;
        return epochMinute / WINDOW_MINUTES * WINDOW_MINUTES;
    }

    public void calculateDailyUsageStats() {
        var today = TimeUtil.getCurrentUTC().truncatedTo(ChronoUnit.DAYS);

        for (var customer : repositories.findAllCustomers()) {
            List<LocalDateTime> unprocessedDays = repositories.findUnprocessedDaysForDailyUsage(customer);

            for (var day : unprocessedDays) {
                // skip processing the current day
                if (!day.isBefore(today)) {
                    continue;
                }

                logger.info("found unprocessed day for customer {}: {}", customer.getName(), day);

                var dailyStats = repositories.findDailyUsageStats(customer, day.toLocalDate());
                if (dailyStats != null) {
                    continue;
                }

                var usageStats = repositories.findUsageStatsByCustomerAndDate(customer, day);

                dailyStats = new DailyUsageStats();
                dailyStats.setCustomer(customer);
                dailyStats.setDate(day.toLocalDate());
                dailyStats.setTotalRequests(usageStats.stream().mapToLong(UsageStats::getCount).sum());
                dailyStats.setP95Requests(getDailyPercentile(usageStats, PERCENTILE));
                repositories.saveDailyUsageStats(dailyStats);
            }
        }
    }

    long getDailyPercentile(@Nonnull List<UsageStats> input, double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100 inclusive.");
        }

        long[] sortedCounts = new long[WINDOWS_PER_DAY];
        for (int i = 0; i < Math.min(input.size(), WINDOWS_PER_DAY); i++) {
            sortedCounts[i] = input.get(i).getCount();
        }

        Arrays.sort(sortedCounts);

        int rank = percentile == 0 ? 1 : (int) Math.ceil(percentile / 100.0 * sortedCounts.length);
        return sortedCounts[rank - 1];
    }
}
