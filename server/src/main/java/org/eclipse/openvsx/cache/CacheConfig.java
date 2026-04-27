/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.jcache.CacheManagerImpl;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import io.micrometer.common.util.StringUtils;
import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.cache.CacheService.*;

@Configuration
@EnableCaching(proxyTargetClass = true)
public class CacheConfig {

    protected final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public Cache<Object, Object> extensionCache(
            @Value("${ovsx.caching.files-extension.tti:PT1H}") Duration timeToIdle,
            @Value("${ovsx.caching.files-extension.max-size:20}") long maxSize
    ) {
        return Caffeine.newBuilder()
                .removalListener(new ExpiredFileListener())
                .expireAfterAccess(timeToIdle)
                .maximumSize(maxSize)
                .scheduler(Scheduler.systemScheduler())
                .recordStats()
                .build();
    }

    @Bean
    public Cache<Object, Object> webResourceCache(
            @Value("${ovsx.caching.files-webresource.tti:PT1H}") Duration timeToIdle,
            @Value("${ovsx.caching.files-webresource.max-size:150}") long maxSize
    ) {
        return Caffeine.newBuilder()
                .removalListener(new ExpiredFileListener())
                .expireAfterAccess(timeToIdle)
                .maximumSize(maxSize)
                .scheduler(Scheduler.systemScheduler())
                .recordStats()
                .build();
    }

    @Bean
    public Cache<Object, Object> browseCache(
            @Value("${ovsx.caching.files-browse.tti:PT1H}") Duration timeToIdle,
            @Value("${ovsx.caching.files-browse.max-size:50}") long maxSize
    ) {
        return Caffeine.newBuilder()
                .expireAfterAccess(timeToIdle)
                .maximumSize(maxSize)
                .scheduler(Scheduler.systemScheduler())
                .recordStats()
                .build();
    }

    @Bean
    public @Qualifier("fileCacheManager") CacheManager fileCacheManager(
            Cache<Object, Object> extensionCache,
            Cache<Object, Object> webResourceCache,
            Cache<Object, Object> browseCache
    ) {
        logger.info("Configure file cache manager");
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.registerCustomCache(CACHE_EXTENSION_FILES, extensionCache);
        caffeineCacheManager.registerCustomCache(CACHE_WEB_RESOURCE_FILES, webResourceCache);
        caffeineCacheManager.registerCustomCache(CACHE_BROWSE_EXTENSION_FILES, browseCache);

        return caffeineCacheManager;
    }

    @Bean
    @ConditionalOnExpression("${bucket4j.enabled:false} && '${bucket4j.cache-to-use:}' == 'redis-jedis'")
    public JedisPool jedisPool(RedisProperties properties) {
        logger.info("Configure 'redis-jedis' bucket4j rate-limiting cache");
        return new JedisPool(properties.getHost(), properties.getPort(), properties.getUsername(), properties.getPassword());
    }

    @Bean
    @ConditionalOnExpression("${bucket4j.enabled:false} && '${bucket4j.cache-to-use:}' == 'redis-cluster-jedis'")
    public JedisCluster jedisCluster(RedisProperties properties) {
        logger.info("Configure 'redis-cluster-jedis' bucket4j rate-limiting cache");
        var configBuilder = DefaultJedisClientConfig.builder();
        var username = properties.getUsername();
        if(StringUtils.isNotEmpty(username)) {
            configBuilder.user(username);
        }
        var password = properties.getPassword();
        if(StringUtils.isNotEmpty(password)) {
            configBuilder.password(password);
        }

        var nodes = properties.getCluster().getNodes().stream()
                .map(HostAndPort::from)
                .collect(Collectors.toSet());

        return new JedisCluster(nodes, configBuilder.build());
    }

