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

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for remote scanners (ovsx.scanning.configured.*).
 * <p>
 * Defines scanner behavior via YAML: HTTP request templates, response extraction
 * rules (JSONPath), state mapping, and timeout policies. Validated at startup.
 * <p>
 * Environment variables (${VAR_NAME} syntax) are resolved by Spring when loading
 * application.yml. Sensitive values like API keys should use this mechanism.
 */
@ConfigurationProperties(prefix = "ovsx.scanning")
@Validated
public class RemoteScannerProperties {
    
    /**
     * Map of scanner name to scanner configuration under the 'configured' key.
     * 
     * Each scanner configuration is validated to ensure required fields are present.
     * 
     * Example YAML:
     * <pre>
     * ovsx:
     *   scanning:
     *     configured:
     *       my-scanner:
     *         enabled: true
     *         type: "MY_SCANNER"
     *         required: false
     *         timeout-minutes: 120
     *         async: true
     *         
     *         # Start scan operation
     *         start:
     *           method: POST
     *           url: "https://api.scanner.example.com/v1/scans"
     *           headers:
     *             x-api-key: "${SCANNER_API_KEY}"
     *           body:
     *             type: multipart
     *             file-field: "file"
     *           response:
     *             job-id-path: "$.scan.id"
     *         
     *         # Poll status operation
     *         poll:
     *           method: GET
     *           url: "https://api.scanner.example.com/v1/scans/{jobId}"
     *           headers:
     *             x-api-key: "${SCANNER_API_KEY}"
     *           response:
     *             status-path: "$.scan.status"
     *             status-mapping:
     *               queued: SUBMITTED
     *               running: PROCESSING
     *               done: COMPLETED
     *         
     *         # Get results operation
     *         result:
     *           method: GET
     *           url: "https://api.scanner.example.com/v1/scans/{jobId}/results"
     *           headers:
     *             x-api-key: "${SCANNER_API_KEY}"
     *           response:
     *             threats-path: "$.findings"
     *             threat-mapping:
     *               name-path: "$.rule"
     *               description-path: "$.message"
     *               severity-path: "$.severity"
     *               file-path: "$.file"
     * </pre>
     */
    @Valid
    private Map<String, @Valid ScannerConfig> configured = new HashMap<>();
    
    /**
     * Get the configured scanners map.
     * 
     * This matches the YAML structure: ovsx.scanning.configured.<scanner-name>
     */
    public Map<String, ScannerConfig> getConfigured() {
        return configured;
    }
    
    public void setConfigured(Map<String, ScannerConfig> configured) {
        this.configured = configured;
    }
    
    /**
     * Convenience method to get scanners (delegates to getConfigured).
     */
    public Map<String, ScannerConfig> getScanners() {
        return configured;
    }
    
    /**
     * Configuration for a single scanner.
     * 
     * Validated to ensure required fields are present at startup.
     */
    public static class ScannerConfig {
        private boolean enabled = false;
        
        @NotBlank(message = "Scanner type must be specified")
        private String type;  // Unique scanner type identifier
        
        private boolean required = true;
        
        /**
         * Whether threats from this scanner block extension activation.
         * If false, threats are recorded but extension is still activated.
         * Defaults to true (threats block activation).
         */
        private boolean enforced = true;
        
        @Min(value = 1, message = "Timeout must be at least 1 minute")
        private int timeoutMinutes = 60;
        private boolean async = true;
        
        /**
         * Maximum concurrent invocations for this scanner across all server pods.
         * Positive values enable the concurrency dispatcher which promotes
         * QUEUED jobs in FIFO order. Set to -1 (default) for unlimited.
         * Zero is not allowed — use -1 for unlimited.
         */
        @Min(value = -1, message = "max-concurrency must be -1 (unlimited) or a positive integer")
        private int maxConcurrency = -1;

        /**
         * Maximum time (minutes) a job may wait in QUEUED status before being
         * failed. Only relevant for concurrency-limited scanners (maxConcurrency > 0).
         * Prevents unbounded queue growth if the external service is down.
         * Default: 120 (2 hours).
         */
        @Min(value = 1, message = "max-queue-wait-minutes must be at least 1")
        private int maxQueueWaitMinutes = 120;
        
