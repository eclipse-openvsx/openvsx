/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.entities.AdminStatistics;

/**
 * The handler is now only the archival half: the computation it used to carry inline lives in
 * {@link AdminStatisticsService} and is covered by {@link AdminStatisticsServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminStatisticsJobRequestHandlerTest {

    @Mock
    AdminStatisticsService service;

    @InjectMocks
    AdminStatisticsJobRequestHandler handler;

    @Test
    void archivesTheComputedStatisticsForTheRequestedMonth() throws Exception {
        var statistics = new AdminStatistics();
        Mockito.when(service.computeAdminStatistics(2023, 11)).thenReturn(statistics);

        handler.run(new AdminStatisticsJobRequest(2023, 11));

        Mockito.verify(service).saveAdminStatistics(statistics);
    }
}
