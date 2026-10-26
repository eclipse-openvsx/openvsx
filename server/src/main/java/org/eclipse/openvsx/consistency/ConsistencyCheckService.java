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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.NotFoundException;

/**
 * Orchestrates every registered {@link ConsistencyCheck}: overview for the admin dashboard, on-demand
 * fixes, and the scheduled sweep's auto-fixing. Adding a new kind of check only requires a new
 * {@code @Component implements ConsistencyCheck} bean - it is picked up here automatically and needs no
 * further wiring.
 * <p>
 * Sweeps and "fix all" are deliberately not one transaction: each check's {@code check()}/{@code fix()}
 * commits on its own, so no connection or row lock is held for the length of the whole run.
 * <p>
 * There is no persisted run history: findings are always computed live, and every fix - whether an
 * admin clicked "Fix"/"Fix all" or the scheduled sweep did it unattended - is recorded as a normal admin
 * log entry instead, attributed to a dedicated system user (the same pattern
 * {@code ExtensionControlService}/{@code FixTargetPlatformsService} use), visible on the existing Admin
 * Logs page rather than a bespoke report.
 */
@Service
public class ConsistencyCheckService {

    private static final Logger logger = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private static final String SYSTEM_USER_LOGIN_NAME = "ConsistencyCheckUser";

    private final Map<String, ConsistencyCheck> checksById;
    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final LogService logs;
    private final TransactionTemplate transactions;

    public ConsistencyCheckService(
            List<ConsistencyCheck> checks,
            EntityManager entityManager,
            RepositoryService repositories,
            LogService logs,
            TransactionTemplate transactions
    ) {
        this.checksById = checks.stream().collect(Collectors.toMap(ConsistencyCheck::getId, c -> c));
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.logs = logs;
        this.transactions = transactions;
    }

    /**
     * One summary per registered check, with its live findings count.
     */
    public List<ConsistencyCheckSummary> listSummaries() {
        return checksById.values().stream()
                .map(
                        check -> new ConsistencyCheckSummary(
                                check.getId(),
                                check.getName(),
                                check.getDescription(),
                                check.check().size()))
                .toList();
    }

    /**
     * The live findings for one check.
     */
    public List<ConsistencyFinding> findings(String checkId) {
        return getCheck(checkId).check();
    }

    /**
     * Fixes every entity the check currently finds inconsistent, logging the outcome.
     */
    public int fixAll(String checkId) {
        var check = getCheck(checkId);
        return fixAll(check, check.check(), "Fixed");
    }

    /**
     * Fixes a single finding, logging the outcome.
     */
    @Transactional
    public void fixOne(String checkId, long entityId) {
        var check = getCheck(checkId);
        check.fix(entityId);
        log("Fixed entity " + entityId + " for consistency check '" + check.getId() + "'.");
    }

    /**
     * Runs every registered check and auto-fixes what it found, unless the check opted out via
     * {@link ConsistencyCheck#autoFixOnSchedule()} - in which case its findings are only logged as a
     * warning for a human to act on from the dashboard. Used by the recurring background job; the admin
     * dashboard's "Fix all" already covers triggering a fix on demand for a single check, so there is no
     * separate "run all checks now" admin action.
     */
    public void runAllChecks() {
        for (var check : checksById.values()) {
            var findings = check.check();
            var count = findings.size();
            if (count == 0) {
                continue;
            }

            logger.atWarn()
                    .setMessage("Consistency check '{}' found {} inconsistent entit{}")
                    .addArgument(check.getId())
                    .addArgument(count)
                    .addArgument(count == 1 ? "y" : "ies")
                    .log();

            if (check.autoFixOnSchedule()) {
                fixAll(check, findings, "Auto-fixed");
            }
        }
    }

    /**
     * Fixes the given findings, then recomputes them rather than trusting a stale list, in case fixing one
     * finding incidentally resolves another or introduces a new one. A finding that survives its own fix
     * is not retried, so a fix that cannot resolve it ends the loop. The fixes made are logged even when a
     * later one throws, since each fix is already committed.
     */
    private int fixAll(ConsistencyCheck check, List<ConsistencyFinding> findings, String verb) {
        var attempted = new HashSet<Long>();
        var fixed = 0;
        try {
            while (!findings.isEmpty()) {
                for (var finding : findings) {
                    check.fix(finding.entityId());
                    attempted.add(finding.entityId());
                    fixed++;
                }
                var remaining = check.check();
                findings = remaining.stream().filter(f -> !attempted.contains(f.entityId())).toList();
                if (findings.isEmpty() && !remaining.isEmpty()) {
                    logger.warn(
                            "Consistency check '{}' still finds {} entities inconsistent after fixing them",
                            check.getId(),
                            remaining.size());
                }
            }
        } finally {
            if (fixed > 0) {
                log(verb + " " + fixed + " finding(s) for consistency check '" + check.getId() + "'.");
            }
        }
        return fixed;
    }

    private void log(String message) {
        transactions.executeWithoutResult(status -> logs.logAction(getSystemUser(), ResultJson.success(message)));
    }

    /**
     * The user every consistency-check fix is attributed to in the admin log, regardless of whether an
     * admin clicked a button or the scheduled sweep did it unattended - fixing a detected inconsistency
     * is a mechanical recomputation either way, so who triggered it is less relevant than what happened.
     * Same convention as {@code ExtensionControlService#createExtensionControlUser()} and
     * {@code FixTargetPlatformsService#getUser()}: a dedicated {@code provider = "system"} user, created
     * once and reused afterward.
     */
    private UserData getSystemUser() {
        var user = repositories.findUserByLoginName("system", SYSTEM_USER_LOGIN_NAME);
        if (user == null) {
            user = new UserData();
            user.setProvider("system");
            user.setLoginName(SYSTEM_USER_LOGIN_NAME);
            entityManager.persist(user);
        }
        return user;
    }

    private ConsistencyCheck getCheck(String checkId) {
        var check = checksById.get(checkId);
        if (check == null) {
            throw new NotFoundException();
        }
        return check;
    }
}