        /**
         * HTTP client configuration for this scanner.
         * 
         * Allows tuning connection pooling and timeouts per-scanner.
         * Different scanners may have different performance characteristics.
         */
        @Valid
        private HttpConfig http = new HttpConfig();
        
        /**
         * Authentication configuration for this scanner.
         * 
         * Supports multiple auth types:
         * - api-key: API key in header or query param
         * - bearer: Bearer token in Authorization header
         * - basic: Basic auth (username:password)
         * - oauth2: OAuth2 client credentials flow
         * 
         * If not set, no authentication is applied (use manual headers instead).
         */
        @Valid
        private AuthConfig auth;
        
        /**
         * Polling configuration for async scanners.
         * 
         * Controls how often to poll and when to stop.
         * Only used when async=true.
         */
        @Valid
        private PollConfig polling = new PollConfig();

        /**
         * Optional template for a user-facing URL pointing at the scanner's own
         * dashboard for this scan. Supports the {jobId} placeholder which is
         * replaced with the scanner's externalJobId.
         *
         * Example: "https://app.example.com/scans/{jobId}"
         *
         * When set, the scan admin dashboard renders a "View in <scanner>" link
         * for each scanner job that has a known externalJobId.
         */
        @Nullable
        private String externalUrlTemplate;
        
        // Operation configurations
        @NotNull(message = "Start operation must be configured")
        @Valid
        private HttpOperation start;
        
        @Valid
        private HttpOperation poll;
        
        @Valid
        private HttpOperation result;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        
        public boolean isEnforced() { return enforced; }
        public void setEnforced(boolean enforced) { this.enforced = enforced; }
        
        public int getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        
        public boolean isAsync() { return async; }
        public void setAsync(boolean async) { this.async = async; }
        
        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
        
        public int getMaxQueueWaitMinutes() { return maxQueueWaitMinutes; }
        public void setMaxQueueWaitMinutes(int maxQueueWaitMinutes) { this.maxQueueWaitMinutes = maxQueueWaitMinutes; }
        
        public HttpConfig getHttp() { return http; }
        public void setHttp(HttpConfig http) { this.http = http; }
        
        public AuthConfig getAuth() { return auth; }
        public void setAuth(AuthConfig auth) { this.auth = auth; }
        
        public PollConfig getPolling() { return polling; }
        public void setPolling(PollConfig polling) { this.polling = polling; }

        @Nullable
        public String getExternalUrlTemplate() { return externalUrlTemplate; }
        public void setExternalUrlTemplate(@Nullable String externalUrlTemplate) { this.externalUrlTemplate = externalUrlTemplate; }
        
        public HttpOperation getStart() { return start; }
        public void setStart(HttpOperation start) { this.start = start; }
        
        public HttpOperation getPoll() { return poll; }
        public void setPoll(HttpOperation poll) { this.poll = poll; }
        
        public HttpOperation getResult() { return result; }
        public void setResult(HttpOperation result) { this.result = result; }
    }
    
    /**
     * Configuration for an HTTP operation (start, poll, or result).
     * 
     * Validated to ensure URL and response config are present.
     */
    public static class HttpOperation {
        private String method = "GET";  // GET, POST, PUT, etc.
        
        @NotBlank(message = "URL must be specified for HTTP operation")
        private String url;  // URL template with {jobId} placeholder
        
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> queryParams = new HashMap<>();
        
        @Valid
        private BodyConfig body;
        
        @NotNull(message = "Response configuration must be specified")
        @Valid
        private ResponseConfig response;
        
        @Valid
        private RetryConfig retry;
        
        // Getters and setters
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public Map<String, String> getQueryParams() { return queryParams; }
        public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams; }
        
        public BodyConfig getBody() { return body; }
        public void setBody(BodyConfig body) { this.body = body; }
        
        public ResponseConfig getResponse() { return response; }
        public void setResponse(ResponseConfig response) { this.response = response; }
        
        public RetryConfig getRetry() { return retry; }
        public void setRetry(RetryConfig retry) { this.retry = retry; }
        
