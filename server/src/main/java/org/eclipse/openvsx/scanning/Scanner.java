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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base interface for all scanner implementations.
 * <p>
 * Each scanner implementation (internal checks, external scanners, etc.)
 * must implement this interface. Scanners can be either synchronous
 * (returning results immediately) or asynchronous (requiring polling).
 */
public interface Scanner {
    
    /**
     * Command to start a scan. Contains metadata about what to scan.
     * Scanners retrieve the actual file via extensionVersionId using ScannerFileService.
     */
    record Command(long extensionVersionId, @Nonnull String scanId) {}
    
    /**
     * Represents a scan that has been submitted to an external service.
     */
    record Submission(@Nonnull String externalJobId, @Nullable Map<String, String> fileHashes) {
        public Submission(@Nonnull String externalJobId) {
            this(externalJobId, null);
        }
        
        @Nonnull
        public Map<String, String> fileHashes() {
            return fileHashes != null ? fileHashes : Collections.emptyMap();
        }
        
        public boolean hasFileHashes() {
            return fileHashes != null && !fileHashes.isEmpty();
        }
    }
    
    /**
     * Status returned by {@link #pollStatus(Submission)}.
     */
    enum PollStatus {
        SUBMITTED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    /**
     * Result of a completed scan.
     */
    class Result {
        private final boolean clean;
        private final List<Threat> threats;
        private final String summary;

        private Result(boolean clean, List<Threat> threats, String summary) {
            this.clean = clean;
            this.threats = new ArrayList<>(threats);
            this.summary = summary;
        }
        
        @Nonnull
        public static Result clean() {
            return new Result(true, Collections.emptyList(), null);
        }

        @Nonnull
        public static Result clean(@Nullable String summary) {
            return new Result(true, Collections.emptyList(), summary);
        }

        @Nonnull
        public static Result withThreats(@Nonnull List<Threat> threats) {
            return new Result(false, threats, null);
        }

        @Nonnull
        public static Result withThreats(@Nonnull List<Threat> threats, @Nullable String summary) {
            return new Result(false, threats, summary);
        }
        
        public boolean isClean() {
            return clean;
        }
        
        @Nonnull
        public List<Threat> getThreats() {
            return Collections.unmodifiableList(threats);
        }

        /** Scanner-provided summary text, or null if not supplied. */
        @Nullable
        public String getSummary() {
            return summary;
        }
    }
    
    /**
     * A security threat found during scanning.
     */
    class Threat {
        private final String name;
        private final String description;
        private final String severity;
        private final String filePath;
        private final String fileHash;
        
        public Threat(@Nonnull String name, @Nullable String description, @Nonnull String severity) {
            this(name, description, severity, null, null);
        }
        
        public Threat(@Nonnull String name, @Nullable String description, @Nonnull String severity, @Nullable String filePath) {
            this(name, description, severity, filePath, null);
        }
        
        public Threat(@Nonnull String name, @Nullable String description, @Nonnull String severity, @Nullable String filePath, @Nullable String fileHash) {
            this.name = name;
            this.description = description;
            this.severity = severity;
            this.filePath = filePath;
            this.fileHash = fileHash;
        }
        
        @Nonnull public String getName() { return name; }
        @Nullable public String getDescription() { return description; }
        @Nonnull public String getSeverity() { return severity; }
        @Nullable public String getFilePath() { return filePath; }
        @Nullable public String getFileHash() { return fileHash; }
    }
    
    /**
     * Result of invoking a scanner.
     * <p>
     * A scanner returns either:
     * - Completed: Sync scan with immediate results
     * - Submitted: Async scan that requires polling
     */
    sealed interface Invocation {
        record Completed(@Nonnull Result result) implements Invocation {}
        record Submitted(@Nonnull Submission submission) implements Invocation {}
    }
    
    /**
     * Returns the unique type identifier for this scanner.
     */
    @Nonnull
    String getScannerType();
    
    /**
     * Indicates if this scanner is required for extension activation.
     * If true: Scanner failure blocks activation (fail-closed)
     * If false: Scanner failure is logged but extension can still activate
     */
    default boolean isRequired() {
        return true;
    }
    
    /**
     * Indicates if threats from this scanner should block extension activation.
     * If true: Threats quarantine the extension
     * If false: Threats are logged as warnings but extension can activate
     */
    default boolean enforcesThreats() {
        return true;
    }
    
    /**
     * Returns the timeout duration in minutes for async scanners.
     */
    default int getTimeoutMinutes() {
        return 60;
    }
    
    /**
     * Indicates if this scanner is asynchronous.
     */
    boolean isAsync();
    
    /**
     * Maximum concurrent invocations across all server pods.
     * -1 means no limit (default). Positive values enable the concurrency dispatcher
     * which promotes QUEUED jobs in FIFO order.
     */
    default int getMaxConcurrency() { return -1; }

    /**
     * Build a user-facing URL pointing at the scanner's own dashboard for a
     * given external job id. Used by the admin UI to deep-link from a scan
     * check result to the scanner service's UI for debugging / detail.
     * <p>
     * Default returns null — scanners that don't have an external UI
     * (internal checks, self-hosted services without a web UI) simply don't
     * contribute a link.
     */
    @Nullable
    default String buildExternalUrl(@Nullable String externalJobId) {
        return null;
    }

    /**
     * Maximum time (minutes) a concurrency-limited job may wait in QUEUED status
     * before being failed. Prevents unbounded queue growth.
     * Only relevant when maxConcurrency > 0.
     */
    default int getMaxQueueWaitMinutes() { return 120; }
    
    /**
     * Get the polling configuration for this async scanner.
     * Returns null to use defaults.
     */
    @Nullable
    default RemoteScannerProperties.PollConfig getPollConfig() {
        return null;
    }
    
    /**
     * Start a scan and return the invocation result.
     */
    @Nonnull
    Invocation startScan(@Nonnull Command command) throws ScannerException;
    
    /**
     * Poll status of an async scan job.
     */
    @Nonnull
    default PollStatus pollStatus(@Nonnull Submission submission) throws ScannerException {
        throw new UnsupportedOperationException(
            "Scanner " + getScannerType() + " does not support polling"
        );
    }
    
    /**
     * Retrieve final results from an async scan job.
     */
    @Nonnull
    default Result fetchResults(@Nonnull Submission submission) throws ScannerException {
        throw new UnsupportedOperationException(
            "Scanner " + getScannerType() + " does not support result retrieval"
        );
    }
}
