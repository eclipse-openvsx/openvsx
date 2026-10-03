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
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public class SizeOverrideJson extends ResultJson {

    private long id;

    private String namespace;

    /** {@code null} means the override applies to the whole namespace. */
    private @Nullable String extension;

    private long maxSize;

    public static SizeOverrideJson error(String message) {
        var json = new SizeOverrideJson();
        json.setError(message);
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public @Nullable String getExtension() {
        return extension;
    }

    public void setExtension(@Nullable String extension) {
        this.extension = extension;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
