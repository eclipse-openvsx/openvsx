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

import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import org.eclipse.openvsx.entities.FileResource;

/**
 * The enabled {@link DownloadRecordSource}s, indexed by storage type.
 * <p>
 * Resolving a source is a lookup rather than a scan, which matters for {@link #covers} because that
 * runs on every download. Build one per holder from an {@code ObjectProvider} once the application
 * context is up - {@code ObjectProvider.stream()} resolves beans from the bean factory on each call,
 * so it is not something to do per request.
 * <p>
 * Enablement is settled here, once: every input to {@link DownloadRecordSource#isEnabled()} is a
 * configuration property and so is fixed at startup. A source that is not enabled is absent from the
 * index entirely, which is what keeps enablement out of {@link DownloadRecordSource#covers} - an
 * implementation cannot suppress downloads that nothing ingests by forgetting to check for it.
 */
public final class DownloadRecordSourceIndex {

    private final Map<String, DownloadRecordSource> byStorageType;

    public DownloadRecordSourceIndex(ObjectProvider<DownloadRecordSource> sources) {
        this.byStorageType = sources.stream()
                .filter(DownloadRecordSource::isEnabled)
                .collect(
                        Collectors.toUnmodifiableMap(
                                DownloadRecordSource::getStorageType,
                                source -> source,
                                (first, second) -> {
                                    // Two sources ingesting the same storage type would each claim the same
                                    // downloads, and which one answered would depend on bean ordering. There is
                                    // no safe way to pick, so refuse to start rather than silently use one.
                                    throw new IllegalStateException(
                                            "Multiple enabled download ingestion sources for storage type '"
                                                    + first.getStorageType() + "': " + first.getClass().getName()
                                                    + " and " + second.getClass().getName());
                                }));
    }

    /**
     * The enabled source ingesting the given storage type, or {@code null} if there is none.
     */
    public @Nullable DownloadRecordSource find(String storageType) {
        return byStorageType.get(storageType);
    }

    /**
     * Whether downloads of the given file are counted by an enabled source, in which case the request
     * path must not count them again.
     */
    public boolean covers(FileResource resource) {
        var source = byStorageType.get(resource.getStorageType());
        return source != null && source.covers(resource);
    }
}
