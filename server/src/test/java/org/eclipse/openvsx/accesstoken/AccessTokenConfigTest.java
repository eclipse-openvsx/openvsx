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
package org.eclipse.openvsx.accesstoken;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers how the pepper keyring is bound and derived. The properties are the operator's interface to
 * pepper rotation, and getting one of them wrong invalidates every access token in the registry, so the
 * binding is worth asserting rather than assuming - not least because the previous-peppers list is a
 * comma separated {@code @Value}, whose behaviour for an absent property is not obvious.
 */
@ExtendWith(OutputCaptureExtension.class)
class AccessTokenConfigTest {

    // A bare runner has no conversion service, where a real Boot context does; without it neither the
    // Duration properties nor the comma separated pepper list can bind at all.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(
                    context -> context.getBeanFactory()
                            .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(AccessTokenConfig.class);

    @Test
    void hasNoKeyringByDefault() {
        contextRunner.run(context -> {
            var config = context.getBean(AccessTokenConfig.class);
            assertThat(config.getTokenHashPepper()).isEmpty();
            assertThat(config.getTokenHashPepperKeyring()).isEmpty();
        });
    }

    @Test
    void bindsPreviousPeppersInTheConfiguredOrder() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=older,old")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("older", "old"));
    }

    // Adopting a pepper where there was none: the previous pepper is the empty string, which a comma
    // separated list cannot express, hence the separate flag.
    @Test
    void addsTheUnpepperedHashToTheKeyringWhenAccepted() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-accept-unpeppered=true")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly(""));
    }

    @Test
    void putsTheUnpepperedHashAfterTheConfiguredPeppers() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old",
                        "ovsx.access-token.token-hash-accept-unpeppered=true")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("old", ""));
    }

    // The current pepper is always tried first, so listing it again would only add a wasted query per
    // authentication. An operator who forgets to drop it after a rotation should not pay for that.
    @Test
    void dropsTheCurrentPepperFromTheKeyring() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old,current")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("old"));
    }

    // Same reasoning for an accept-unpeppered flag left set on an instance that never adopted a pepper:
    // the empty current pepper already covers it.
    @Test
    void dropsTheUnpepperedHashWhenThereIsNoCurrentPepper() {
        contextRunner
                .withPropertyValues("ovsx.access-token.token-hash-accept-unpeppered=true")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .isEmpty());
    }

    @Test
    void deduplicatesRepeatedPeppers() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old,older,old")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("old", "older"));
    }

    // A blank element is what a trailing or doubled comma leaves behind, and a blank pepper *is* the
    // unpeppered hash - which is exactly what token-hash-accept-unpeppered exists to gate. Honouring one
    // would let a stray comma turn on acceptance of unpeppered tokens without anyone asking for it.
    @Test
    void ignoresBlankEntriesInThePreviousPepperList() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old,")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("old"));
    }

    @Test
    void ignoresWhitespaceOnlyEntriesInThePreviousPepperList() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old, ,older")
                .run(
                        context -> assertThat(context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring())
                                .containsExactly("old", "older"));
    }

    // Caught at startup rather than at the rotation that would silently split it into two wrong peppers
    // - by which point the tokens hashed with it are unreachable and the mistake looks like data loss.
    @Test
    void refusesACurrentPepperContainingAComma() {
        contextRunner
                .withPropertyValues("ovsx.access-token.token-hash-pepper=one,two")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("must not contain a comma"));
    }

    @Test
    void keepsTheKeyringImmutable() {
        contextRunner
                .withPropertyValues(
                        "ovsx.access-token.token-hash-pepper=current",
                        "ovsx.access-token.token-hash-previous-peppers=old")
                .run(context -> {
                    List<String> keyring = context.getBean(AccessTokenConfig.class).getTokenHashPepperKeyring();
                    assertThat(keyring).isUnmodifiable();
                });
    }

    // The empty pepper is the default and no shipped configuration sets one, so without this the
    // recommendation exists only in a javadoc that nobody deploying the server reads.
    @Test
    void warnsWhenNoPepperIsConfigured(CapturedOutput output) {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output)
                    .contains("No ovsx.access-token.token-hash-pepper is configured")
                    .contains("token-hash-accept-unpeppered");
        });
    }

    @Test
    void staysQuietWhenAPepperIsConfigured(CapturedOutput output) {
        contextRunner
                .withPropertyValues("ovsx.access-token.token-hash-pepper=current")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(output).doesNotContain("token-hash-pepper is configured");
                });
    }
}
