/********************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SemanticVersionTest {

    @Test
    void testParseFullVersion() {
        var semver = SemanticVersion.parse("1.2.31-rc1+armhf");
        assertEquals(1, semver.getMajor());
        assertEquals(2, semver.getMinor());
        assertEquals(31, semver.getPatch());
        assertEquals("rc1", semver.getPreRelease());
        assertEquals("armhf", semver.getBuildMetadata());
    }

    @Test
    void testParseNumbersVersion() {
        var semver = SemanticVersion.parse("1.2.3");
        assertEquals(1, semver.getMajor());
        assertEquals(2, semver.getMinor());
        assertEquals(3, semver.getPatch());
        assertNull(semver.getPreRelease());
        assertNull(semver.getBuildMetadata());
    }

    @Test
    void testParseInvalidVersion() {
        var exception = assertThrows(IllegalStateException.class, () -> SemanticVersion.parse("9.2"));
        assertEquals("No match found", exception.getMessage());
    }

    @Test
    void testCompare() {
        assertThat(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.2.3")))
                .isEqualTo(-1);
        assertThat(SemanticVersion.parse("1.2.3").compareTo(SemanticVersion.parse("1.2.4")))
                .isEqualTo(1);
        assertThat(SemanticVersion.parse("1.2.3-next.bc11e2c5").compareTo(SemanticVersion.parse("1.2.3-next.6aa3b0d6")))
                .isZero();
        assertThat(SemanticVersion.parse("1.2.3").compareTo(SemanticVersion.parse("1.2.3-next.bc11e2c5")))
                .isEqualTo(-1);
    }
}
