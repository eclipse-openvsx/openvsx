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
package org.eclipse.openvsx.analytics;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether the time-series database is reachable. Analytics is optional, so while it is not the
 * registry keeps serving and everything analytics-related fails fast instead of waiting on a
 * connection timeout.
 * <p>
 * Two signals feed it. A periodic probe is authoritative: it opens or closes the circuit on its
 * own. Calls that fail with a connectivity error also count, and open the circuit after
 * {@code failureThreshold} in a row, so an outage is noticed between probes. While the circuit is
 * open nothing else reaches the database, so only the probe can close it again.
 * <p>
 * It starts out unavailable and becomes available with the first successful probe.
 */
public class AnalyticsAvailability implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsAvailability.class);

    private final Runnable probe;
    private final Duration probeInterval;
    private final int failureThreshold;

    // Written under the lock, so that the failure count and the state change together; read without it.
    private final Object lock = new Object();
    private volatile boolean available;
    private int consecutiveFailures;
    private Instant since = Instant.now();
    private @Nullable Instant lastProbe;
    private @Nullable String lastError;
    private @Nullable ScheduledExecutorService scheduler;

    /**
     * What the admin dashboard shows: the current verdict, since when it holds, and what the last
     * probe found. The error is the last probe's, and absent once a probe succeeded.
     */
    public record Status(boolean available, Instant since, @Nullable Instant lastProbe, @Nullable String lastError) {}

    /**
     * @param probe            touches the database and throws when it cannot be used
     * @param probeInterval    delay between two probes
     * @param failureThreshold consecutive failed calls that open the circuit between probes
     */
    public AnalyticsAvailability(Runnable probe, Duration probeInterval, int failureThreshold) {
        this.probe = probe;
        this.probeInterval = probeInterval;
        this.failureThreshold = failureThreshold;
    }

    public boolean isAvailable() {
        return available;
    }

    public Status status() {
        synchronized (lock) {
            return new Status(available, since, lastProbe, lastError);
        }
    }

    /** Must be called with the lock held. */
    private boolean setAvailable(boolean value) {
        if (available == value) {
            return false;
        }

        available = value;
        since = Instant.now();
        return true;
    }

    /**
     * Resets the count of failed calls. It never reopens an open circuit: a call admitted before an
     * outage can finish after it, so only a successful probe restores availability.
     */
    public void recordSuccess() {
        synchronized (lock) {
            consecutiveFailures = 0;
        }
    }

    public void recordFailure() {
        synchronized (lock) {
            if (++consecutiveFailures >= failureThreshold) {
                setAvailable(false);
            }
        }
    }

    /**
     * Probes once, then keeps probing in the background. A failure that is not a connectivity
     * problem, such as a schema the migration rejects, fails this first probe so the startup fails
     * with it; an unreachable database does not.
     */
    public synchronized void start() {
        runProbe(true);
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "analytics-availability");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> runProbe(false),
                probeInterval.toMillis(),
                probeInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    void runProbe(boolean startup) {
        try {
            probe.run();
            boolean restored;
            synchronized (lock) {
                consecutiveFailures = 0;
                lastProbe = Instant.now();
                lastError = null;
                restored = setAvailable(true);
            }
            if (restored) {
                logger.info("the analytics database is reachable, analytics is available");
            }
        } catch (RuntimeException e) {
            if (startup && !isConnectivityFailure(e)) {
                throw e;
            }

            boolean opened;
            synchronized (lock) {
                consecutiveFailures = failureThreshold;
                lastProbe = Instant.now();
                lastError = describe(e);
                opened = setAvailable(false);
            }
            if (opened || startup) {
                logger.warn("the analytics database is unreachable, analytics is unavailable until it is", e);
            } else {
                logger.debug("the analytics database is still unreachable", e);
            }
        }
    }

    private static String describe(Throwable failure) {
        var root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        var description = root.getClass().getSimpleName() + ": " + root.getMessage();
        return description.length() > 300 ? description.substring(0, 300) : description;
    }

    /**
     * Whether the failure, or anything it was caused by, says the database could not be reached
     * rather than that a statement was wrong.
     */
    public static boolean isConnectivityFailure(@Nullable Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof SocketTimeoutException) {
                return true;
            }
            // 08: connection exception, 57P01/57P03: the server is shutting down or not accepting connections
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P0"))) {
                return true;
            }
        }
        return false;
    }
}
