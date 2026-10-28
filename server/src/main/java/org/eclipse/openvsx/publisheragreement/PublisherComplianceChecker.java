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
package org.eclipse.openvsx.publisheragreement;

import java.util.LinkedHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * On startup, deactivates the extension versions of every publisher whose agreement service reports them
 * as {@link PublisherCompliance#NON_COMPLIANT}. A publisher whose compliance is unknown, or cannot be
 * determined because the service fails, is left alone.
 */
@Component
public class PublisherComplianceChecker {

    protected final Logger logger = LoggerFactory.getLogger(PublisherComplianceChecker.class);

    private final TransactionTemplate transactions;
    private final RepositoryService repositories;
    private final ExtensionService extensions;
    private final PublisherAgreementService publisherAgreements;

    @Value("${ovsx.publisher-agreement.check-compliance-on-start:false}")
    boolean checkCompliance;

    public PublisherComplianceChecker(
            TransactionTemplate transactions,
            RepositoryService repositories,
            ExtensionService extensions,
            PublisherAgreementService publisherAgreements
    ) {
        this.transactions = transactions;
        this.repositories = repositories;
        this.extensions = extensions;
        this.publisherAgreements = publisherAgreements;
    }

    @EventListener
    public void checkPublishers(ApplicationStartedEvent event) {
        if (!checkCompliance) {
            return;
        }

        repositories.findPublishersWithActiveVersions().forEach(user -> {
            if (complianceOf(user) == PublisherCompliance.NON_COMPLIANT) {
                // Found a non-compliant publisher: deactivate all extension versions
                transactions.<Void>execute(status -> {
                    deactivateExtensions(user);
                    return null;
                });
            }
        });
    }

    private PublisherCompliance complianceOf(UserData user) {
        try {
            var compliance = publisherAgreements.compliance(user);
            if (compliance == PublisherCompliance.UNKNOWN) {
                logger.warn(
                        "The publisher agreement of {} could not be determined, keeping their extensions",
                        user.getLoginName());
            }
            return compliance;
        } catch (RuntimeException e) {
            logger.warn(
                    "The publisher agreement of {} could not be checked, keeping their extensions",
                    user.getLoginName(),
                    e);
            return PublisherCompliance.UNKNOWN;
        }
    }

    private void deactivateExtensions(UserData user) {
        var affectedExtensions = new LinkedHashSet<Extension>();
        var now = TimeUtil.getCurrentUTC();
        var versions = repositories.findVersionsByUser(user, true);
        for (var version : versions) {
            version.setActive(false);
            // the version stops being publicly visible here, which the changes feed reports at
            // this instant rather than at the one it was published at
            repositories.recordExtensionVersionChange(version, ExtensionVersionState.INACTIVE, now);
            var extension = version.getExtension();
            affectedExtensions.add(extension);
            logger.atInfo()
                    .setMessage("Deactivated: {} - {}")
                    .addArgument(user::getLoginName)
                    .addArgument(() -> NamingUtil.toLogFormat(version))
                    .log();
        }

        // Update affected extensions
        for (var extension : affectedExtensions) {
            // Managed already, as findVersionsByUser runs inside the transaction; the setters persist.
            extensions.updateExtension(extension);
        }
    }
}
