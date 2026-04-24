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
package org.eclipse.openvsx.scanning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP-based scanner driven entirely by YAML configuration.
 * <p>
 * Reads HTTP operation configs (URLs, headers, body templates), executes
 * requests via HttpClientExecutor, and parses responses via ResponseExtractor.
 * <p>
 * Adding new scanners requires only YAML configuration - no Java code needed.
 */
public class RemoteScanner implements Scanner {
    
    private static final Logger logger = LoggerFactory.getLogger(RemoteScanner.class);
    
    private final String scannerName;
    private final RemoteScannerProperties.ScannerConfig config;
    private final HttpTemplateEngine templateEngine;
    private final HttpClientExecutor httpExecutor;
    private final HttpResponseExtractor responseExtractor;
    private final ScannerFileProvider scanFileService;
    
    public RemoteScanner(
        @Nonnull String scannerName,
        @Nonnull RemoteScannerProperties.ScannerConfig config,
        @Nonnull HttpTemplateEngine templateEngine,
        @Nonnull HttpClientExecutor httpExecutor,
        @Nonnull HttpResponseExtractor responseExtractor,
        @Nonnull ScannerFileProvider scanFileService
    ) {
        this.scannerName = scannerName;
        this.config = config;
        this.templateEngine = templateEngine;
        this.httpExecutor = httpExecutor;
        this.responseExtractor = responseExtractor;
        this.scanFileService = scanFileService;
    }
    
    @Override
    @Nonnull
    public String getScannerType() {
        return config.getType();
    }
    
    @Override
    public boolean isRequired() {
        return config.isRequired();
    }
    
    @Override
    public int getTimeoutMinutes() {
        return config.getTimeoutMinutes();
    }
    
    @Override
    public boolean isAsync() {
        return config.isAsync();
    }
    
    @Override
    public int getMaxConcurrency() { return config.getMaxConcurrency(); }
    
    @Override
    public int getMaxQueueWaitMinutes() { return config.getMaxQueueWaitMinutes(); }
    
    @Override
    public boolean enforcesThreats() {
        return config.isEnforced();
    }
    
    @Override
    public RemoteScannerProperties.PollConfig getPollConfig() {
        return config.getPolling();
    }

    /**
     * Build a dashboard URL for a scanner job by substituting {jobId} in the
     * configured external-url-template. Returns null if no template is
     * configured or externalJobId is null.
     */
    @Override
    @Nullable
    public String buildExternalUrl(@Nullable String externalJobId) {
        String template = config.getExternalUrlTemplate();
        if (template == null || externalJobId == null) {
            return null;
        }
        return template.replace("{jobId}", externalJobId);
    }
    
    /**
     * Start a scan by sending the entire .vsix file to the scanner.
     * <p>
     * For sync scanners: Parses result immediately from start response
     * For async scanners: Extracts job ID from start response
     */
    @Override
    @Nonnull
    public Scanner.Invocation startScan(@Nonnull Command command) throws ScannerException {
        logger.debug("Starting {} scan for extension version {}", 
            scannerName, command.extensionVersionId());
        
        RemoteScannerProperties.HttpOperation configOp = config.getStart();
        if (configOp == null) {
            throw new ScannerException("No start operation configured for scanner: " + scannerName);
        }
        
        try (var extensionFile = scanFileService.getExtensionFile(command.extensionVersionId())) {
            File file = extensionFile.getPath().toFile();
            String fileName = extensionFile.getResource() != null ? extensionFile.getResource().getName() : file.getName();
            
            // Copy operation to avoid mutating shared config (thread safety)
            RemoteScannerProperties.HttpOperation startOp = configOp.copy();
            
            // Process URL and headers with placeholders
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("fileName", fileName);
            
            processOperation(startOp, placeholders);
            
            // Execute HTTP request
            String response = httpExecutor.execute(startOp, extensionFile);
            
            logger.debug("Start operation response: {}", response);
            
            return parseStartResponse(response, startOp);
        } catch (java.io.IOException | ScannerException e) {
            throw new ScannerException("Failed to start scan: " + e.getMessage(), e);
        }
    }

    /**
     * Parse the start operation response and return appropriate ScanInvocation.
     */
    private Scanner.Invocation parseStartResponse(
            String response,
            RemoteScannerProperties.HttpOperation startOp
    ) throws ScannerException {
        // Parse response based on scanner type
        if (config.isAsync()) {
            // Extract job ID for async scanner
            String jobId = extractJobId(response, startOp);
            logger.debug("Scan submitted with job ID: {}", jobId);
            return new Scanner.Invocation.Submitted(new Submission(jobId));
        } else {
            // Parse result immediately for sync scanner
            Scanner.Result result = parseResult(response, startOp);
            return new Scanner.Invocation.Completed(result);
        }
    }
    
