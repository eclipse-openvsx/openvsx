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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublisherAgreementVersionsTest {

    private static PublisherAgreementVersions versions(String version, String... allowedVersions) {
        return new PublisherAgreementVersions(version, List.of(allowedVersions));
    }

    @Test
    void invalidVersionIsRejected() {
        for (var version : new String[] { "v1.0.0", "1.", ".1", "1..0", "1.2.3.4", "x", "-1", "01", "1.00" }) {
            assertThatThrownBy(() -> versions(version)).as(version).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aVersionTooLargeToReadIsRejectedNamingTheProperty() {
        assertThatThrownBy(() -> versions("99999999999999999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ovsx.publisher-agreement.version");
    }

    @Test
    void aVersionNeedsNotSpellOutAllThreeNumbers() {
        for (var version : new String[] { "1", "1.0", "1.1", "1.0.0", "1.2.3", "1.0.0-beta", " 2 " }) {
            versions(version);
        }
    }

    @Test
    void aSignatureIsComparedByMajorWhateverTheFormOfTheVersion() {
        for (var current : new String[] { "1", "1.0", "1.1", "1.0.0", "1.4.2" }) {
            assertThat(versions(current).accepts("1")).as(current).isTrue();
        }
        for (var current : new String[] { "2", "2.0", "2.1.0" }) {
            assertThat(versions(current).accepts("1")).as(current).isFalse();
        }
    }

    @Test
    void aLowerConfiguredMajorStillCountsTheHigherSignature() {
        assertThat(versions("1.5").accepts("2.1")).isTrue();
        assertThat(versions("3").accepts("2.1")).isFalse();
    }

    @Test
    void aSignatureOfAnAllowedVersionStaysValidAcrossAMinorChange() {
        assertThat(versions("1.2", "1.1", "1.2").accepts("1.1")).isTrue();
    }

    @Test
    void aSignatureOfAVersionThatIsNotAllowedIsOutdatedEvenWithinTheSameMajor() {
        assertThat(versions("1.2", "1.2").accepts("1.1")).isFalse();
    }

    @Test
    void aSignatureOfAHigherMajorIsOutdatedWhenItIsNotAllowed() {
        assertThat(versions("2", "1", "2").accepts("3")).isFalse();
    }

    @Test
    void theConfiguredVersionIsAlwaysAllowed() {
        assertThat(versions("1.2", "1.0").accepts("1.2")).isTrue();
    }

    @Test
    void allowedVersionsMatchWhateverTheSpellingOfTheVersion() {
        assertThat(versions("2", "1.0.0").accepts("1")).isTrue();
        assertThat(versions("2", "1.0").accepts("1")).isTrue();
        assertThat(versions("2", "1.0.1").accepts("1")).isFalse();
    }

    @Test
    void withoutAllowedVersionsTheSameMajorStaysValid() {
        assertThat(versions("1.9").accepts("1.1")).isTrue();
    }

    @Test
    void buildMetadataDoesNotMakeAVersionAnotherOne() {
        assertThat(versions("2", "1.0.0").accepts("1.0.0+build5")).isTrue();
        assertThat(versions("2", "1.0.0+other").accepts("1.0.0")).isTrue();
    }

    /** One written by something else is not in the list and not of a major, so it is outdated. */
    @Test
    void aSignatureWhoseVersionCannotBeReadIsNotAccepted() {
        assertThat(versions("1").accepts("v1")).isFalse();
        assertThat(versions("1", "1.0").accepts("v1")).isFalse();
    }

    @Test
    void everythingIsAcceptedWhileThereIsNoCurrentVersion() {
        assertThat(versions("", "1.1").accepts("v1")).isTrue();
        assertThat(versions(null).accepts("7")).isTrue();
    }

    @Test
    void allowedVersionsAreTrimmed() {
        assertThat(versions("2", " 1.1 ").accepts("1.1")).isTrue();
    }

    @Test
    void anInvalidAllowedVersionIsRejectedNamingTheProperty() {
        for (var allowed : new String[] { "v1", "1.", "99999999999999999999" }) {
            assertThatThrownBy(() -> versions("1", allowed))
                    .as(allowed)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ovsx.publisher-agreement.allowed-versions");
        }
    }
}
