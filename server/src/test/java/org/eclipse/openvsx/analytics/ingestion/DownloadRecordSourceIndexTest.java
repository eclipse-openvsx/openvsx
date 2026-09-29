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
package org.eclipse.openvsx.analytics.ingestion;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import org.eclipse.openvsx.entities.FileResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadRecordSourceIndexTest {

    @Test
    void findReturnsTheEnabledSourceForItsStorageType() {
        var aws = source(FileResource.STORAGE_AWS, true);
        var azure = source(FileResource.STORAGE_AZURE, true);

        var index = index(aws, azure);

        assertThat(index.find(FileResource.STORAGE_AWS)).isSameAs(aws);
        assertThat(index.find(FileResource.STORAGE_AZURE)).isSameAs(azure);
    }

    @Test
    void findReturnsNullForAStorageTypeWithNoSource() {
        assertThat(index(source(FileResource.STORAGE_AWS, true)).find(FileResource.STORAGE_LOCAL)).isNull();
    }

    @Test
    void findReturnsNullForADisabledSource() {
        // The bean exists - its log location is configured - but a dependency of it is not, so it
        // cannot ingest. Callers must treat it as absent, not as covering its storage type.
        assertThat(index(source(FileResource.STORAGE_AWS, false)).find(FileResource.STORAGE_AWS)).isNull();
    }

    @Test
    void isEnabledIsAskedOnceAtIndexingRatherThanPerLookup() {
        // covers() runs on every download, so isEnabled() must not be on that path - Azure's
        // implementation logs a warning from inside it.
        var aws = source(FileResource.STORAGE_AWS, true);
        var index = index(aws);
        verify(aws).isEnabled();

        index.covers(resource(FileResource.STORAGE_AWS));
        index.covers(resource(FileResource.STORAGE_AWS));

        verify(aws, never()).getCronSchedule();
        verify(aws).isEnabled();
    }

    @Test
    void coversDefersToTheSourceForAStorageTypeItHolds() {
        var aws = mock(DownloadRecordSource.class);
        when(aws.isEnabled()).thenReturn(true);
        when(aws.getStorageType()).thenReturn(FileResource.STORAGE_AWS);
        when(aws.covers(any())).thenReturn(false);

        // A source may cover less than its whole storage type, so a matching type is not enough.
        assertThat(index(aws).covers(resource(FileResource.STORAGE_AWS))).isFalse();
    }

    @Test
    void coversIsFalseWhenNoEnabledSourceHoldsTheStorageType() {
        assertThat(index(source(FileResource.STORAGE_AWS, false)).covers(resource(FileResource.STORAGE_AWS)))
                .isFalse();
        assertThat(index(source(FileResource.STORAGE_AWS, true)).covers(resource(FileResource.STORAGE_LOCAL)))
                .isFalse();
    }

    @Test
    void rejectsTwoEnabledSourcesForOneStorageType() {
        // Which one answered would otherwise depend on bean ordering.
        assertThatThrownBy(() -> index(source(FileResource.STORAGE_AWS, true), source(FileResource.STORAGE_AWS, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FileResource.STORAGE_AWS);
    }

    @Test
    void toleratesTwoDisabledSourcesForOneStorageType() {
        // Neither is indexed, so there is nothing to disambiguate and no reason to fail startup.
        assertThat(
                index(source(FileResource.STORAGE_AWS, false), source(FileResource.STORAGE_AWS, false))
                        .find(FileResource.STORAGE_AWS))
                .isNull();
    }

    private DownloadRecordSourceIndex index(DownloadRecordSource... sources) {
        @SuppressWarnings("unchecked")
        ObjectProvider<DownloadRecordSource> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(sources));
        return new DownloadRecordSourceIndex(provider);
    }

    private DownloadRecordSource source(String storageType, boolean enabled) {
        var source = mock(DownloadRecordSource.class);
        when(source.isEnabled()).thenReturn(enabled);
        if (enabled) {
            when(source.getStorageType()).thenReturn(storageType);
            lenient().when(source.covers(any())).thenAnswer(
                    invocation -> storageType.equals(((FileResource) invocation.getArgument(0)).getStorageType()));
        }
        return source;
    }

    private FileResource resource(String storageType) {
        var resource = new FileResource();
        resource.setStorageType(storageType);
        return resource;
    }
}
