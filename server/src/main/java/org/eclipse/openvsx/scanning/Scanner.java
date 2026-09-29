/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    record Command(long extensionVersionId, @NonNull String scanId) {}

    /**
     * Represents a scan that has been submitted to an external service.
     */
    record Submission(@NonNull String externalJobId, @Nullable Map<String, String> fileHashes) {
        public Submission(@NonNull String externalJobId) {
            this(externalJobId, null);
        }

        @NonNull
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
        SUBMITTED, PROCESSING, COMPLETED, FAILED
    }

    /**
     * Result of a completed scan.
     */
    class Result {
        private final boolean clean;
        private final List<Threat> threats;
        private final String summary;
        private final Boolean maliciousVerdict;

        private Result(boolean clean, List<Threat> threats, String summary, Boolean maliciousVerdict) {
            this.clean = clean;
            this.threats = new ArrayList<>(threats);
            this.summary = summary;
            this.maliciousVerdict = maliciousVerdict;
        }

        @NonNull
        public static Result clean() {
            return new Result(true, Collections.emptyList(), null, null);
        }

        @NonNull
        public static Result clean(@Nullable String summary) {
            return new Result(true, Collections.emptyList(), summary, null);
        }

        @NonNull
        public static Result withThreats(@NonNull List<Threat> threats) {
            return new Result(false, threats, null, null);
        }

        @NonNull
        public static Result withThreats(@NonNull List<Threat> threats, @Nullable String summary) {
            return new Result(false, threats, summary, null);
        }

        /**
         * Builds a result directly from a scanner's findings and its explicit malicious verdict (e.g. Argus
         * verdictData.isMalicious). Prefer this over clean()/withThreats() whenever a verdict is available: naming a
         * malicious result via "clean" reads as contradictory, since findings and the safety verdict are independent
         * signals here. When a verdict is present it is the sole gatekeeper for isClean(), overriding the
         * raw finding count; without a verdict, "clean" falls back to whether any findings were recorded.
         */
        @NonNull
        public static Result of(@NonNull List<Threat> threats, @Nullable String summary, @Nullable Boolean malicious) {
            boolean clean = malicious != null ? !malicious : threats.isEmpty();
            return new Result(clean, threats, summary, malicious);
        }

        /**
         * True when the scan found nothing that should block publishing. If an explicit malicious verdict is present,
         * it alone determines this value: a malicious verdict is never clean and a benign verdict always is, no
         * matter how many findings were recorded alongside it - see hasMaliciousVerdict() and hasBenignVerdict().
         * Without a verdict, this simply reflects whether the threats list is empty.
         */
        public boolean isClean() {
            return clean;
        }

        @NonNull
        public List<Threat> getThreats() {
            return Collections.unmodifiableList(threats);
        }

        /** Scanner-provided summary text, or null if not supplied. */
        @Nullable
        public String getSummary() {
            return summary;
        }

        /**
         * Explicit malicious verdict from the scanner response, if configured. null means the scanner does not expose
         * this signal.
         */
        @Nullable
        public Boolean isMalicious() {
            return maliciousVerdict;
        }

        /** True when the scanner explicitly returned a malicious verdict. */
        public boolean hasMaliciousVerdict() {
            return Boolean.TRUE.equals(maliciousVerdict);
        }

        /** True when the scanner explicitly returned a non-malicious verdict. */
        public boolean hasBenignVerdict() {
            return Boolean.FALSE.equals(maliciousVerdict);
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

        public Threat(@NonNull String name, @Nullable String description, @NonNull String severity) {
            this(name, description, severity, null, null);
        }

        public Threat(
                @NonNull String name,
                @Nullable String description,
                @NonNull String severity,
                @Nullable String filePath
        ) {
            this(name, description, severity, filePath, null);
        }

        public Threat(
                @NonNull String name,
                @Nullable String description,
                @NonNull String severity,
                @Nullable String filePath,
                @Nullable String fileHash
        ) {
            this.name = name;
            this.description = description;
            this.severity = severity;
            this.filePath = filePath;
            this.fileHash = fileHash;
        }

        @NonNull
        public String getName() {
            return name;
        }
        @Nullable
        public String getDescription() {
            return description;
        }
        @NonNull
        public String getSeverity() {
            return severity;
        }
        @Nullable
        public String getFilePath() {
            return filePath;
        }
        @Nullable
        public String getFileHash() {
            return fileHash;
        }
    }

    /**
     * Result of invoking a scanner.
     * <p>
     * A scanner returns either:
     * - Completed: Sync scan with immediate results
     * - Submitted: Async scan that requires polling
     */
    sealed interface Invocation {
        record Completed(@NonNull Result result) implements Invocation {}
        record Submitted(@NonNull Submission submission) implements Invocation {}
    }

    /**
     * Returns the unique type identifier for this scanner.
     */
    @NonNull
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
    default int getMaxConcurrency() {
        return -1;
    }

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
    default int getMaxQueueWaitMinutes() {
        return 120;
    }

    /**
     * Get the polling configuration for this async scanner.
     * Returns null to use defaults.
     */
    default RemoteScannerProperties.@Nullable PollConfig getPollConfig() {
        return null;
    }

    /**
     * Start a scan and return the invocation result.
     */
    @NonNull
    Invocation startScan(@NonNull Command command) throws ScannerException;

    /**
     * Poll status of an async scan job.
     */
    @NonNull
    default PollStatus pollStatus(@NonNull Submission submission) throws ScannerException {
        throw new UnsupportedOperationException(
                "Scanner " + getScannerType() + " does not support polling");
    }

    /**
     * Retrieve final results from an async scan job.
     */
    @NonNull
    default Result fetchResults(@NonNull Submission submission) throws ScannerException {
        throw new UnsupportedOperationException(
                "Scanner " + getScannerType() + " does not support result retrieval");
    }
}
