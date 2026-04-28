/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.storage.log;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.metrics.DownloadCountValidator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AzureDownloadCountHandlerTest {

    @Test
    public void shouldFilterEventsWhenValidatorRejectsSome() {
        var processor = Mockito.mock(DownloadCountProcessor.class);
        var validator = Mockito.mock(DownloadCountValidator.class);
        Mockito.when(processor.resolveDownloadFileToExtensionId(Mockito.eq(FileResource.STORAGE_AZURE), Mockito.anyList()))
                .thenReturn(Map.of("TEST.VSIX", 10L));
        Instant ts = Instant.parse("2026-01-01T00:10:00Z");
        Mockito.when(validator.shouldCountDownload(10L, "1.1.1.1", "Mozilla/5.0", ts))
                .thenReturn(true);
        Mockito.when(validator.shouldCountDownload(10L, "2.2.2.2", "curl/8.0", ts))
                .thenReturn(false);

        var handler = new AzureDownloadCountHandler(processor, Optional.of(validator));
        var events = List.of(
                new AzureDownloadCountHandler.DownloadEvent("TEST.VSIX", "1.1.1.1", "Mozilla/5.0", ts),
                new AzureDownloadCountHandler.DownloadEvent("TEST.VSIX", "2.2.2.2", "curl/8.0", ts)
        );

        var counts = handler.countValidatedEvents(events);

        assertEquals(Map.of("TEST.VSIX", 1), counts);
    }
}
