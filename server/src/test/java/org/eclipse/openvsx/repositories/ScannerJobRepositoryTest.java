/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import org.eclipse.openvsx.entities.ScannerJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ScannerJobRepositoryTest {

    private final ScannerJobRepository repo = mock(ScannerJobRepository.class, CALLS_REAL_METHODS);
    private final ScannerJob job = new ScannerJob();

    @Test
    void trySave_returnsTrueWhenSaved() {
        doReturn(job).when(repo).saveAndFlush(job);

        assertThat(repo.trySave(job)).isTrue();
    }

    @Test
    void trySave_returnsFalseOnVersionConflict() {
        doThrow(new ObjectOptimisticLockingFailureException(ScannerJob.class, 1L)).when(repo).saveAndFlush(job);

        assertThat(repo.trySave(job)).isFalse();
    }
}