    @Bean
    @Primary
    @ConditionalOnProperty(value = "ovsx.redis.enabled", havingValue = "false", matchIfMissing = true)
    public JCacheCacheManager caffeineCacheManager(
            @Value("${ovsx.caching.average-review-rating.ttl:P3D}") Duration averageReviewRatingTtl,
            @Value("${ovsx.caching.average-review-rating.max-size:1}") long averageReviewRatingMaxSize,
            @Value("${ovsx.caching.namespace-details-json.ttl:PT1H}") Duration namespaceDetailsJsonTtl,
            @Value("${ovsx.caching.namespace-details-json.max-size:1024}") long namespaceDetailsJsonMaxSize,
            @Value("${ovsx.caching.database-search.ttl:PT1H}") Duration databaseSearchTtl,
            @Value("${ovsx.caching.database-search.max-size:1024}") long databaseSearchMaxSize,
            @Value("${ovsx.caching.extension-json.ttl:PT1H}") Duration extensionJsonTtl,
            @Value("${ovsx.caching.extension-json.max-size:1024}") long extensionJsonMaxSize,
            @Value("${ovsx.caching.latest-extension-version.ttl:PT1H}") Duration latestExtensionVersionTtl,
            @Value("${ovsx.caching.latest-extension-version.max-size:1024}") long latestExtensionVersionMaxSize,
            @Value("${ovsx.caching.latest-extension-version-vscode.ttl:PT1H}") Duration latestExtensionVersionVscodeTtl,
            @Value("${ovsx.caching.latest-extension-version-vscode.max-size:1024}") long latestExtensionVersionVscodeMaxSize,
            @Value("${ovsx.caching.latest-extension-versions-by-platform.ttl:PT1H}") Duration latestExtensionVersionsByPlatformTtl,
            @Value("${ovsx.caching.latest-extension-versions-by-platform.max-size:1024}") long latestExtensionVersionsByPlatformMaxSize,
            @Value("${ovsx.caching.sitemap.ttl:PT1H}") Duration sitemapTtl,
            @Value("${ovsx.caching.sitemap.max-size:1}") long sitemapMaxSize,
            @Value("${ovsx.caching.malicious-extensions.ttl:P3D}") Duration maliciousExtensionsTtl,
            @Value("${ovsx.caching.malicious-extensions.max-size:1}") long maliciousExtensionsMaxSize,
            @Value("${ovsx.caching.rate-limiting.name:buckets}") String rateLimitingCacheName,
            @Value("${ovsx.caching.rate-limiting.tti:PT1H}") Duration rateLimitingTti,
            @Value("${ovsx.caching.rate-limiting.max-size:1024}") long rateLimitingMaxSize
    ) {
        logger.info("Configure Caffeine cache manager");
        var averageReviewRatingCache = createCaffeineConfiguration(averageReviewRatingTtl, averageReviewRatingMaxSize, false);
        var namespaceDetailsJsonCache = createCaffeineConfiguration(namespaceDetailsJsonTtl, namespaceDetailsJsonMaxSize, false);
        var databaseSearchCache = createCaffeineConfiguration(databaseSearchTtl, databaseSearchMaxSize, false);
        var extensionJsonCache = createCaffeineConfiguration(extensionJsonTtl, extensionJsonMaxSize, false);
        var latestExtensionVersionCache = createCaffeineConfiguration(latestExtensionVersionTtl, latestExtensionVersionMaxSize, false);
        var latestExtensionVersionVscodeCache = createCaffeineConfiguration(latestExtensionVersionVscodeTtl, latestExtensionVersionVscodeMaxSize, false);
        var latestExtensionVersionsByPlatformCache = createCaffeineConfiguration(latestExtensionVersionsByPlatformTtl, latestExtensionVersionsByPlatformMaxSize, false);
        var sitemapCache = createCaffeineConfiguration(sitemapTtl, sitemapMaxSize, false);
        var maliciousExtensionsCache = createCaffeineConfiguration(maliciousExtensionsTtl, maliciousExtensionsMaxSize, false);
        var rateLimitingCache = createCaffeineConfiguration(rateLimitingTti, rateLimitingMaxSize, true);

        var cacheManager = new CacheManagerImpl(
                new CaffeineCachingProvider(),
                false,
                URI.create("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider"),
                Thread.currentThread().getContextClassLoader(),
                new Properties()
        );

        cacheManager.createCache(CACHE_AVERAGE_REVIEW_RATING, averageReviewRatingCache);
        cacheManager.createCache(CACHE_NAMESPACE_DETAILS_JSON, namespaceDetailsJsonCache);
        cacheManager.createCache(CACHE_DATABASE_SEARCH, databaseSearchCache);
        cacheManager.createCache(CACHE_EXTENSION_JSON, extensionJsonCache);
        cacheManager.createCache(CACHE_LATEST_EXTENSION_VERSION, latestExtensionVersionCache);
        cacheManager.createCache(CACHE_LATEST_EXTENSION_VERSION_VSCODE, latestExtensionVersionVscodeCache);
        cacheManager.createCache(CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM, latestExtensionVersionsByPlatformCache);
        cacheManager.createCache(CACHE_SITEMAP, sitemapCache);
        cacheManager.createCache(CACHE_MALICIOUS_EXTENSIONS, maliciousExtensionsCache);
        cacheManager.createCache(rateLimitingCacheName, rateLimitingCache);
        return new JCacheCacheManager(cacheManager);
    }

