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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.util.Streamable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublisherComplianceCheckerTest {

    private final RepositoryService repositories = mock(RepositoryService.class);
    private final ExtensionService extensions = mock(ExtensionService.class);
    private final PublisherAgreementService agreements = mock(PublisherAgreementService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private PublisherComplianceChecker checker;

    @BeforeEach
    void setup() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        checker = new PublisherComplianceChecker(transactions, repositories, extensions, agreements);
        checker.checkCompliance = true;
    }

    private UserData publisher(String name) {
        var user = new UserData();
        user.setLoginName(name);
        user.setProvider("github");
        return user;
    }

    private ExtensionVersion activeVersionOf(UserData user) {
        var namespace = new Namespace();
        namespace.setName(user.getLoginName() + "-ns");
        var extension = new Extension();
        extension.setName("ext");
        extension.setNamespace(namespace);
        var version = new ExtensionVersion();
        version.setVersion("1.0.0");
        version.setTargetPlatform("universal");
        version.setExtension(extension);
        version.setActive(true);
        when(repositories.findVersionsByUser(user, true)).thenReturn(Streamable.of(version));
        return version;
    }

    @Test
    void doesNothingUnlessEnabled() {
        checker.checkCompliance = false;

        checker.checkPublishers(null);

        verifyNoInteractions(repositories, agreements);
    }

    @Test
    void deactivatesTheVersionsOfAPublisherWithoutACurrentAgreement() {
        var user = publisher("lapsed");
        var version = activeVersionOf(user);
        when(repositories.findPublishersWithActiveVersions()).thenReturn(Streamable.of(user));
        when(agreements.compliance(user)).thenReturn(PublisherCompliance.NON_COMPLIANT);

        checker.checkPublishers(null);

        assertThat(version.isActive()).isFalse();
        verify(repositories).recordExtensionVersionChange(eq(version), eq(ExtensionVersionState.INACTIVE), any());
        verify(extensions).updateExtension(version.getExtension());
    }

    @Test
    void keepsACompliantPublisher() {
        var user = publisher("signed");
        var version = activeVersionOf(user);
        when(repositories.findPublishersWithActiveVersions()).thenReturn(Streamable.of(user));
        when(agreements.compliance(user)).thenReturn(PublisherCompliance.COMPLIANT);

        checker.checkPublishers(null);

        assertThat(version.isActive()).isTrue();
        verify(repositories, never()).findVersionsByUser(user, true);
    }

    /** The provider could not tell, as when its server is down: nobody is deactivated on a guess. */
    @Test
    void leavesAPublisherWhoseStatusIsUnknown() {
        var user = publisher("unknown");
        var version = activeVersionOf(user);
        when(repositories.findPublishersWithActiveVersions()).thenReturn(Streamable.of(user));
        when(agreements.compliance(user)).thenReturn(PublisherCompliance.UNKNOWN);

        checker.checkPublishers(null);

        assertThat(version.isActive()).isTrue();
        verify(repositories, never()).findVersionsByUser(user, true);
    }

    @Test
    void anErrorFromTheServiceDeactivatesNobodyAndDoesNotStopTheCheck() {
        var failing = publisher("failing");
        var failingVersion = activeVersionOf(failing);
        var lapsed = publisher("lapsed");
        var lapsedVersion = activeVersionOf(lapsed);
        when(repositories.findPublishersWithActiveVersions()).thenReturn(Streamable.of(failing, lapsed));
        when(agreements.compliance(failing)).thenThrow(new IllegalStateException("provider down"));
        when(agreements.compliance(lapsed)).thenReturn(PublisherCompliance.NON_COMPLIANT);

        checker.checkPublishers(null);

        assertThat(failingVersion.isActive()).isTrue();
        assertThat(lapsedVersion.isActive()).isFalse();
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TransactionTemplate.class, () -> Mockito.mock(TransactionTemplate.class))
            .withBean(RepositoryService.class, () -> repositories)
            .withBean(ExtensionService.class, () -> extensions)
            .withBean(PublisherAgreementService.class, () -> agreements)
            .withUserConfiguration(PublisherComplianceChecker.class);

    @Test
    void isOffByDefault() {
        runner.run(context -> assertThat(context.getBean(PublisherComplianceChecker.class).checkCompliance).isFalse());
    }

    @Test
    void isSwitchedOnByTheAgreementProperty() {
        runner.withPropertyValues("ovsx.publisher-agreement.check-compliance-on-start=true").run(
                context -> assertThat(context.getBean(PublisherComplianceChecker.class).checkCompliance).isTrue());
    }
}
