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

import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Registers remote scanners from application configuration at startup.
 * <p>
 * Scanner configuration changes require server restart to take effect.
 */
@Component
@EnableConfigurationProperties(RemoteScannerProperties.class)
public class RemoteScannerRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(RemoteScannerRegistrar.class);

    private final RemoteScannerProperties properties;
    private final ScannerRegistry registry;
    private final HttpTemplateEngine templateEngine;
    private final HttpResponseExtractor responseExtractor;
    private final ScannerFileProvider scanFileService;

    public RemoteScannerRegistrar(
            RemoteScannerProperties properties,
            ScannerRegistry registry,
            HttpTemplateEngine templateEngine,
            HttpResponseExtractor responseExtractor,
            ScannerFileProvider scanFileService
    ) {
        this.properties = properties;
        this.registry = registry;
        this.templateEngine = templateEngine;
        this.responseExtractor = responseExtractor;
        this.scanFileService = scanFileService;
    }

    /**
     * Register scanners from configuration at startup.
     */
    @PostConstruct
    public void registerScanners() {
        Map<String, RemoteScannerProperties.ScannerConfig> configuredScanners = properties.getScanners();

        if (configuredScanners == null || configuredScanners.isEmpty()) {
            logger.info("No remote scanners configured");
            return;
        }

        int registered = 0;
        for (Map.Entry<String, RemoteScannerProperties.ScannerConfig> entry : configuredScanners.entrySet()) {
            String scannerName = entry.getKey();
            RemoteScannerProperties.ScannerConfig config = entry.getValue();

            if (config.isEnabled() && isValidConfig(scannerName, config)) {
                registerScanner(scannerName, config);
                registered++;
            }
        }

        logger.info("Registered {} remote scanner(s)", registered);
    }

    /**
     * Validate scanner configuration has required fields.
     */
    private boolean isValidConfig(String scannerName, RemoteScannerProperties.ScannerConfig config) {
        if (config.getType() == null || config.getType().trim().isEmpty()) {
            logger.warn("Scanner {} has no type configured, skipping", scannerName);
            return false;
        }
        if (config.getStart() == null) {
            logger.warn("Scanner {} has no start operation configured, skipping", scannerName);
            return false;
        }
        return true;
    }

    /**
     * Register a single scanner with the registry.
     */
    private void registerScanner(String scannerName, RemoteScannerProperties.ScannerConfig config) {
        try {
            HttpClientExecutor httpExecutor = HttpClientExecutor.create(
                    config.getHttp(),
                    config.getAuth(),
                    scannerName);

            RemoteScanner scanner = new RemoteScanner(
                    scannerName,
                    config,
                    templateEngine,
                    httpExecutor,
                    responseExtractor,
                    scanFileService);

            registry.registerScanner(scanner);

            logger.info(
                    "Registered remote scanner: {} (type: {}, async: {}, required: {}, enforced: {})",
                    scannerName,
                    config.getType(),
                    config.isAsync(),
                    config.isRequired(),
                    config.isEnforced());

        } catch (Exception e) {
            logger.error("Failed to register scanner {}: {}", scannerName, e.getMessage(), e);
        }
    }
}
