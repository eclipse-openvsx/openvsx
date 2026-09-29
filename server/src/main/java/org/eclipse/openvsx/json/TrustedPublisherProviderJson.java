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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A trusted publisher provider description for client. This is one way, is listed to users only from server.
 */
@JsonInclude(Include.NON_NULL)
public class TrustedPublisherProviderJson extends ResultJson {

    public static TrustedPublisherProviderJson error(String message) {
        var result = new TrustedPublisherProviderJson();
        result.setError(message);
        return result;
    }

    private String id;

    private String name;

    private String url;

    private List<TrustedPublisherInputJson> registrationInputs;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public List<TrustedPublisherInputJson> getRegistrationInputs() {
        return registrationInputs;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setRegistrationInputs(List<TrustedPublisherInputJson> registrationInputs) {
        this.registrationInputs = registrationInputs;
    }
}
