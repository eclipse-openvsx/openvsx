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

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class PublisherAgreementConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RepositoryService.class, () -> Mockito.mock(RepositoryService.class))
            .withUserConfiguration(PublisherAgreementConfig.class);

    @Test
    void simpleProviderIsTheDefault() {
        runner.run(
                context -> assertThat(context.getBean(PublisherAgreementProvider.class))
                        .isInstanceOf(SimplePublisherAgreementProvider.class));
    }

    @Test
    void invalidVersionFailsStartup() {
        runner.withPropertyValues("ovsx.publisher-agreement.version=v1.0").run(
                context -> assertThat(context).hasFailed());
    }

    @Test
    void shortVersionEnablesAgreements() {
        for (var version : new String[] { "1", "1.1" }) {
            runner.withPropertyValues("ovsx.publisher-agreement.version=" + version).run(
                    context -> assertThat(context).as(version).hasNotFailed());
        }
    }

    @Test
    void semanticVersionEnablesAgreements() {
        runner.withPropertyValues("ovsx.publisher-agreement.version=1.0.0").run(
                context -> assertThat(context).hasNotFailed());
    }

    @Test
    void primaryDeploymentProviderReplacesTheDefault() {
        runner.withUserConfiguration(DeploymentConfig.class).run(context -> {
            assertThat(context.getBeansOfType(PublisherAgreementProvider.class)).hasSize(2);
            assertThat(context.getBean(PublisherAgreementProvider.class))
                    .isSameAs(context.getBean(DeploymentConfig.class).custom);
        });
    }

    /** Signed 2.0 while the agreement is 1.1: only the list can make that outdated, the major rule would not. */
    @Test
    void theListReachesTheVersionsHoweverItIsWritten() {
        for (var list : new String[][] {
            { "ovsx.publisher-agreement.allowed-versions=1.1, 1.2" },
            { "ovsx.publisher-agreement.allowed-versions[0]=1.1", "ovsx.publisher-agreement.allowed-versions[1]=1.2" }
        }) {
            runner.withPropertyValues("ovsx.publisher-agreement.version=1.1").withPropertyValues(list).run(
                    context -> {
                        var versions = context.getBean(PublisherAgreementVersions.class);
                        assertThat(versions.accepts("1.2")).as(list[0]).isTrue();
                        assertThat(versions.accepts("2.0")).as(list[0]).isFalse();
                    });
        }
    }

    @Test
    void theListIsReadFromAnEnvironmentVariable() {
        var environment = new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                        "OVSX_PUBLISHERAGREEMENT_VERSION",
                        "1.1",
                        "OVSX_PUBLISHERAGREEMENT_ALLOWEDVERSIONS",
                        "1.1,1.2"));

        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(environment)).run(
                context -> {
                    var versions = context.getBean(PublisherAgreementVersions.class);
                    assertThat(versions.accepts("1.2")).isTrue();
                    assertThat(versions.accepts("2.0")).isFalse();
                });
    }

    @Test
    void blankEntriesInTheListAreIgnored() {
        for (var list : new String[][] {
            { "ovsx.publisher-agreement.allowed-versions=1.1,1.2," },
            { "ovsx.publisher-agreement.allowed-versions=1.1,,1.2" },
            { "ovsx.publisher-agreement.allowed-versions[0]=1.2", "ovsx.publisher-agreement.allowed-versions[1]=" }
        }) {
            runner.withPropertyValues("ovsx.publisher-agreement.version=1.2").withPropertyValues(list).run(
                    context -> {
                        assertThat(context).as(list[0]).hasNotFailed();
                        assertThat(context.getBean(PublisherAgreementVersions.class).accepts("1.2")).isTrue();
                    });
        }
    }

    @Test
    void withoutAListTheSameMajorStaysValid() {
        runner.withPropertyValues("ovsx.publisher-agreement.version=1.1").run(
                context -> assertThat(context.getBean(PublisherAgreementVersions.class).accepts("1.7")).isTrue());
    }

    @Test
    void anInvalidAllowedVersionFailsStartupNamingTheProperty() {
        runner.withPropertyValues(
                "ovsx.publisher-agreement.version=1",
                "ovsx.publisher-agreement.allowed-versions=1,x").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("ovsx.publisher-agreement.allowed-versions");
                });
    }

    @Test
    void anOverlongVersionFailsStartupNamingTheProperty() {
        runner.withPropertyValues("ovsx.publisher-agreement.version=99999999999999999999").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("ovsx.publisher-agreement.version");
        });
    }

    @Test
    void warnsWhenAgreementsAreEnabledWithoutAText(CapturedOutput output) {
        runner.withPropertyValues("ovsx.publisher-agreement.version=1").run(
                context -> assertThat(output).contains("without ovsx.publisher-agreement.text"));
    }

    @Test
    void doesNotWarnAboutTheTextWhenOneIsSetOrAgreementsAreDisabled(CapturedOutput output) {
        runner.withPropertyValues(
                "ovsx.publisher-agreement.version=1",
                "ovsx.publisher-agreement.text=classpath:agreement.md").run(context -> {
                });
        runner.run(context -> {
        });

        assertThat(output).doesNotContain("without ovsx.publisher-agreement.text");
    }

    @Configuration(proxyBeanMethods = false)
    static class DeploymentConfig {
        final PublisherAgreementProvider custom = Mockito.mock(PublisherAgreementProvider.class);

        @Bean
        @Primary
        PublisherAgreementProvider custom() {
            return custom;
        }
    }
}
