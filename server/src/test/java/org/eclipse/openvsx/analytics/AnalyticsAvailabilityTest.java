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
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsAvailabilityTest {

    private final AtomicBoolean reachable = new AtomicBoolean(true);

    private AnalyticsAvailability availability(int failureThreshold) {
        return new AnalyticsAvailability(() -> {
            if (!reachable.get()) {
                throw new DataAccessException("down", new SQLTransientConnectionException("timeout"));
            }
        }, Duration.ofMinutes(10), failureThreshold);
    }

    @Test
    void isUnavailableUntilTheFirstProbeSucceeds() {
        var availability = availability(3);

        assertThat(availability.isAvailable()).isFalse();
        availability.runProbe(false);

        assertThat(availability.isAvailable()).isTrue();
    }

    @Test
    void startProbesImmediately() {
        try (var availability = availability(3)) {
            availability.start();

            assertThat(availability.isAvailable()).isTrue();
        }
    }

    @Test
    void startToleratesAnUnreachableDatabase() {
        reachable.set(false);

        try (var availability = availability(3)) {
            availability.start();

            assertThat(availability.isAvailable()).isFalse();
        }
    }

    @Test
    void startFailsOnAProblemThatIsNotConnectivity() {
        var rejected = new AnalyticsAvailability(() -> {
            throw new IllegalStateException("the migration was rejected");
        }, Duration.ofMinutes(10), 3);

        assertThatThrownBy(rejected::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailedProbeOpensTheCircuitAtOnce() {
        var availability = availability(3);
        availability.runProbe(false);
        reachable.set(false);

        availability.runProbe(false);

        assertThat(availability.isAvailable()).isFalse();
    }

    @Test
    void aSuccessfulProbeClosesTheCircuit() {
        var availability = availability(3);
        reachable.set(false);
        availability.runProbe(false);

        reachable.set(true);
        availability.runProbe(false);

        assertThat(availability.isAvailable()).isTrue();
    }

    @Test
    void failedCallsOpenTheCircuitOnlyAtTheThreshold() {
        var availability = availability(3);
        availability.runProbe(false);

        availability.recordFailure();
        availability.recordFailure();
        assertThat(availability.isAvailable()).isTrue();

        availability.recordFailure();
        assertThat(availability.isAvailable()).isFalse();
    }

    @Test
    void aLateSuccessfulCallDoesNotRestoreAvailability() {
        var availability = availability(3);
        reachable.set(false);
        availability.runProbe(false);
        assertThat(availability.isAvailable()).isFalse();

        // a call admitted before the outage finishes after it
        availability.recordSuccess();

        assertThat(availability.isAvailable()).isFalse();
    }

    @Test
    void failuresBeforeARecoveryDoNotCountTowardsTheNextOutage() {
        var availability = availability(3);
        availability.runProbe(false);
        availability.recordFailure();
        availability.recordFailure();

        availability.runProbe(false);
        availability.recordFailure();
        availability.recordFailure();

        assertThat(availability.isAvailable()).isTrue();
    }

    @Test
    void statusReportsTheLastProbeAndItsErrorUntilItSucceeds() {
        var availability = availability(3);
        availability.runProbe(false);
        var healthy = availability.status();
        assertThat(healthy.available()).isTrue();
        assertThat(healthy.lastProbe()).isNotNull();
        assertThat(healthy.lastError()).isNull();

        reachable.set(false);
        availability.runProbe(false);
        var down = availability.status();
        assertThat(down.available()).isFalse();
        assertThat(down.lastError()).isEqualTo("SQLTransientConnectionException: timeout");
        assertThat(down.since()).isAfterOrEqualTo(healthy.since());

        reachable.set(true);
        availability.runProbe(false);
        assertThat(availability.status().available()).isTrue();
        assertThat(availability.status().lastError()).isNull();
    }

    @Test
    void sinceChangesOnlyWhenTheStateDoes() throws Exception {
        var availability = availability(3);
        availability.runProbe(false);
        var since = availability.status().since();

        Thread.sleep(5);
        availability.runProbe(false);

        assertThat(availability.status().since()).isEqualTo(since);
    }

    @Test
    void aSuccessfulCallResetsTheFailureCount() {
        var availability = availability(3);
        availability.runProbe(false);

        availability.recordFailure();
        availability.recordFailure();
        availability.recordSuccess();
        availability.recordFailure();
        availability.recordFailure();

        assertThat(availability.isAvailable()).isTrue();
    }

    @Test
    void probesInTheBackgroundAfterStart() throws Exception {
        var probes = new AtomicInteger();
        try (var availability = new AnalyticsAvailability(probes::incrementAndGet, Duration.ofMillis(20), 3)) {
            availability.start();

            var deadline = System.currentTimeMillis() + 5000;
            while (probes.get() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            assertThat(probes.get()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void recognisesConnectivityFailuresAnywhereInTheCauseChain() {
        assertThat(AnalyticsAvailability.isConnectivityFailure(new SQLTransientConnectionException())).isTrue();
        assertThat(AnalyticsAvailability.isConnectivityFailure(new RuntimeException(new ConnectException()))).isTrue();
        assertThat(
                AnalyticsAvailability
                        .isConnectivityFailure(new DataAccessException("x", new SQLException("down", "08006"))))
                .isTrue();
        assertThat(
                AnalyticsAvailability
                        .isConnectivityFailure(new DataAccessException("x", new SQLException("down", "57P03"))))
                .isTrue();
    }

    @Test
    void doesNotTreatARejectedStatementAsAnOutage() {
        assertThat(
                AnalyticsAvailability
                        .isConnectivityFailure(
                                new DataAccessException("x", new SQLException("no such table", "42P01"))))
                .isFalse();
        assertThat(AnalyticsAvailability.isConnectivityFailure(null)).isFalse();
    }
}
