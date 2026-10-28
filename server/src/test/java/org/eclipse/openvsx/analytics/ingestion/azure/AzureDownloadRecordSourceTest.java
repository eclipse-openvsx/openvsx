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
package org.eclipse.openvsx.analytics.ingestion.azure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

class AzureDownloadRecordSourceTest {

    private static final String BLOB = "resourceId=/subscriptions/s/PT1H.json";
    private static final Instant BLOB_TIME = Instant.parse("2026-10-14T09:00:00Z");

    private static final String LOG_LINE_WITHOUT_TIME = """
            {"operationName":"GetBlob","statusCode":200,"uri":"https://store.blob.core.windows.net/openvsx-resources/redhat/java/1.0.0/redhat.java-1.0.0.vsix","callerIpAddress":"1.2.3.4:5000","properties":{"userAgentHeader":"VSCode"}}
            """;

    private AzureDownloadRecordSource sourceReading(String log, OffsetDateTime lastModified) {
        var source = new AzureDownloadRecordSource(Mockito.mock(DownloadIngestionMetrics.class));
        source.storageServiceEndpoint = "https://store.blob.core.windows.net";
        source.storageBlobContainer = "openvsx-resources";

        var properties = Mockito.mock(BlobProperties.class);
        Mockito.when(properties.getLastModified()).thenReturn(lastModified);
        var blob = Mockito.mock(BlobClient.class);
        Mockito.when(blob.downloadToFile(anyString(), anyBoolean())).thenAnswer(invocation -> {
            Files.writeString(Path.of(invocation.getArgument(0, String.class)), log);
            return properties;
        });
        var container = Mockito.mock(BlobContainerClient.class);
        Mockito.when(container.getBlobClient(BLOB)).thenReturn(blob);
        ReflectionTestUtils.setField(source, "containerClient", container);
        return source;
    }

    @Test
    void recordWithoutTimeFallsBackToTheTimeTheBlobWasLastWritten() throws Exception {
        var source = sourceReading(LOG_LINE_WITHOUT_TIME, BLOB_TIME.atOffset(ZoneOffset.UTC));

        var records = source.read(BLOB);

        assertThat(records).hasSize(1);
        // not the time of reading: a replay on a later day has to put the download in the same bucket
        assertThat(records.getFirst().time()).isEqualTo(BLOB_TIME);
    }

    @Test
    void recordWithTimeKeepsItsOwnTime() throws Exception {
        var log = LOG_LINE_WITHOUT_TIME
                .replace("{\"operationName\"", "{\"time\":\"2026-10-14T08:30:00Z\",\"operationName\"");
        var source = sourceReading(log, BLOB_TIME.atOffset(ZoneOffset.UTC));

        assertThat(source.read(BLOB).getFirst().time()).isEqualTo(Instant.parse("2026-10-14T08:30:00Z"));
    }
}
