/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPubSub;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages gitleaks secret detection rules: generation, scheduled refresh, and Redis sync.
 * 
 * This service combines three responsibilities:
 * 1. Generates rules from gitleaks.toml at startup (if auto-fetch enabled)
 * 2. Refreshes rules on a schedule (if scheduled-refresh enabled)
 * 3. Syncs rules across pods via Redis (if Redis enabled)
 * 
 * Only loaded when gitleaks.auto-fetch is enabled.
 */
@Service
@ConditionalOnProperty(name = "ovsx.scanning.secret-detection.gitleaks.auto-fetch", havingValue = "true")
public class GitleaksRulesService extends JedisPubSub {

    private static final Logger logger = LoggerFactory.getLogger(GitleaksRulesService.class);
    
    private static final String GITLEAKS_URL = 
        "https://raw.githubusercontent.com/gitleaks/gitleaks/master/config/gitleaks.toml";

    // Redis keys for sync
    private static final String RULES_KEY = "openvsx:gitleaks:rules";
    private static final String RULES_VERSION_KEY = "openvsx:gitleaks:rules:version";
    private static final String RULES_UPDATE_CHANNEL = "openvsx:gitleaks:rules:update";

    private final SecretDetectorConfig config;
    private final ObjectProvider<SecretDetectorFactory> detectorFactoryProvider;
    private final JedisCluster jedisCluster;

    // Path to generated rules file
    private String generatedRulesPath;
    
    // Redis subscriber state
    private volatile Thread subscriberThread;
    private volatile boolean running = true;

    public GitleaksRulesService(
            SecretDetectorConfig config,
            ObjectProvider<SecretDetectorFactory> detectorFactoryProvider,
            @Nullable JedisCluster jedisCluster
    ) {
        this.config = config;
        this.detectorFactoryProvider = detectorFactoryProvider;
        this.jedisCluster = jedisCluster;
        
        if (jedisCluster != null) {
            logger.debug("GitleaksRulesService initialized with Redis sync");
        } else {
            logger.debug("GitleaksRulesService initialized (local only, no Redis)");
        }
    }
    
    public String getGeneratedRulesPath() {
        return generatedRulesPath;
    }

