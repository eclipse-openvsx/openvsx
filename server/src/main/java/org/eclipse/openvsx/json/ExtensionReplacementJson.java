/******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
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
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ExtensionReplacement",
    description = "Metadata of an extension replacement"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtensionReplacementJson {

    @Schema(description = "URL of the extension replacement")
    private String url;

    @Schema(description = "Name to be displayed in user interfaces")
    private String displayName;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
