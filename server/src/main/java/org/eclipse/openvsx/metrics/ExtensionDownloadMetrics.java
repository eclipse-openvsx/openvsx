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
package org.eclipse.openvsx.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;

/**
 * Service for recording extension download metrics to Prometheus.
 *
 * Metrics recorded:
 * - openvsx_extension_downloads_total: Counter with namespace, extension tags
 * - openvsx_namespace_downloads_total: Counter with namespace tag
 */
@Service
public class ExtensionDownloadMetrics {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionDownloadMetrics.class);

    private static final String EXTENSION_DOWNLOADS_METRIC = "openvsx_extension_downloads_total";
    private static final String NAMESPACE_DOWNLOADS_METRIC = "openvsx_namespace_downloads_total";

    private final MeterRegistry meterRegistry;

    public ExtensionDownloadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a download for the given file resource.
     * Only records metrics for DOWNLOAD type resources (VSIX files).
     */
    public void recordDownload(FileResource resource) {
        if (resource == null) {
            logger.debug("Skipping metrics: null resource");
            return;
        }

        // Only track actual extension downloads (VSIX files)
        if (!FileResource.DOWNLOAD.equals(resource.getType())) {
            logger.debug("Skipping metrics: resource type is {}, not DOWNLOAD", resource.getType());
            return;
        }

        ExtensionVersion extVersion = resource.getExtension();
        if (extVersion == null) {
            logger.warn("Skipping metrics: resource has no extension version");
            return;
        }

        Extension extension = extVersion.getExtension();
        if (extension == null || extension.getNamespace() == null) {
            logger.warn("Skipping metrics: extension or namespace is null");
            return;
        }

        String namespace = extension.getNamespace().getName();
        String extensionName = extension.getName();

        // Record extension-level download
        recordExtensionDownload(namespace, extensionName);

        // Record namespace-level download for efficient namespace aggregation
        recordNamespaceDownload(namespace);

        logger.debug("Recorded download metrics for {}.{}", namespace, extensionName);
    }

    /**
     * Records extension-level download metric.
     * Tags: namespace, extension
     */
    private void recordExtensionDownload(String namespace, String extensionName) {
        try {
            Counter.builder(EXTENSION_DOWNLOADS_METRIC)
                    .description("Total extension downloads by namespace and extension")
                    .tags(
                            Tags.of(
                                    "namespace",
                                    sanitizeLabel(namespace),
                                    "extension",
                                    sanitizeLabel(extensionName)))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            logger.error("Failed to record extension download metric: {}", e.getMessage());
        }
    }

    /**
     * Records namespace-level download metric.
     * Separate metric for efficient namespace-only queries.
     */
    private void recordNamespaceDownload(String namespace) {
        try {
            Counter.builder(NAMESPACE_DOWNLOADS_METRIC)
                    .description("Total downloads by namespace")
                    .tag("namespace", sanitizeLabel(namespace))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            logger.error("Failed to record namespace download metric: {}", e.getMessage());
        }
    }

    /**
     * Sanitizes label values for Prometheus compatibility.
     * Prometheus labels should not contain special characters.
     */
    private String sanitizeLabel(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        // Replace any problematic characters with underscores
        // Keep alphanumeric, dash, underscore, and dot
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
