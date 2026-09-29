/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A badge to be shown in the sidebar of the extension page in the marketplace.
 */
@JsonInclude(Include.NON_NULL)
public class CsrfTokenJson extends ResultJson {

    public static CsrfTokenJson error(String message) {
        var info = new CsrfTokenJson();
        info.setError(message);
        return info;
    }

    public CsrfTokenJson() {
    }

    public CsrfTokenJson(String value, String header) {
        this.value = value;
        this.header = header;
    }

    private String value;

    private String header;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
