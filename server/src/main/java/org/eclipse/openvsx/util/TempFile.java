/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.util;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempFile implements Closeable {

    private final Path path;
    private FileResource resource;
    private Namespace namespace;
    
    public TempFile(String prefix, String suffix) throws IOException {
        path = Files.createTempFile(prefix, suffix);
    }

    /**
     * Create a TempFile from an existing path.
     * <p>
     * Used when extracting files to a pre-created temp location.
     * The file will be deleted when close() is called.
     * 
     * @param existingPath Path to an existing temp file
     */
    public TempFile(Path existingPath) {
        this.path = existingPath;
    }

    public Path getPath() {
        return path;
    }

    public FileResource getResource() {
        return resource;
    }

    public void setResource(FileResource resource) {
        this.resource = resource;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}
