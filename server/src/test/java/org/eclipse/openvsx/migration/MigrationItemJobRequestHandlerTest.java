/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.migration;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.entities.MigrationItem;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.SettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationItemJobRequestHandlerTest {

    @Mock
    SettingsService settings;
    @Mock
    RepositoryService repositories;
    @Mock
    MigrationService migrations;
    @Mock
    MigrationScheduler scheduler;

    @Test
    void run_doesNothingWhenReadOnly() throws Exception {
        when(settings.isReadOnly()).thenReturn(true);

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        verify(repositories, never()).findNotMigratedItems(any());
    }

    @Test
    void validateBatchSize_clampsNonPositiveValuesToOne() {
        var handler = new MigrationItemJobRequestHandler(settings, repositories, migrations, scheduler);
        ReflectionTestUtils.setField(handler, "batchSize", 0);

        handler.validateBatchSize();

        assertThat((int) ReflectionTestUtils.getField(handler, "batchSize")).isEqualTo(1);
    }

    @Test
    void run_requestsThePageSizeConfiguredAsBatchSize() throws Exception {
        when(repositories.findNotMigratedItems(any())).thenReturn(new SliceImpl<>(List.of()));

        newHandler(37).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repositories).findNotMigratedItems(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(37);
    }

    @Test
    void run_staggersEachItemsScheduledTimeInsteadOfMakingThemAllDueAtOnce() throws Exception {
        var items = List.of(migrationItem(1), migrationItem(2), migrationItem(3));
        when(repositories.findNotMigratedItems(any())).thenReturn(new SliceImpl<>(items));

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        var scheduledAt = ArgumentCaptor.forClass(Instant.class);
        verify(migrations, org.mockito.Mockito.times(3)).scheduleMigration(any(), scheduledAt.capture());
        var times = scheduledAt.getAllValues();
        // Strictly increasing and evenly spaced, not all equal to the same "now" -- that's the whole
        // point of staggering (see MigrationService.scheduleMigration for why).
        assertThat(times.get(1)).isAfter(times.get(0));
        assertThat(times.get(2)).isAfter(times.get(1));
        var firstGap = java.time.Duration.between(times.get(0), times.get(1));
        var secondGap = java.time.Duration.between(times.get(1), times.get(2));
        assertThat(firstGap).isEqualTo(secondGap);
    }

    @Test
    void run_queriesTheRemainingBacklogAfterSchedulingSoItCanBeLogged() throws Exception {
        when(repositories.findNotMigratedItems(any()))
                .thenReturn(new SliceImpl<>(List.of(migrationItem(1)), Pageable.unpaged(), true));
        when(repositories.countNotMigratedItems()).thenReturn(4823L);

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        verify(repositories).countNotMigratedItems();
    }

    @Test
    void run_skipsTheRemainingBacklogQueryWhenNothingWasFound() throws Exception {
        when(repositories.findNotMigratedItems(any())).thenReturn(new SliceImpl<>(List.of()));

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        verify(repositories, never()).countNotMigratedItems();
    }

    @Test
    void run_deletesTheRecurringJobOnceTheresNothingLeftToMigrate() throws Exception {
        when(repositories.findNotMigratedItems(any()))
                .thenReturn(new SliceImpl<>(List.of(migrationItem(1)), Pageable.unpaged(), false));

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        verify(scheduler).deleteScheduleMigrationItemsJob();
    }

    @Test
    void run_keepsTheRecurringJobWhileMoreItemsRemain() throws Exception {
        when(repositories.findNotMigratedItems(any()))
                .thenReturn(new SliceImpl<>(List.of(migrationItem(1)), Pageable.unpaged(), true));

        newHandler(200).run(new HandlerJobRequest<>(MigrationItemJobRequestHandler.class));

        verify(scheduler, never()).deleteScheduleMigrationItemsJob();
    }

    private MigrationItem migrationItem(long id) {
        var item = new MigrationItem();
        item.setId(id);
        item.setJobName("FileResourceSizeMigration");
        item.setEntityId(id);
        return item;
    }

    private MigrationItemJobRequestHandler newHandler(int batchSize) {
        var handler = new MigrationItemJobRequestHandler(settings, repositories, migrations, scheduler);
        ReflectionTestUtils.setField(handler, "batchSize", batchSize);
        return handler;
    }
}
