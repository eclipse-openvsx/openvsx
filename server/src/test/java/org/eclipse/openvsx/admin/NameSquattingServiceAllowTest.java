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
package org.eclipse.openvsx.admin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NameSquattingJson;
import org.eclipse.openvsx.repositories.NameSimilarityAllowRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.ExtensionNames;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Match;
import org.eclipse.openvsx.repositories.NameSimilarityJooqRepository.Probe;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.NameSimilarityCheckService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Allowing a pair when the database times out or another admin gets there first. */
@ExtendWith(MockitoExtension.class)
class NameSquattingServiceAllowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Mock
    NameSimilarityJooqRepository similarity;

    @Mock
    NameSimilarityAllowRepository allows;

    @Mock
    NameSimilarityCheckService check;

    @Mock
    RepositoryService repositories;

    @Mock
    LogService logs;

    @Mock
    EntityManager entityManager;

    private NameSquattingService service;

    private final UserData admin = new UserData();

    @BeforeEach
    void setUp() {
        service = new NameSquattingService(similarity, allows, check, repositories, logs, entityManager);
        extension(1, "lookalike-ns", "snippets");
        extension(2, "original-ns", "snippets");
        when(similarity.findNames(anyCollection())).thenReturn(
                List.of(names(1, "lookalike-ns"), names(2, "original-ns")));
        when(allows.findPair(1, 2)).thenReturn(Optional.empty());
        when(check.probe(anyString(), anyString(), anyCollection())).thenAnswer(
                invocation -> new Probe(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        0.7f,
                        0.7f,
                        4,
                        10,
                        TIMEOUT));
    }

    @Test
    void answers503WhenALookupRunsOutOfItsStatementTimeout() {
        when(check.getStatementTimeout()).thenReturn(TIMEOUT);
        when(check.findMatches(any(Probe.class))).thenThrow(
                new QueryTimeoutException(
                        "cancelled",
                        new SQLException("canceling statement due to statement timeout", "57014")));

        assertThatThrownBy(() -> service.allow(request(), admin))
                .isInstanceOfSatisfying(
                        ErrorResultException.class,
                        exc -> assertThat(exc.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(similarity, never()).insertAllow(any());
    }

    @Test
    void reportsAPairAnotherAdminAllowedMeanwhileAsAlreadyAllowed() throws ErrorResultException {
        when(check.findMatches(any(Probe.class))).thenReturn(List.of(match()));
        when(similarity.insertAllow(any())).thenReturn(false);

        var result = service.allow(request(), admin);

        assertThat(result.getSuccess()).startsWith("Already allowed");
        verify(logs, never()).logAction(any(), any());
    }

    private void extension(long id, String namespace, String name) {
        var extension = new Extension();
        extension.setId(id);
        when(repositories.findExtension(name, namespace)).thenReturn(extension);
    }

    private static ExtensionNames names(long id, String namespace) {
        return new ExtensionNames(id, namespace, "snippets", true, LocalDateTime.now(), 0, 1, List.of("Snippets"));
    }

    private static Match match() {
        return new Match(
                2,
                "original-ns",
                "snippets",
                NameSimilarityField.NAME,
                "snippets",
                "snippets",
                1f,
                true,
                0.2f,
                LocalDateTime.now(),
                0);
    }

    private static NameSquattingJson.AllowRequest request() {
        return new NameSquattingJson.AllowRequest("lookalike-ns", "snippets", "original-ns", "snippets", null);
    }
}
