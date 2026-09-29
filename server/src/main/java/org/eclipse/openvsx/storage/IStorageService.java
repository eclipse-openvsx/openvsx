/*******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.data.util.Pair;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UrlUtil;

public interface IStorageService {

    /**
     * Indicates whether this storage service is enabled by application config.
     */
    boolean isEnabled();

    /**
     * Upload a file to the external storage.
     */
    void uploadFile(TempFile tempFile);

    /**
     * Remove a file from the external storage.
     */
    void removeFile(FileResource resource);

    /**
     * Returns the public access location of a resource.
     */
    URI getLocation(FileResource resource);

    /**
     * Upload a namespace logo to the external storage.
     */
    void uploadNamespaceLogo(TempFile logoFile);

    /**
     * Remove a namespace logo from the external storage.
     */
    void removeNamespaceLogo(Namespace namespace);

    /**
     * Returns the public access location of a namespace logo.
     */
    URI getNamespaceLogoLocation(Namespace namespace);

    TempFile downloadFile(FileResource resource) throws IOException;

    /**
     * Returns the size in bytes of {@code resource} as already stored, via a metadata-only lookup
     * (e.g. a HEAD request) rather than downloading its content. Used to backfill
     * {@link FileResource#getSize()} for resources stored before that field existed, where
     * downloading every file just to measure it would be far too expensive at scale.
     *
     * @throws FileNotFoundInStorageException if the backing object is confirmed absent, as opposed
     *         to some other (possibly transient) storage error
     */
    long getFileSize(FileResource resource) throws IOException;

    void copyFiles(List<Pair<FileResource, FileResource>> pairs);

    void copyNamespaceLogo(Namespace oldNamespace, Namespace newNamespace);

    @Nullable
    Path getCachedFile(FileResource resource);

    default String getObjectKey(FileResource resource) {
        var extVersion = resource.getExtension();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var segments = new String[] { namespace.getName(), extension.getName() };
        if (!extVersion.isUniversalTargetPlatform()) {
            segments = ArrayUtils.add(segments, extVersion.getTargetPlatform());
        }

        segments = ArrayUtils.add(segments, extVersion.getVersion());
        segments = ArrayUtils.addAll(segments, resource.getName().split("/"));
        var url = UrlUtil.createApiUrl("", segments);
        return url.substring(1); // remove first '/'
    }

    default String getObjectKey(Namespace namespace) {
        var url = UrlUtil.createApiUrl("", namespace.getName(), "logo", namespace.getLogoName());
        return url.substring(1); // remove first '/'
    }

    /**
     * Returns the object key of {@code resource} in the form it has to take within a URL path.
     * <p>
     * Defaults to the object key itself, which {@link #getObjectKey(FileResource)} already encoded
     * per path segment. Storage providers that do not interpret a path the way RFC 3986 defines it
     * can override this to escape what they need on top of that.
     */
    default String getObjectKeyAsUrlPath(FileResource resource) {
        return getObjectKey(resource);
    }

    /**
     * Returns the object key of the logo of {@code namespace} in the form it has to take within a URL
     * path, see {@link #getObjectKeyAsUrlPath(FileResource)}.
     */
    default String getObjectKeyAsUrlPath(Namespace namespace) {
        return getObjectKey(namespace);
    }
}
