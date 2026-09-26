/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identifies one extension a moderation action applies to.
 * <p>
 * Namespace and extension name are kept as separate fields rather than a single qualified id
 * because extension names may contain dots.
 */
@Schema(
    name = "NameSquattingTarget",
    description = "The extension a name squatting moderation action applies to"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingTargetJson {

    @Schema(description = "Namespace of the extension", example = "julialang")
    private String namespace;

    @Schema(description = "Name of the extension", example = "language-julia")
    private String extension;

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
}
