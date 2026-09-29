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

import static java.util.Objects.requireNonNull;

/**
 * A trusted publisher provider input. This is one way, is sent to users only from server.
 */
@JsonInclude(Include.NON_NULL)
public class TrustedPublisherInputJson extends ResultJson {

    public static TrustedPublisherInputJson error(String message) {
        var result = new TrustedPublisherInputJson();
        result.setError(message);
        return result;
    }

    public static TrustedPublisherInputJson create(String key, String description, boolean optional) {
        requireNonNull(key);
        requireNonNull(description);
        TrustedPublisherInputJson result = new TrustedPublisherInputJson();
        result.setKey(key);
        result.setDescription(description);
        result.setOptional(optional);
        return result;
    }

    private String key;

    private String description;

    private boolean optional;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }
}
