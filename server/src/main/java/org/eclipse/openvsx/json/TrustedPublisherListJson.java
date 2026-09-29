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

@JsonInclude(Include.NON_NULL)
public class TrustedPublisherListJson extends ResultJson {

    public static TrustedPublisherListJson error(String message) {
        var result = new TrustedPublisherListJson();
        result.setError(message);
        return result;
    }

    private List<TrustedPublisherJson> trustedPublishers;

    private List<String> registrableExtensions;

    public List<TrustedPublisherJson> getTrustedPublishers() {
        return trustedPublishers;
    }

    public void setTrustedPublishers(List<TrustedPublisherJson> trustedPublishers) {
        this.trustedPublishers = trustedPublishers;
    }

    /**
     * Names of the extensions of this namespace a trusted publisher can still be registered for: the
     * active extensions that do not have a registration yet. Clients should offer only these, as
     * registering for anything else is rejected.
     */
    public List<String> getRegistrableExtensions() {
        return registrableExtensions;
    }

    public void setRegistrableExtensions(List<String> registrableExtensions) {
        this.registrableExtensions = registrableExtensions;
    }
}