    @PostConstruct
    public void initialize() {
        // Generate rules at startup
        generateRulesIfNeeded();
        
        // Start Redis subscriber if available
        if (jedisCluster != null) {
            startRedisSubscriber();
            loadRulesFromRedisIfNewer();
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (isSubscribed()) {
            unsubscribe();
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
    }

    /**
     * Generate rules at startup if configured.
     */
    private void generateRulesIfNeeded() {
        if (!config.isGitleaksAutoFetch()) {
            return;
        }

        try {
            File outputFile = resolveOutputFile();
            if (outputFile == null) {
                throw new IllegalStateException("Cannot resolve output file for generated rules");
            }
            
            this.generatedRulesPath = outputFile.getAbsolutePath();
            
            if (outputFile.exists() && !config.isGitleaksForceRefresh()) {
                logger.debug("Secret rules file already exists: {}", outputFile.getName());
                return;
            }

            generateRules(outputFile.toPath());
            
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IllegalStateException("Failed to generate rules: " + outputFile.getAbsolutePath());
            }
            
            logger.info("Generated secret detection rules: {} ({} bytes)", 
                outputFile.getName(), outputFile.length());
            
        } catch (Exception e) {
            logger.error("Failed to generate secret detection rules", e);
            throw new IllegalStateException(
                "Secret detection rule generation failed. Disable auto-generation or fix the issue.", e);
        }
    }

    /**
     * Refresh rules (called by scheduled job or manually).
     */
    public boolean refreshRules() {
        try {
            File outputFile = resolveOutputFile();
            if (outputFile == null) {
                logger.error("Cannot resolve output file for rules refresh");
                return false;
            }
            
            logger.debug("Refreshing gitleaks rules from remote source...");
            generateRules(outputFile.toPath());
            
            if (!outputFile.exists() || outputFile.length() == 0) {
                logger.error("Rules refresh failed: output file is missing or empty");
                return false;
            }
            
            this.generatedRulesPath = outputFile.getAbsolutePath();
            logger.debug("Refreshed gitleaks rules: {} ({} bytes)", outputFile.getName(), outputFile.length());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to refresh gitleaks rules", e);
            return false;
        }
    }

    /**
     * Scheduled refresh job - only runs if scheduled-refresh is enabled.
     */
    @Job(name = "Refresh gitleaks rules", retries = 0)
    @Recurring(
        id = "refresh-gitleaks-rules", 
        cron = "0 0 3 * * *", 
        zoneId = "UTC"
    )
    public void scheduledRefresh() {
        // Check if scheduled refresh is enabled at runtime
        if (!config.isGitleaksScheduledRefresh()) {
            return;
        }
        
        logger.debug("Starting scheduled gitleaks rules refresh");
        
        try {
            boolean rulesRefreshed = refreshRules();
            if (!rulesRefreshed) {
                logger.warn("Gitleaks rules refresh failed; scanner continues with existing rules");
                return;
            }

            // Sync to Redis and notify other pods
            if (jedisCluster != null) {
                String rulesContent = readRulesFile();
                if (rulesContent != null) {
                    boolean synced = storeAndPublishRules(rulesContent);
                    if (synced) {
                        logger.debug("Rules stored in Redis and other pods notified");
                    }
                }
            }
            
            // Reinitialize scanner with new rules
            reinitializeDetector();
            logger.debug("Scheduled gitleaks rules refresh completed");
            
        } catch (Exception e) {
            logger.error("Scheduled gitleaks rules refresh failed", e);
        }
    }

    private void startRedisSubscriber() {
        subscriberThread = new Thread(this::subscribeLoop, "GitleaksRulesSubscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void subscribeLoop() {
        AtomicInteger backoffMs = new AtomicInteger(1000);

        try (var executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("gitleaks-rules-reconnect"))) {
            while (running && !Thread.currentThread().isInterrupted()) {
                ScheduledFuture<?> resetTask = null;
                try {
                    resetTask = executor.schedule(() -> backoffMs.set(1000), 10, TimeUnit.SECONDS);
                    logger.debug("Subscribing to gitleaks rules update channel");
                    jedisCluster.subscribe(this, RULES_UPDATE_CHANNEL);
                } catch (Exception e) {
                    if (!running) break;
                    logger.warn("Gitleaks rules subscriber disconnected, reconnecting in {}s: {}",
                            backoffMs.get() / 1000, e.getMessage());
                    if (resetTask != null) resetTask.cancel(true);
                    try {
                        Thread.sleep(backoffMs.get());
                        backoffMs.set(Math.min(backoffMs.get() * 2, 30000));
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
            executor.shutdownNow();
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (RULES_UPDATE_CHANNEL.equals(channel)) {
            logger.debug("Received gitleaks rules update notification from another pod");
            loadRulesFromRedis();
        }
    }

    /**
     * Store rules in Redis and notify other pods.
     */
    public boolean storeAndPublishRules(String rulesContent) {
        if (jedisCluster == null) return false;
        
        try {
            String version = String.valueOf(System.currentTimeMillis());
            jedisCluster.set(RULES_KEY, rulesContent);
            jedisCluster.set(RULES_VERSION_KEY, version);
            jedisCluster.publish(RULES_UPDATE_CHANNEL, version);
            logger.debug("Stored gitleaks rules in Redis (version: {}, size: {} bytes)",
                version, rulesContent.length());
            return true;
        } catch (Exception e) {
            logger.error("Failed to store gitleaks rules in Redis", e);
            return false;
        }
    }

    private void loadRulesFromRedisIfNewer() {
        if (jedisCluster == null) return;
        
        try {
            String redisVersion = jedisCluster.get(RULES_VERSION_KEY);
            if (redisVersion == null) {
                logger.debug("No gitleaks rules in Redis yet");
                return;
            }

            String outputPath = config.getGitleaksOutputPath();
            if (outputPath != null && !outputPath.isEmpty()) {
                Path localPath = Path.of(outputPath);
                if (Files.exists(localPath)) {
                    long localModified = Files.getLastModifiedTime(localPath).toMillis();
                    long redisTs = Long.parseLong(redisVersion);
                    if (localModified >= redisTs) {
                        logger.debug("Local rules are up to date");
                        return;
                    }
                }
            }
            loadRulesFromRedis();
        } catch (Exception e) {
            logger.warn("Failed to check Redis for rules on startup: {}", e.getMessage());
        }
    }

    private void loadRulesFromRedis() {
        if (jedisCluster == null) return;
        
        try {
            String rulesContent = jedisCluster.get(RULES_KEY);
            if (rulesContent == null || rulesContent.isEmpty()) {
                logger.warn("No rules content found in Redis");
                return;
            }

            String outputPath = config.getGitleaksOutputPath();
            if (outputPath == null || outputPath.isEmpty()) {
                logger.warn("No output path configured, cannot sync from Redis");
                return;
            }

            Path localPath = Path.of(outputPath);
            Files.createDirectories(localPath.getParent());
            Files.writeString(localPath, rulesContent);
            logger.debug("Loaded gitleaks rules from Redis ({} bytes)", rulesContent.length());
            reinitializeDetector();
        } catch (Exception e) {
            logger.error("Failed to load rules from Redis", e);
        }
    }

    /**
     * Reinitialize the detector factory if available.
     * Uses ObjectProvider for lazy lookup to avoid circular dependency.
     */
    private void reinitializeDetector() {
        SecretDetectorFactory factory = detectorFactoryProvider.getIfAvailable();
        if (factory != null) {
            factory.reinitialize();
        } else {
            logger.warn("SecretDetectorFactory not available; skipping reinitialize");
        }
    }

    private void generateRules(Path outputPath) throws IOException, InterruptedException {
        logger.debug("Downloading gitleaks.toml from: {}", GITLEAKS_URL);
        String tomlContent = downloadGitleaksToml();
        GitleaksToml parsed = parseToml(tomlContent);
        List<Rule> rules = buildRules(parsed.rules);
        GlobalAllowlist allowlist = extractGlobalAllowlist(parsed.allowlist);
        writeYaml(rules, allowlist, outputPath);
    }

    private String downloadGitleaksToml() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITLEAKS_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to download gitleaks.toml: HTTP " + response.statusCode());
            }
            String body = response.body();
            if (body == null || body.isEmpty()) {
                throw new IOException("Downloaded gitleaks.toml is empty");
            }
            return body;
        }
    }

    private GitleaksToml parseToml(String tomlContent) throws IOException {
        return new TomlMapper().readValue(tomlContent, GitleaksToml.class);
    }

    private List<Rule> buildRules(List<RawRule> rawRules) {
        if (rawRules == null) return new ArrayList<>();
        
        Set<String> skipRuleIds = config.getGitleaksSkipRuleIds();
        List<Rule> result = new ArrayList<>();
        int skipped = 0;
        
        for (RawRule raw : rawRules) {
            if (raw.id != null && skipRuleIds.contains(raw.id)) {
                skipped++;
                continue;
            }
            Rule normalized = normalizeRule(raw);
            if (normalized != null) result.add(normalized);
        }
        
        logger.info("Parsed {} rules (skipped {} via skip-rule-ids config)", result.size(), skipped);
        return result;
    }

    private GlobalAllowlist extractGlobalAllowlist(RawAllowlist raw) {
        GlobalAllowlist allowlist = new GlobalAllowlist();
        if (raw != null) {
            allowlist.paths = raw.paths != null ? raw.paths : new ArrayList<>();
            allowlist.regexes = raw.regexes != null ? raw.regexes : new ArrayList<>();
            allowlist.stopwords = raw.stopwords != null ? raw.stopwords : new ArrayList<>();
            allowlist.fileExtensions = raw.file_extensions != null ? raw.file_extensions : new ArrayList<>();
        }
        return allowlist;
    }

    private Rule normalizeRule(RawRule raw) {
        if (raw == null || raw.id == null || raw.regex == null) return null;
        
        Rule rule = new Rule();
        rule.id = raw.id;
        rule.description = raw.description != null ? raw.description : "";
        rule.regex = raw.regex;
        rule.entropy = raw.entropy;
        rule.secretGroup = raw.secretGroup;
        rule.keywords = raw.keywords != null 
            ? raw.keywords.stream().map(String::toLowerCase).collect(Collectors.toList())
            : new ArrayList<>();
        
        List<RawAllowlist> rawAllowlists = raw.getAllowlists();
        if (!rawAllowlists.isEmpty()) {
            rule.allowlists = new ArrayList<>();
            for (RawAllowlist al : rawAllowlists) {
                if (al.regexes != null && !al.regexes.isEmpty()) {
                    RuleAllowlist allowlist = new RuleAllowlist();
                    allowlist.regexes = al.regexes;
                    rule.allowlists.add(allowlist);
                }
            }
        }
        return rule;
    }

    private void writeYaml(List<Rule> rules, GlobalAllowlist allowlist, Path outputPath) throws IOException {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Auto-generated by GitleaksRulesService - do not edit manually\n\n");
        yaml.append("allowlist:\n");
        writeYamlList(yaml, "  paths", allowlist.paths);
        writeYamlList(yaml, "  regexes", allowlist.regexes);
        writeYamlList(yaml, "  stopwords", allowlist.stopwords);
        writeYamlList(yaml, "  file-extensions", allowlist.fileExtensions);
        
        yaml.append("\nrules:\n");
        for (Rule rule : rules) {
            yaml.append("  - id: ").append(rule.id).append("\n");
            yaml.append("    description: ").append(quote(rule.description)).append("\n");
            yaml.append("    regex: ").append(quote(rule.regex)).append("\n");
            if (rule.entropy != null) yaml.append("    entropy: ").append(rule.entropy).append("\n");
            if (rule.secretGroup != null) yaml.append("    secretGroup: ").append(rule.secretGroup).append("\n");
            yaml.append("    keywords: ");
            if (rule.keywords.isEmpty()) {
                yaml.append("[]\n");
            } else {
                yaml.append("\n");
                for (String kw : rule.keywords) yaml.append("      - ").append(quote(kw)).append("\n");
            }
            if (rule.allowlists != null && !rule.allowlists.isEmpty()) {
                yaml.append("    allowlists:\n");
                for (RuleAllowlist al : rule.allowlists) {
                    if (al.regexes != null && !al.regexes.isEmpty()) {
                        yaml.append("      - regexes:\n");
                        for (String r : al.regexes) yaml.append("          - ").append(quote(r)).append("\n");
                    }
                }
            }
        }
        Files.writeString(outputPath, yaml.toString());
    }

    private void writeYamlList(StringBuilder yaml, String prefix, List<String> items) {
        if (items != null && !items.isEmpty()) {
            yaml.append(prefix).append(":\n");
            // List items need to be indented relative to the parent key
            // prefix is like "  paths", so list items should use 4 spaces
            for (String item : items) {
                yaml.append("    - ").append(quote(item)).append("\n");
            }
        }
    }

    private String quote(String value) {
        if (value == null) return "\"\"";
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    private File resolveOutputFile() {
        String path = config.getGitleaksOutputPath();
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalStateException(
                "gitleaks.auto-fetch enabled but 'gitleaks.output-path' not configured");
        }
        
        File outputFile = new File(path);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            try {
                Files.createDirectories(parentDir.toPath());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create directory: " + parentDir, e);
            }
        }
        return outputFile;
    }

    @Nullable
    private String readRulesFile() {
        String outputPath = config.getGitleaksOutputPath();
        if (outputPath == null) return null;
        try {
            return Files.readString(Path.of(outputPath));
        } catch (IOException e) {
            logger.error("Failed to read rules file: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GitleaksToml {
        public List<RawRule> rules;
        public RawAllowlist allowlist;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RawRule {
        public String id;
        public String description;
        public String regex;
        public Double entropy;
        public Integer secretGroup;
        public List<String> keywords;
        public List<RawAllowlist> allowlist;
        public List<RawAllowlist> allowlists;
        
        List<RawAllowlist> getAllowlists() {
            if (allowlists != null && !allowlists.isEmpty()) return allowlists;
            if (allowlist != null && !allowlist.isEmpty()) return allowlist;
            return new ArrayList<>();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RawAllowlist {
        public List<String> paths;
        public List<String> regexes;
        public List<String> stopwords;
        public List<String> file_extensions;
    }

    static class Rule {
        String id;
        String description;
        String regex;
        Double entropy;
        Integer secretGroup;
        List<String> keywords;
        List<RuleAllowlist> allowlists;
    }

    static class RuleAllowlist {
        List<String> regexes;
    }

    static class GlobalAllowlist {
        List<String> paths = new ArrayList<>();
        List<String> regexes = new ArrayList<>();
        List<String> stopwords = new ArrayList<>();
        List<String> fileExtensions = new ArrayList<>();
    }
}
