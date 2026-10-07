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
package org.eclipse.openvsx.util;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNameUtilTest {

    static IntStream spaceSeparators() {
        return DisplayNameUtil.SURROUNDING_WHITESPACE.chars();
    }

    @Test
    void coversEverySpaceSeparator() {
        // Fails when a JDK upgrade brings a newer Unicode version with a new Zs character; add it to
        // the constant and to the index expression in the V1_76 migration.
        var expected = IntStream.rangeClosed(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
                .filter(cp -> Character.getType(cp) == Character.SPACE_SEPARATOR)
                .boxed()
                .collect(Collectors.toSet());
        var actual = DisplayNameUtil.SURROUNDING_WHITESPACE.codePoints()
                .boxed()
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
        assertThat(DisplayNameUtil.SURROUNDING_WHITESPACE.codePoints().count()).isEqualTo(expected.size());
    }

    @ParameterizedTest
    @MethodSource("spaceSeparators")
    void normalizeStripsEverySpaceSeparatorAtEitherEnd(int separator) {
        var space = Character.toString(separator);

        assertThat(DisplayNameUtil.normalize(space + "Pretty Formatter")).isEqualTo("pretty formatter");
        assertThat(DisplayNameUtil.normalize("Pretty Formatter" + space)).isEqualTo("pretty formatter");
        assertThat(DisplayNameUtil.normalize(space + space + "Pretty Formatter" + space)).isEqualTo("pretty formatter");
    }

    @ParameterizedTest
    @MethodSource("spaceSeparators")
    void isBlankForANameOfOnlySpaceSeparators(int separator) {
        var space = Character.toString(separator);

        assertThat(DisplayNameUtil.isBlank(space)).isTrue();
        assertThat(DisplayNameUtil.isBlank(space + " " + space)).isTrue();
    }

    @Test
    void normalizeKeepsInteriorWhitespace() {
        assertThat(DisplayNameUtil.normalize("　Pretty Formatter ")).isEqualTo("pretty formatter");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(DisplayNameUtil.normalize(null)).isEmpty();
        assertThat(DisplayNameUtil.normalize("")).isEmpty();
        assertThat(DisplayNameUtil.isBlank(null)).isTrue();
        assertThat(DisplayNameUtil.isBlank("")).isTrue();
        assertThat(DisplayNameUtil.isBlank("\t\n")).isTrue();
        assertThat(DisplayNameUtil.isBlank(" x ")).isFalse();
    }
}
