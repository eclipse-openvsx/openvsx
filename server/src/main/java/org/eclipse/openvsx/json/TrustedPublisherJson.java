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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.jspecify.annotations.Nullable;

/**
 * A trusted publisher registration. On registration requests the {@code namespace}, {@code extension}, {@code provider}
 * and {@code registration} field are filled in by the client; the remaining fields are filled in by the server on responses.
 */
@JsonInclude(Include.NON_NULL)
public class TrustedPublisherJson extends ResultJson {

    public static TrustedPublisherJson error(String message) {
        var result = new TrustedPublisherJson();
        result.setError(message);
        return result;
    }

    private Long id;

    private String namespace;

    private String extension;

    private String provider;

    private Map<String, String> registration;

    @Nullable
    private String createdTimestamp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Map<String, String> getRegistration() {
        return registration;
    }

    public void setRegistration(Map<String, String> registration) {
        this.registration = registration;
    }

    @Nullable
    public String getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(@Nullable String createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
