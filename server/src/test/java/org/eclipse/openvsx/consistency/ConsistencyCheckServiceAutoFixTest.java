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
package org.eclipse.openvsx.consistency;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.LogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ConsistencyCheckService#runAllChecks()}'s auto-fix behavior in isolation, using a fake
 * {@link ConsistencyCheck} rather than the real (always-auto-fixing) {@link ExtensionActiveFlagCheck}, so
 * both the opt-in and opt-out paths of {@link ConsistencyCheck#autoFixOnSchedule()} are exercised
 * without needing a database.
 */
class ConsistencyCheckServiceAutoFixTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final RepositoryService repositories = mock(RepositoryService.class);
    private final LogService logs = mock(LogService.class);
    private final UserData systemUser = new UserData();

    @Test
    void runAllChecks_fixesFindingsByDefault() {
        var check = new FakeCheck(true);
        check.findings.add(new ConsistencyFinding(1L, "thing-1", "broken"));
        newService(check).runAllChecks();

        assertThat(check.fixedIds)
                .as("a check that does not opt out must be auto-fixed by the scheduled run")
                .containsExactly(1L);
    }

    @Test
    void runAllChecks_leavesFindingsAloneWhenACheckOptsOut() {
        var check = new FakeCheck(false);
        check.findings.add(new ConsistencyFinding(1L, "thing-1", "broken"));
        newService(check).runAllChecks();

        assertThat(check.fixedIds)
                .as("a check that opts out of autoFixOnSchedule must not be touched by the scheduled run")
                .isEmpty();
        verify(logs, never()).logAction(any(), any());
    }

    /**
     * Every fix - scheduled or manual - is attributed to the dedicated system user and recorded via the
     * normal admin log, rather than a bespoke history table.
     */
    @Test
    void runAllChecks_logsTheFixUnderTheSystemUser() {
        var check = new FakeCheck(true);
        check.findings.add(new ConsistencyFinding(1L, "thing-1", "broken"));
        when(repositories.findUserByLoginName("system", "ConsistencyCheckUser")).thenReturn(systemUser);

        newService(check).runAllChecks();

        var resultCaptor = ArgumentCaptor.forClass(ResultJson.class);
        verify(logs).logAction(eq(systemUser), resultCaptor.capture());
        assertThat(resultCaptor.getValue().getSuccess())
                .as("the log message must mention the check and how many findings it auto-fixed")
                .contains("fake-check")
                .contains("1");
    }

    @Test
    void fixAll_logsUnderTheSystemUserToo() {
        var check = new FakeCheck(true);
        check.findings.add(new ConsistencyFinding(1L, "thing-1", "broken"));
        when(repositories.findUserByLoginName("system", "ConsistencyCheckUser")).thenReturn(systemUser);

        var fixed = newService(check).fixAll("fake-check");

        assertThat(fixed).isEqualTo(1);
        verify(logs).logAction(eq(systemUser), any(ResultJson.class));
    }

    @Test
    void fixAll_createsTheSystemUserWhenItDoesNotExistYet() {
        var check = new FakeCheck(true);
        check.findings.add(new ConsistencyFinding(1L, "thing-1", "broken"));
        when(repositories.findUserByLoginName("system", "ConsistencyCheckUser")).thenReturn(null);

        newService(check).fixAll("fake-check");

        var userCaptor = ArgumentCaptor.forClass(UserData.class);
        verify(entityManager).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().getProvider()).isEqualTo("system");
        assertThat(userCaptor.getValue().getLoginName()).isEqualTo("ConsistencyCheckUser");
    }

    private ConsistencyCheckService newService(FakeCheck check) {
        return new ConsistencyCheckService(List.of(check), entityManager, repositories, logs);
    }

    private static class FakeCheck implements ConsistencyCheck {

        private final boolean autoFix;
        private final List<ConsistencyFinding> findings = new ArrayList<>();
        private final List<Long> fixedIds = new ArrayList<>();

        FakeCheck(boolean autoFix) {
            this.autoFix = autoFix;
        }

        @Override
        public String getId() {
            return "fake-check";
        }

        @Override
        public String getName() {
            return "Fake check";
        }

        @Override
        public String getDescription() {
            return "A fake check for testing ConsistencyCheckService in isolation.";
        }

        @Override
        public List<ConsistencyFinding> check() {
            return List.copyOf(findings);
        }

        @Override
        public void fix(long entityId) {
            fixedIds.add(entityId);
            findings.removeIf(f -> f.entityId() == entityId);
        }

        @Override
        public boolean autoFixOnSchedule() {
            return autoFix;
        }
    }
}