    private CaffeineConfiguration<Object, Object> createCaffeineConfiguration(Duration duration, long maxSize, boolean tti) {
        var configuration = new CaffeineConfiguration<>();
        configuration.setMaximumSize(OptionalLong.of(maxSize));
        if(tti) {
            configuration.setExpireAfterAccess(OptionalLong.of(duration.toNanos()));
        } else {
            configuration.setExpireAfterWrite(OptionalLong.of(duration.toNanos()));
        }

        return configuration;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(value = "ovsx.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${ovsx.caching.average-review-rating.ttl:P3D}") Duration averageReviewRatingTtl,
            @Value("${ovsx.caching.namespace-details-json.ttl:PT1H}") Duration namespaceDetailsJsonTtl,
            @Value("${ovsx.caching.database-search.ttl:PT1H}") Duration databaseSearchTtl,
            @Value("${ovsx.caching.extension-json.ttl:PT1H}") Duration extensionJsonTtl,
            @Value("${ovsx.caching.latest-extension-version.ttl:PT1H}") Duration latestExtensionVersionTtl,
            @Value("${ovsx.caching.latest-extension-version-vscode.ttl:PT1H}") Duration latestExtensionVersionVscodeTtl,
            @Value("${ovsx.caching.latest-extension-versions-by-platform.ttl:PT1H}") Duration latestExtensionVersionsByPlatformTtl,
            @Value("${ovsx.caching.sitemap.ttl:PT1H}") Duration sitemapTtl,
            @Value("${ovsx.caching.malicious-extensions.ttl:P3D}") Duration maliciousExtensionsTtl
    ) {
        logger.info("Configure Redis cache manager");
        var extensionVersionMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
        return RedisCacheManager.builder(redisConnectionFactory)
                .withCacheConfiguration(
                        CACHE_AVERAGE_REVIEW_RATING,
                        redisCacheConfig(new GenericJackson2JsonRedisSerializer(), averageReviewRatingTtl)
                )
                .withCacheConfiguration(
                        CACHE_NAMESPACE_DETAILS_JSON,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(NamespaceDetailsJson.class), namespaceDetailsJsonTtl)
                )
                .withCacheConfiguration(
                        CACHE_DATABASE_SEARCH,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(SearchResult.class), databaseSearchTtl)
                )
                .withCacheConfiguration(
                        CACHE_EXTENSION_JSON,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(ExtensionJson.class), extensionJsonTtl)
                )
                .withCacheConfiguration(
                        CACHE_LATEST_EXTENSION_VERSION_VSCODE,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(ExtensionQueryResult.Extension.class), latestExtensionVersionVscodeTtl)
                )
                .withCacheConfiguration(
                        CACHE_LATEST_EXTENSION_VERSION,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(extensionVersionMapper, ExtensionVersion.class), latestExtensionVersionTtl)
                )
                .withCacheConfiguration(
                        CACHE_LATEST_EXTENSION_VERSIONS_BY_PLATFORM,
                        redisCacheConfig(new Jackson2JsonRedisSerializer<>(extensionVersionMapper, List.class), latestExtensionVersionsByPlatformTtl)
                )
                .withCacheConfiguration(
                        CACHE_SITEMAP,
                        redisCacheConfig(new StringRedisSerializer(), sitemapTtl)
                )
                .withCacheConfiguration(
                        CACHE_MALICIOUS_EXTENSIONS,
                        redisCacheConfig(new GenericJackson2JsonRedisSerializer(), maliciousExtensionsTtl)
                )
                .build();
    }

    private <T> RedisCacheConfiguration redisCacheConfig(RedisSerializer<T> serializer, Duration ttl) {
        var serializationPair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(serializationPair)
                .entryTtl(ttl);
    }
}
