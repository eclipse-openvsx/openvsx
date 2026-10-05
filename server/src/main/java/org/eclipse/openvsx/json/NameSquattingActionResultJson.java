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
 * Outcome of a name squatting moderation action for one extension.
 */
@Schema(
    name = "NameSquattingActionResult",
    description = "Individual result in a name squatting moderation response"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingActionResultJson {

    @Schema(description = "Namespace of the extension that was processed")
    private String namespace;

    @Schema(description = "Name of the extension that was processed")
    private String extension;

    @Schema(description = "Whether the action was applied successfully")
    private boolean success;

    @Schema(description = "What the action changed, when it succeeded")
    private String message;

    @Schema(description = "Why the action could not be applied, when it failed")
    private String error;

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static NameSquattingActionResultJson success(String namespace, String extension, String message) {
        var result = new NameSquattingActionResultJson();
        result.setNamespace(namespace);
        result.setExtension(extension);
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static NameSquattingActionResultJson failure(String namespace, String extension, String error) {
        var result = new NameSquattingActionResultJson();
        result.setNamespace(namespace);
        result.setExtension(extension);
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
