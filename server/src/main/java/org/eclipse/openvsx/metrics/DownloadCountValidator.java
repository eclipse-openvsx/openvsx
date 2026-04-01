/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation 
 *
 * See the NOTICE file(s) distributed with this work for additional 
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 
 ********************************************************************************/
package org.eclipse.openvsx.metrics;

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.metrics.config.DownloadCountValidationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Validates whether a download should increment the download count.
 * Prevents malicious actors from inflating download counts through:
 * - Per-IP per-extension hourly rate limiting (hourly-limit-per-ip)
 * - Filtering out automated/bot downloads
 *
 * Only active when Redis is enabled (ovsx.redis.enabled=true).
 * When Redis is unavailable, all downloads are counted (no filtering).
 */
@Service
@ConditionalOnProperty(value = "ovsx.redis.enabled", havingValue = "true")
public class DownloadCountValidator {

    private static final Logger logger = LoggerFactory.getLogger(DownloadCountValidator.class);

    private final StringRedisTemplate redisTemplate;
    private final DownloadCountValidationProperties properties;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * SpEL expression evaluated against the HttpServletRequest to extract the client IP.
     * Shares the same property as the rate limiter (ovsx.rate-limit.ip-address-function)
     * so both systems resolve IPs consistently.
     *
     * Default is getRemoteAddr() (TCP source IP — safe, but returns the proxy IP
     * when behind a reverse proxy). Override in application.yml for proxied deployments.
     */
    private final String ipAddressFunction;

    public DownloadCountValidator(
            StringRedisTemplate redisTemplate,
            @Value("${ovsx.rate-limit.ip-address-function:getRemoteAddr()}") String ipAddressFunction,
            DownloadCountValidationProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.ipAddressFunction = ipAddressFunction;
        this.properties = properties;
    }

    /**
     * Determines if this download should increment the extension's download count.
     */
    public boolean shouldCountDownload(Extension extension, HttpServletRequest request) {
        if (!isValidationEnabled()) {
            return true;
        }

        if (request == null) {
            // Fail closed when validation is enabled but request context is missing.
            return false;
        }

        String userAgent = extractUserAgent(request);
        String ipAddress = extractClientIp(request);
        // API download flow does not carry a log event timestamp, so use request time
        // as event-time for dedup bucketing.
        return shouldCountDownload(extension.getId(), ipAddress, userAgent, Instant.now());
    }

    /**
     * Determines if a download should be counted for non-request contexts
     * using event-time bucketing.
     * <p>
     * The Redis key includes a time bucket computed from the event timestamp.
     * This makes dedup independent of when the handler runs.
     */
    public boolean shouldCountDownload(Long extensionId, String clientIp, String userAgent, Instant eventTime) {
        if (!isValidationEnabled()) {
            return true;
        }

        if (isAutomatedClient(userAgent)) {
            return false;
        }

        if (clientIp == null || clientIp.isBlank()) {
            // Fail closed when validation is enabled but client IP cannot be resolved.
            return false;
        }

        if (eventTime == null) {
            // Event-time bucketing is required; without an event timestamp the event
            // cannot be placed in a deterministic dedup bucket.
            return false;
        }

        return isUnderHourlyLimit(clientIp, extensionId, eventTime);
    }

    public boolean isValidationEnabled() {
        return properties.getEnabled();
    }

    /**
     * Checks whether this download is within the per-IP per-extension hourly limit.
     * <p>
     * Redis key: {@code {prefix}:{hashedIp}:{extensionId}:{hourBucket}}
     * TTL: 1 hour + {@code lateArrivalHours} so late CDN log entries can still
     * deduplicate against the correct bucket after the hour rolls over.
     * <p>
     * The key is created with the TTL atomically via SET NX before incrementing,
     * so the TTL is always set at creation with no race window.
     */
    private boolean isUnderHourlyLimit(String ipAddress, Long extensionId, Instant eventTime) {
        String key = buildRateLimitKey(ipAddress, extensionId, eventTime);
        Duration ttl = Duration.ofHours(1).plusHours(properties.getLateArrivalHours());

        // Create the key with TTL atomically if it doesn't exist yet.
        // This guarantees the TTL is always set before any increment happens.
        redisTemplate.opsForValue().setIfAbsent(key, "0", ttl);

        Long count = redisTemplate.opsForValue().increment(key);
        return count != null && count <= properties.getHourlyLimitPerIp();
    }

    /**
     * Builds the Redis key for per-IP per-extension hourly rate limiting.
     * <p>
     * Format: {@code {prefix}:{hashedIp}:{extensionId}:{hourBucket}}
     * where {@code hourBucket} is the event timestamp divided by 3600 (epoch seconds),
     * so all events from the same IP + extension within the same clock-hour share one counter.
     */
    private String buildRateLimitKey(String ipAddress, Long extensionId, Instant eventTime) {
        // Truncate to the hour so all events in the same hour share one key.
        long hourBucket = eventTime.getEpochSecond() / 3600;
        return String.format("%s:%s:%d:%d",
                properties.getKeyPrefix(),
                hashIp(ipAddress),
                extensionId,
                hourBucket
        );
    }

    private String hashIp(String ipAddress) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ipAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts client IP by evaluating the same SpEL expression used by the rate limiter.
     * The expression runs against the HttpServletRequest as root object, so methods
     * like getHeader(), getRemoteAddr(), getParameter() are all available.
     */
    private String extractClientIp(HttpServletRequest request) {
        try {
            var expr = expressionParser.parseExpression(ipAddressFunction);
            var result = expr.getValue(request, String.class);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate ip-address-function '{}': {}", ipAddressFunction, e.getMessage());
        }

        // Fallback to TCP source IP if the expression fails or returns empty
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isEmpty()) ? remoteAddr : null;
    }

    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return (userAgent != null && !userAgent.isEmpty()) ? userAgent : null;
    }

    /**
     * Heuristic check for automated HTTP clients.
     * These downloads are served normally but don't inflate the count.
     */
    private boolean isAutomatedClient(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        return properties.getAutomatedClientKeywords().stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(ua::contains);
    }

}