        /**
         * Create a shallow copy of this operation for thread-safe processing.
         * URL, headers, and queryParams are copied; other fields reference originals.
         */
        public HttpOperation copy() {
            HttpOperation copy = new HttpOperation();
            copy.method = this.method;
            copy.url = this.url;
            copy.headers = new HashMap<>(this.headers);
            copy.queryParams = new HashMap<>(this.queryParams);
            copy.body = this.body;          // Shared (read-only during execution)
            copy.response = this.response;  // Shared (read-only during execution)
            copy.retry = this.retry;        // Shared (read-only during execution)
            return copy;
        }
    }
    
    /**
     * Configuration for request body.
     */
    public static class BodyConfig {
        private String type = "json";  // json, multipart, form-urlencoded, xml, raw
        private String fileField = "file";  // Field name for file in multipart
        private Map<String, String> fields = new HashMap<>();  // Additional fields
        private String template;  // Template for json/xml/raw body
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getFileField() { return fileField; }
        public void setFileField(String fileField) { this.fileField = fileField; }
        
        public Map<String, String> getFields() { return fields; }
        public void setFields(Map<String, String> fields) { this.fields = fields; }
        
        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }
    }
    
    /**
     * Configuration for parsing responses.
     */
    public static class ResponseConfig {
        private String format = "json";  // json, xml, text
        
        // For extracting job ID (start operation)
        private String jobIdPath;  // JSONPath or XPath
        
        // For extracting status (poll operation)
        private String statusPath;  // JSONPath or XPath
        private Map<String, String> statusMapping = new HashMap<>();  // Maps scanner status to ScannerProvider.PollStatus
        
        // For extracting threats (result operation)
        private String threatsPath;  // JSONPath to array of threats
        private ThreatMapping threatMapping;

        // For extracting a scanner-provided summary string (result operation).
        // Surfaced verbatim in ScanCheckResult.summary when present
        private String summaryPath;
        
        // For error detection
        private String errorPath;  // Path to error message
        private String errorCondition;  // Expression to detect errors
        
        // Getters and setters
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public String getJobIdPath() { return jobIdPath; }
        public void setJobIdPath(String jobIdPath) { this.jobIdPath = jobIdPath; }
        
        public String getStatusPath() { return statusPath; }
        public void setStatusPath(String statusPath) { this.statusPath = statusPath; }
        
        public Map<String, String> getStatusMapping() { return statusMapping; }
        public void setStatusMapping(Map<String, String> statusMapping) { this.statusMapping = statusMapping; }
        
        public String getThreatsPath() { return threatsPath; }
        public void setThreatsPath(String threatsPath) { this.threatsPath = threatsPath; }
        
        public ThreatMapping getThreatMapping() { return threatMapping; }
        public void setThreatMapping(ThreatMapping threatMapping) { this.threatMapping = threatMapping; }

        public String getSummaryPath() { return summaryPath; }
        public void setSummaryPath(String summaryPath) { this.summaryPath = summaryPath; }

        public String getErrorPath() { return errorPath; }
        public void setErrorPath(String errorPath) { this.errorPath = errorPath; }
        
        public String getErrorCondition() { return errorCondition; }
        public void setErrorCondition(String errorCondition) { this.errorCondition = errorCondition; }
    }
    
    /**
     * Configuration for mapping scanner threats to ScanResult.Threat.
     */
    public static class ThreatMapping {
        private String namePath;  // JSONPath to threat name
        private String descriptionPath;  // JSONPath to threat description
        private String severityPath;  // JSONPath to severity
        private String severityExpression;  // Expression to compute severity
        private String filePathPath;  // JSONPath to file path
        private String fileHashPath;  // JSONPath to file hash (SHA256)
        private String condition;  // Expression to filter threats (e.g., "$.detected == true")
        
        // Getters and setters
        public String getNamePath() { return namePath; }
        public void setNamePath(String namePath) { this.namePath = namePath; }
        
        public String getDescriptionPath() { return descriptionPath; }
        public void setDescriptionPath(String descriptionPath) { this.descriptionPath = descriptionPath; }
        
        public String getSeverityPath() { return severityPath; }
        public void setSeverityPath(String severityPath) { this.severityPath = severityPath; }
        
        public String getSeverityExpression() { return severityExpression; }
        public void setSeverityExpression(String severityExpression) { this.severityExpression = severityExpression; }
        
        public String getFilePathPath() { return filePathPath; }
        public void setFilePathPath(String filePathPath) { this.filePathPath = filePathPath; }
        
        public String getFileHashPath() { return fileHashPath; }
        public void setFileHashPath(String fileHashPath) { this.fileHashPath = fileHashPath; }
        
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
    }
    
    /**
     * Configuration for retry behavior.
     */
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private double multiplier = 2.0;
        private long maxDelayMs = 30000;
        
        // Getters and setters
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
    }
    
    /**
     * HTTP client configuration for a scanner.
     * 
     * Allows tuning connection pooling and timeouts per-scanner.
     * Different scanners may have different performance characteristics
     * (e.g., some scanners need longer timeouts for large files).
     */
    public static class HttpConfig {
        /** Maximum total connections in the pool */
        private int maxTotal = 10;
        
        /** Maximum connections per route (per host) */
        private int defaultMaxPerRoute = 5;
        
        /** Timeout to get a connection from the pool (ms) */
        private int connectionRequestTimeoutMs = 30000;
        
        /** Timeout to establish a TCP connection (ms) */
        private int connectTimeoutMs = 30000;
        
        /** 
         * Timeout to receive a response after sending request (ms).
         * This is the "read timeout" - how long to wait for the scanner to respond.
         * Increase for scanners that process large files.
         * Default: 300000 (5 minutes)
         */
        private int socketTimeoutMs = 300000;
        
        // Getters and setters
        public int getMaxTotal() { return maxTotal; }
        public void setMaxTotal(int maxTotal) { this.maxTotal = maxTotal; }
        
        public int getDefaultMaxPerRoute() { return defaultMaxPerRoute; }
        public void setDefaultMaxPerRoute(int defaultMaxPerRoute) { this.defaultMaxPerRoute = defaultMaxPerRoute; }
        
        public int getConnectionRequestTimeoutMs() { return connectionRequestTimeoutMs; }
        public void setConnectionRequestTimeoutMs(int connectionRequestTimeoutMs) { this.connectionRequestTimeoutMs = connectionRequestTimeoutMs; }
        
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        
        public int getSocketTimeoutMs() { return socketTimeoutMs; }
        public void setSocketTimeoutMs(int socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }
    }
    
    /**
     * Authentication configuration for a scanner.
     * 
     * Supports multiple authentication types commonly used by security APIs:
     * - api-key: API key sent in header or query parameter
     * - bearer: Bearer token in Authorization header
     * - basic: HTTP Basic authentication (username:password)
     * - oauth2: OAuth2 client credentials flow (auto token refresh)
     * 
     * Example configurations:
     * <pre>
     * # API Key in header
     * auth:
     *   type: api-key
     *   api-key:
     *     key: "${SCANNER_API_KEY}"
     *     header-name: x-api-key
     * 
     * # Bearer token
     * auth:
     *   type: bearer
     *   bearer:
     *     token: "${SCANNER_TOKEN}"
     * 
     * # Basic auth
     * auth:
     *   type: basic
     *   basic:
     *     username: "${SCANNER_USER}"
     *     password: "${SCANNER_PASSWORD}"
     * 
     * # OAuth2 client credentials
     * auth:
     *   type: oauth2
     *   oauth2:
     *     token-url: "https://auth.scanner.example.com/oauth/token"
     *     client-id: "${SCANNER_CLIENT_ID}"
     *     client-secret: "${SCANNER_CLIENT_SECRET}"
     *     scope: "scan:read scan:write"
     * </pre>
     */
    public static class AuthConfig {
        /**
         * Authentication type: api-key, bearer, basic, oauth2
         */
        private String type;
        
        /** API key configuration (when type=api-key) */
        @Valid
        private ApiKeyAuth apiKey;
        
        /** Bearer token configuration (when type=bearer) */
        @Valid
        private BearerAuth bearer;
        
        /** Basic auth configuration (when type=basic) */
        @Valid
        private BasicAuth basic;
        
        /** OAuth2 configuration (when type=oauth2) */
        @Valid
        private OAuth2Auth oauth2;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public ApiKeyAuth getApiKey() { return apiKey; }
        public void setApiKey(ApiKeyAuth apiKey) { this.apiKey = apiKey; }
        
        public BearerAuth getBearer() { return bearer; }
        public void setBearer(BearerAuth bearer) { this.bearer = bearer; }
        
        public BasicAuth getBasic() { return basic; }
        public void setBasic(BasicAuth basic) { this.basic = basic; }
        
        public OAuth2Auth getOauth2() { return oauth2; }
        public void setOauth2(OAuth2Auth oauth2) { this.oauth2 = oauth2; }
    }
    
    /**
     * API Key authentication configuration.
     */
    public static class ApiKeyAuth {
        /** The API key value */
        private String key;
        
        /** Header name for the API key (e.g., "x-apikey", "Authorization") */
        private String headerName;
        
        /** Query parameter name (alternative to header) */
        private String queryParam;
        
        /** Prefix to add before key value (e.g., "ApiKey " for "ApiKey xxx") */
        private String prefix = "";
        
        // Getters and setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
        
        public String getQueryParam() { return queryParam; }
        public void setQueryParam(String queryParam) { this.queryParam = queryParam; }
        
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
    }
    
    /**
     * Bearer token authentication configuration.
     */
    public static class BearerAuth {
        /** The bearer token */
        private String token;
        
        // Getters and setters
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
    
    /**
     * HTTP Basic authentication configuration.
     */
    public static class BasicAuth {
        /** Username */
        private String username;
        
        /** Password */
        private String password;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    /**
     * OAuth2 client credentials flow configuration.
     * <p>
     * Tokens are automatically refreshed when they expire.
     */
    public static class OAuth2Auth {
        /** Token endpoint URL */
        private String tokenUrl;
        
        /** OAuth2 client ID */
        private String clientId;
        
        /** OAuth2 client secret */
        private String clientSecret;
        
        /** OAuth2 scopes (space-separated) */
        private String scope;
        
        /** Seconds to refresh token before it expires (default: 60) */
        private int refreshBeforeExpiry = 60;
        
        // Getters and setters
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        
        public int getRefreshBeforeExpiry() { return refreshBeforeExpiry; }
        public void setRefreshBeforeExpiry(int refreshBeforeExpiry) { this.refreshBeforeExpiry = refreshBeforeExpiry; }
    }
    
    /**
     * Polling configuration for async scanners.
     * <p>
     * Controls how frequently to poll for scan status and when to stop.
     * Only applies when scanner is configured with async=true.
     */
    public static class PollConfig {
        
        /** Shared default poll config instance. Use this instead of creating new instances. */
        public static final PollConfig DEFAULT = new PollConfig();
        
        /**
         * Initial delay before first poll (seconds).
         * Give the scanner time to start processing.
         * Default: 5 seconds
         */
        private int initialDelaySeconds = 5;
        
        /**
         * Interval between polls (seconds).
         * How long to wait between status checks.
         * Default: 30 seconds
         */
        private int intervalSeconds = 30;
        
        /**
         * Maximum number of poll attempts before giving up.
         * After this many attempts, the scan is marked as timed out.
         * Default: 60 (with 30s interval = 30 minutes of polling)
         */
        private int maxAttempts = 60;
        
        /**
         * Whether to use exponential backoff for poll intervals.
         * If true, interval doubles after each poll (up to maxIntervalSeconds).
         * Useful for APIs with strict rate limits.
         * Default: false
         */
        private boolean exponentialBackoff = false;
        
        /**
         * Maximum interval when using exponential backoff (seconds).
         * Default: 300 (5 minutes)
         */
        private int maxIntervalSeconds = 300;
        
        /**
         * Backoff multiplier when using exponential backoff.
         * Default: 2.0 (double the interval each time)
         */
        private double backoffMultiplier = 2.0;
        
        // Getters and setters
        public int getInitialDelaySeconds() { return initialDelaySeconds; }
        public void setInitialDelaySeconds(int initialDelaySeconds) { this.initialDelaySeconds = initialDelaySeconds; }
        
        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public boolean isExponentialBackoff() { return exponentialBackoff; }
        public void setExponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; }
        
        public int getMaxIntervalSeconds() { return maxIntervalSeconds; }
        public void setMaxIntervalSeconds(int maxIntervalSeconds) { this.maxIntervalSeconds = maxIntervalSeconds; }
        
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    }
}
