/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
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

@Schema(
    name = "Result",
    description = "Generic result indicator",
    subTypes = {
        ExtensionJson.class,
        NamespaceJson.class,
        NamespaceMembershipListJson.class,
        QueryResultJson.class,
        ReviewListJson.class,
        SearchResultJson.class,
        UserJson.class
    }
)
@JsonInclude(Include.NON_NULL)
public class ResultJson {

    public static ResultJson success(String message) {
        var result = new ResultJson();
        result.success = message;
        return result;
    }

    public static ResultJson warning(String message) {
        var result = new ResultJson();
        result.warning = message;
        return result;
    }

    public static ResultJson error(String message) {
        var result = new ResultJson();
        result.error = message;
        return result;
    }

    @Schema(description = "Indicates success of the operation (omitted if a more specific result type is returned)")
    private String success;

    @Schema(description = "Indicates a warning; when this is present, other properties can still be used")
    private String warning;

    @Schema(description = "Indicates an error; when this is present, all other properties should be ignored")
    private String error;

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
