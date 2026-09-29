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
package org.eclipse.openvsx.scanning;

import java.time.Duration;
import java.time.ZoneOffset;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.migration.HandlerJobRequest;

@Component
public class ScheduleScanningJobs {
    private final Logger logger = LoggerFactory.getLogger(ScheduleScanningJobs.class);

    private static final String SCHEDULE_SCAN_WATCHDOG_JOB = "scan-job-watchdog";
    private static final String SCHEDULE_SCAN_COMPLETION_JOB = "process-completed-scans";
    private static final String SCHEDULE_SCAN_CONCURRENCY_DISPATCHER_JOB = "scanner-concurrency-dispatcher";
    private static final String SCHEDULE_GITLEAKS_RULES_REFRESH_JOB = "refresh-gitleaks-rules";

    private final ExtensionScanConfig scanConfig;
    private final SecretDetectorConfig secretDetectorConfig;
    private final RemoteScannerProperties remoteScannerProperties;
    private final JobRequestScheduler scheduler;

    public ScheduleScanningJobs(
            ExtensionScanService scanService,
            ExtensionScanConfig scanConfig,
            SecretDetectorConfig secretDetectorConfig,
            RemoteScannerProperties remoteScannerProperties,
            JobRequestScheduler scheduler
    ) {
        this.scanConfig = scanConfig;
        this.secretDetectorConfig = secretDetectorConfig;
        this.remoteScannerProperties = remoteScannerProperties;
        this.scheduler = scheduler;
    }

    @EventListener
    public void scheduleJobs(ApplicationStartedEvent event) {
        var enabledScanners = remoteScannerProperties
                .getScanners()
                .values()
                .stream()
                .filter(RemoteScannerProperties.ScannerConfig::isEnabled).toList();

        // schedule the scan recovery watchdog when there are remote scanners enabled
        if (scanConfig.isEnabled() && !enabledScanners.isEmpty()) {
            var interval = Duration.parse("PT10M");
            logger.info("Scheduling scan recovery job with interval '{}'", interval);

            scheduler.scheduleRecurrently(
                    SCHEDULE_SCAN_WATCHDOG_JOB,
                    interval,
                    new HandlerJobRequest<>(ExtensionScanJobRecoveryService.class));
        } else {
            scheduler.deleteRecurringJob(SCHEDULE_SCAN_WATCHDOG_JOB);
        }

        // schedule the scan completion job when remote scanners are enabled
        if (scanConfig.isEnabled() && !enabledScanners.isEmpty()) {
            var interval = Duration.parse("PT5M");
            logger.info("Scheduling scan completion job with interval '{}'", interval);

            scheduler.scheduleRecurrently(
                    SCHEDULE_SCAN_COMPLETION_JOB,
                    interval,
                    new HandlerJobRequest<>(ExtensionScanCompletionService.class));
        } else {
            scheduler.deleteRecurringJob(SCHEDULE_SCAN_COMPLETION_JOB);
        }

        var enabledScannersWithConcurrency = enabledScanners.stream().anyMatch(c -> c.getMaxConcurrency() > 0);

        // schedule scan concurrency dispatcher if there are enabled scanners with a maxConcurrency setting > 0
        if (scanConfig.isEnabled() && enabledScannersWithConcurrency) {
            var interval = Duration.parse("PT15S");
            logger.info("Scheduling scan concurrency dispatcher job with interval '{}'", interval);

            scheduler.scheduleRecurrently(
                    SCHEDULE_SCAN_CONCURRENCY_DISPATCHER_JOB,
                    interval,
                    new HandlerJobRequest<>(ScannerConcurrencyDispatcher.class));
        } else {
            scheduler.deleteRecurringJob(SCHEDULE_SCAN_CONCURRENCY_DISPATCHER_JOB);
        }

        // schedule the GitLeaks rules refresh job if enabled
        if (scanConfig.isEnabled() && secretDetectorConfig.isEnabled()
                && secretDetectorConfig.isGitleaksScheduledRefresh()) {
            var schedule = secretDetectorConfig.getGitleaksRefreshCron();
            logger.info("Scheduling GitLeaks rules refresh job with schedule '{}'", schedule);

            scheduler.scheduleRecurrently(
                    SCHEDULE_GITLEAKS_RULES_REFRESH_JOB,
                    schedule,
                    ZoneOffset.UTC,
                    new HandlerJobRequest<>(GitleaksRulesService.class));
        } else {
            scheduler.deleteRecurringJob(SCHEDULE_GITLEAKS_RULES_REFRESH_JOB);
        }
    }
}
