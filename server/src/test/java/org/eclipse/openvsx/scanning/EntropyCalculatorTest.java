/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EntropyCalculator} Shannon entropy calculation.
 */
class EntropyCalculatorTest {

    private final EntropyCalculator calculator = new EntropyCalculator();

    @Test
    void calculate_returnsZeroForNull() {
        assertEquals(0.0, calculator.calculate(null));
    }

    @Test
    void calculate_returnsZeroForEmptyString() {
        assertEquals(0.0, calculator.calculate(""));
    }

    @Test
    void calculate_returnsZeroForSingleCharRepeat() {
        // All same characters = zero entropy (no randomness)
        assertEquals(0.0, calculator.calculate("aaaa"));
    }

    @Test
    void calculate_lowEntropyForRepetitive() {
        // Repetitive patterns have low entropy
        double entropy = calculator.calculate("abababab");
        assertTrue(entropy < 1.5, "Repetitive pattern should have low entropy");
    }

    @Test
    void calculate_highEntropyForRandom() {
        // Random-looking strings have high entropy (like secrets)
        double entropy = calculator.calculate("aB3$xY9!mK2@");
        assertTrue(entropy > 3.0, "Random string should have high entropy");
    }

    @Test
    void calculate_typicalSecretHasHighEntropy() {
        // API keys and tokens typically have entropy > 3.5
        double entropy = calculator.calculate("sk_live_51H7xs2CXyz123abcDEF");
        assertTrue(entropy > 3.5, "Typical secret should have high entropy");
    }

    @Test
    void calculate_englishWordHasModerateEntropy() {
        // English words have moderate entropy
        double entropy = calculator.calculate("password");
        assertTrue(
                entropy > 2.0 && entropy < 4.0,
                "English word should have moderate entropy");
    }

    @Test
    void calculate_maxEntropyForUniqueChars() {
        // Maximum entropy when all characters are unique
        double entropy = calculator.calculate("abcd");
        assertEquals(2.0, entropy, 0.001); // log2(4) = 2.0
    }

    @Test
    void calculate_consistentResults() {
        // Same input should always produce same output
        String input = "test123ABC";
        double first = calculator.calculate(input);
        double second = calculator.calculate(input);
        assertEquals(first, second);
    }

    @Test
    void calculate_handlesUnicodeCharacters() {
        // Should handle unicode without throwing
        double entropy = calculator.calculate("hello世界");
        assertTrue(entropy > 0);
    }
}
