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

import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import org.eclipse.openvsx.repositories.RepositoryService;

@Configuration(proxyBeanMethods = false)
public class PublisherAgreementConfig {

    // A deployment's own bean is @Primary; this stays a candidate so injection by type has a fallback.
    @Bean
    PublisherAgreementProvider simplePublisherAgreementProvider(
            RepositoryService repositories,
            @Value("${ovsx.publisher-agreement.version:}") String version,
            @Value("${ovsx.publisher-agreement.text:}") String text
    ) {
        if (!version.isBlank() && text.isBlank()) {
            LoggerFactory.getLogger(PublisherAgreementConfig.class).warn(
                    "ovsx.publisher-agreement.version is set without ovsx.publisher-agreement.text: "
                            + "users have no agreement text to read before signing");
        }
        return new SimplePublisherAgreementProvider(repositories, version);
    }

    /** Which signed versions count, for whichever provider is in use; an invalid version stops the startup. */
    @Bean
    PublisherAgreementVersions publisherAgreementVersions(
            @Value("${ovsx.publisher-agreement.version:}") String version,
            Environment environment
    ) {
        // Bound rather than read as a placeholder, which does not see a YAML list or an indexed key.
        var allowed = Binder.get(environment)
                .bind("ovsx.publisher-agreement.allowed-versions", Bindable.listOf(String.class))
                .orElse(List.of())
                .stream()
                .filter(allowedVersion -> !allowedVersion.isEmpty())
                .toList();
        return new PublisherAgreementVersions(version, allowed);
    }
}
