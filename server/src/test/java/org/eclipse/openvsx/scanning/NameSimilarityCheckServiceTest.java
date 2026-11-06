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
package org.eclipse.openvsx.scanning;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.NameSimilarityAllow;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.NameSimilarityAllowRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Probe;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TempFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NameSimilarityCheckServiceTest {

    private final NameSimilarityCheckConfig config = mock(NameSimilarityCheckConfig.class);
    private final NameSimilarityJooqRepository similarity = mock(NameSimilarityJooqRepository.class);
    private final NameSimilarityAllowRepository allows = mock(NameSimilarityAllowRepository.class);
    private final RepositoryService repositories = mock(RepositoryService.class);
    private final NameSimilarityCheckService check = new NameSimilarityCheckService(
            config,
            similarity,
            allows,
            repositories);

    @BeforeEach
    void configure() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getNameThreshold()).thenReturn(0.7f);
        when(config.getDisplayNameThreshold()).thenReturn(0.6f);
        when(config.getMinFuzzyLength()).thenReturn(4);
        when(config.getMaxMatches()).thenReturn(10);
        when(config.getStatementTimeout()).thenReturn(Duration.ofSeconds(10));
    }

    @Test
    void neverBlocksPublication() {
        assertThat(check.isEnforced()).isFalse();
        assertThat(check.isRequired()).isFalse();
        assertThat(check.getCheckType()).isEqualTo("NAME_SIMILARITY");
    }

    @Test
    void passesWithoutMatches() {
        when(similarity.findSimilar(any())).thenReturn(List.of());

        assertThat(check.check(context("pythonn-tools", "python", "Python")).passed()).isTrue();
    }

    @Test
    void probesTheScannedNamesWithTheConfiguredThresholds() {
        when(similarity.findSimilar(any())).thenReturn(List.of());

        check.check(context("pythonn-tools", "python", "Python Tools"));

        var probe = ArgumentCaptor.forClass(Probe.class);
        verify(similarity).findSimilar(probe.capture());
        assertThat(probe.getValue()).isEqualTo(
                new Probe(
                        "pythonn-tools",
                        "python",
                        List.of("Python Tools"),
                        0.7f,
                        0.6f,
                        4,
                        10,
                        Duration.ofSeconds(10)));
    }

    @Test
    void leavesOutTheDisplayNameTheScanFellBackTo() {
        when(similarity.findSimilar(any())).thenReturn(List.of());

        check.check(context("pythonn-tools", "python", "python"));

        var probe = ArgumentCaptor.forClass(Probe.class);
        verify(similarity).findSimilar(probe.capture());
        assertThat(probe.getValue().displayNames()).isEmpty();
    }

    @Test
    void flagsEachMatchedExtensionOnceWithItsClosestMatch() {
        when(similarity.findSimilar(any())).thenReturn(
                List.of(
                        match(1, NameSimilarityField.NAME, "python", "python", 1f, true),
                        match(1, NameSimilarityField.DISPLAY_NAME, "Python", "Pythom", 0.56f, false),
                        match(2, NameSimilarityField.DISPLAY_NAME, "Pythonic", "Pythom", 0.72f, false)));

        var result = check.check(context("pyext-labs", "python", "Pythom"));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).containsExactly(
                new PublishCheck.Failure("NAME", "Extension name \"python\" is also used by ns1.ext1"),
                new PublishCheck.Failure(
                        "DISPLAY_NAME",
                        "Display name \"Pythom\" resembles ns2.ext2 (\"Pythonic\", 72% similar)"));
    }

    @Test
    void leavesOutExtensionsAllowedAlongsideAnExistingExtension() {
        var existing = mock(Extension.class);
        when(existing.getId()).thenReturn(99L);
        when(repositories.findExtension("python", "pyext-labs")).thenReturn(existing);
        var allow = new NameSimilarityAllow();
        allow.setExtensionId(1);
        allow.setOtherExtensionId(99);
        when(allows.findInvolving(List.of(99L))).thenReturn(List.of(allow));
        when(similarity.findSimilar(any())).thenReturn(
                List.of(
                        match(1, NameSimilarityField.NAME, "python", "python", 1f, true)));

        assertThat(check.check(context("pyext-labs", "python", "Pythom")).passed()).isTrue();
    }

    private static PublishCheck.Context context(String namespace, String name, String displayName) {
        var scan = new ExtensionScan();
        scan.setNamespaceName(namespace);
        scan.setExtensionName(name);
        scan.setExtensionDisplayName(displayName);
        return new PublishCheck.Context(scan, mock(TempFile.class), new UserData());
    }

    private static Match match(
            long id,
            NameSimilarityField field,
            String value,
            String probeValue,
            float similarity,
            boolean exact
    ) {
        return new Match(
                id,
                "ns" + id,
                "ext" + id,
                field,
                value,
                probeValue,
                similarity,
                exact,
                0.2f,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                100);
    }
}