    /**
     * Poll status of an async scan.
     * 
     * Executes the configured poll operation and maps the response
     * to PollStatus.
     */
    @Override
    @Nonnull
    public PollStatus pollStatus(@Nonnull Submission submission) throws ScannerException {
        if (!config.isAsync()) {
            throw new UnsupportedOperationException("Scanner is not async: " + scannerName);
        }
        
        RemoteScannerProperties.HttpOperation configOp = config.getPoll();
        if (configOp == null) {
            throw new ScannerException("No poll operation configured for scanner: " + scannerName);
        }
        
        try {
            RemoteScannerProperties.HttpOperation pollOp = configOp.copy();
            
            // Process URL and headers with job ID
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("jobId", submission.externalJobId());
            
            processOperation(pollOp, placeholders);
            
            // Execute HTTP request
            String response = httpExecutor.execute(pollOp, null);
            
            logger.debug("Poll operation response: {}", response);
            
            // Extract and map status
            String status = extractStatus(response, pollOp);
            PollStatus mappedStatus = mapStatus(status, pollOp);
            
            logger.debug("Job {} status: {} -> {}", 
                submission.externalJobId(), status, mappedStatus);
            
            return mappedStatus;
            
        } catch (Exception e) {
            throw new ScannerException("Failed to poll scan status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Retrieve results from a completed async scan.
     * <p>
     * Executes the configured result operation and parses threats.
     */
    @Override
    @Nonnull
    public Scanner.Result fetchResults(@Nonnull Submission submission) throws ScannerException {
        if (!config.isAsync()) {
            throw new UnsupportedOperationException("Scanner is not async: " + scannerName);
        }
        
        RemoteScannerProperties.HttpOperation configOp = config.getResult();
        if (configOp == null) {
            throw new ScannerException("No result operation configured for scanner: " + scannerName);
        }
        
        try {
            // Copy operation to avoid mutating shared config (thread safety)
            RemoteScannerProperties.HttpOperation resultOp = configOp.copy();
            
            // Process URL and headers with job ID
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("jobId", submission.externalJobId());
            
            processOperation(resultOp, placeholders);
            
            // Execute HTTP request
            String response = httpExecutor.execute(resultOp, null);
            
            logger.debug("Result operation response: {}", response);
            
            // Parse result
            Scanner.Result result = parseResult(response, resultOp);
            
            logger.debug("Scan {} completed: {}", 
                submission.externalJobId(),
                result.isClean() ? "clean" : result.getThreats().size() + " threats found");
            
            return result;
            
        } catch (Exception e) {
            throw new ScannerException("Failed to retrieve scan results: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process an operation by substituting placeholders in URL and headers.
     */
    private void processOperation(
        RemoteScannerProperties.HttpOperation operation,
        Map<String, String> placeholders
    ) {
        // Process URL
        String processedUrl = templateEngine.process(operation.getUrl(), placeholders);
        operation.setUrl(processedUrl);
        
        // Process headers
        Map<String, String> processedHeaders = templateEngine.processMap(
            operation.getHeaders(),
            placeholders
        );
        operation.setHeaders(processedHeaders);
        
        // Process query params
        Map<String, String> processedParams = templateEngine.processMap(
            operation.getQueryParams(),
            placeholders
        );
        operation.setQueryParams(processedParams);
    }
    
    /**
     * Extract job ID from start operation response.
     */
    private String extractJobId(
        String response,
        RemoteScannerProperties.HttpOperation operation
    ) throws ScannerException {
        RemoteScannerProperties.ResponseConfig responseConfig = operation.getResponse();
        if (responseConfig == null || responseConfig.getJobIdPath() == null) {
            throw new ScannerException("No job ID path configured");
        }
        
        String jobId = responseExtractor.extractString(
            response,
            responseConfig.getFormat(),
            responseConfig.getJobIdPath()
        );
        
        if (jobId == null) {
            throw new ScannerException("Failed to extract job ID from response");
        }
        
        return jobId;
    }
    
    /**
     * Extract status from poll operation response.
     */
    private String extractStatus(
        String response,
        RemoteScannerProperties.HttpOperation operation
    ) throws ScannerException {
        RemoteScannerProperties.ResponseConfig responseConfig = operation.getResponse();
        if (responseConfig == null || responseConfig.getStatusPath() == null) {
            throw new ScannerException("No status path configured");
        }
        
        String status = responseExtractor.extractString(
            response,
            responseConfig.getFormat(),
            responseConfig.getStatusPath()
        );
        
        if (status == null) {
            throw new ScannerException("Failed to extract status from response");
        }
        
        return status;
    }
    
    /**
     * Map scanner-specific status to PollStatus.
     */
    private PollStatus mapStatus(
        String status,
        RemoteScannerProperties.HttpOperation operation
    ) throws ScannerException {
        RemoteScannerProperties.ResponseConfig responseConfig = operation.getResponse();
        Map<String, String> statusMapping = responseConfig.getStatusMapping();
        
        if (statusMapping == null || statusMapping.isEmpty()) {
            throw new ScannerException("No status mapping configured");
        }
        
        // Look up mapped status
        String mappedStatus = statusMapping.get(status.toLowerCase());
        if (mappedStatus == null) {
            // Default: if not mapped, assume PROCESSING
            logger.warn("Unknown status '{}', assuming PROCESSING", status);
            return PollStatus.PROCESSING;
        }
        
        try {
            return PollStatus.valueOf(mappedStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ScannerException("Invalid mapped status: " + mappedStatus);
        }
    }
    
    /**
     * Parse scan result from response.
     */
    private Scanner.Result parseResult(
        String response,
        RemoteScannerProperties.HttpOperation operation
    ) throws ScannerException {
        RemoteScannerProperties.ResponseConfig responseConfig = operation.getResponse();
        if (responseConfig == null) {
            throw new ScannerException("No response config for result parsing");
        }
        
        // Check for errors first
        if (responseConfig.getErrorPath() != null) {
            String error = responseExtractor.extractString(
                response,
                responseConfig.getFormat(),
                responseConfig.getErrorPath()
            );
            if (error != null) {
                throw new ScannerException("Scanner reported error: " + error);
            }
        }
        
        String summary = null;
        if (responseConfig.getSummaryPath() != null) {
            summary = responseExtractor.extractString(
                response,
                responseConfig.getFormat(),
                responseConfig.getSummaryPath()
            );
        }

        // Extract threats
        String threatsPath = responseConfig.getThreatsPath();
        if (threatsPath == null) {
            // No threats path - assume clean
            return Scanner.Result.clean(summary);
        }
        
        List<Map<String, Object>> threatObjects = responseExtractor.extractList(
            response,
            responseConfig.getFormat(),
            threatsPath
        );
        
        // Map threats
        List<Scanner.Threat> threats = mapThreats(threatObjects, responseConfig);
        
        if (threats.isEmpty()) {
            return Scanner.Result.clean(summary);
        } else {
            return Scanner.Result.withThreats(threats, summary);
        }
    }
    
    /**
     * Map threat objects to ScanResult.Threat instances.
     */
    private List<Scanner.Threat> mapThreats(
        List<Map<String, Object>> threatObjects,
        RemoteScannerProperties.ResponseConfig responseConfig
    ) throws ScannerException {
        RemoteScannerProperties.ThreatMapping threatMapping = responseConfig.getThreatMapping();
        if (threatMapping == null) {
            throw new ScannerException("No threat mapping configured");
        }
        
        List<Scanner.Threat> threats = new ArrayList<>();
        
        for (Map<String, Object> threatObj : threatObjects) {
            // Check condition filter
            if (threatMapping.getCondition() != null) {
                boolean matches = responseExtractor.evaluateCondition(
                    threatObj,
                    threatMapping.getCondition()
                );
                if (!matches) {
                    continue;  // Skip this threat
                }
            }
            
            // Extract threat fields
            String name = extractThreatField(threatObj, threatMapping.getNamePath());
            String description = extractThreatField(threatObj, threatMapping.getDescriptionPath());
            String severity = extractThreatSeverity(threatObj, threatMapping);
            String filePath = extractThreatField(threatObj, threatMapping.getFilePathPath());
            String fileHash = extractThreatField(threatObj, threatMapping.getFileHashPath());
            
            threats.add(new Scanner.Threat(name, description, severity, filePath, fileHash));
        }
        
        return threats;
    }
    
    /**
     * Extract a threat field value.
     * Returns null if path is not configured or value not found.
     */
    private String extractThreatField(Map<String, Object> threatObj, String path) {
        if (path == null) {
            return null;
        }
        
        // Simple path extraction (e.g., "$.name" -> "name")
        String key = path.startsWith("$.") ? path.substring(2) : path;
        Object value = threatObj.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Extract threat severity using path or expression.
     * Always returns a non-null value (defaults to "MEDIUM").
     */
    @Nonnull
    private String extractThreatSeverity(
        Map<String, Object> threatObj,
        RemoteScannerProperties.ThreatMapping threatMapping
    ) {
        // Try expression first
        if (threatMapping.getSeverityExpression() != null) {
            String result = responseExtractor.evaluateExpression(
                threatObj,
                threatMapping.getSeverityExpression()
            );
            if (result != null) {
                return result;
            }
        }
        
        // Fall back to path
        if (threatMapping.getSeverityPath() != null) {
            String result = extractThreatField(threatObj, threatMapping.getSeverityPath());
            if (result != null) {
                return result;
            }
        }
        
        // Default severity
        return "MEDIUM";
    }
}
