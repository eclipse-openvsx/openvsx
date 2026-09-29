/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.json;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "Namespace",
    description = "Metadata of a namespace"
)
@JsonInclude(Include.NON_NULL)
public class NamespaceJson extends ResultJson {

    public static NamespaceJson error(String message) {
        var result = new NamespaceJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Name of the namespace")
    @NotNull
    private String name;

    @Schema(description = "Map of extension names to their metadata URLs (not required for creating)")
    private Map<String, String> extensions;

    @Schema(description = "Indicates whether the namespace has an owner (not required for creating)")
    @NotNull
    private Boolean verified;

    @Schema(hidden = true)
    private String membersUrl;

    @Schema(hidden = true)
    private String roleUrl;

    @Schema(hidden = true)
    private String detailsUrl;

    @Schema(hidden = true)
    private String trustedPublishingUrl;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, String> extensions) {
        this.extensions = extensions;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public String getMembersUrl() {
        return membersUrl;
    }

    public void setMembersUrl(String membersUrl) {
        this.membersUrl = membersUrl;
    }

    public String getRoleUrl() {
        return roleUrl;
    }

    public void setRoleUrl(String roleUrl) {
        this.roleUrl = roleUrl;
    }

    public String getDetailsUrl() {
        return detailsUrl;
    }

    public void setDetailsUrl(String detailsUrl) {
        this.detailsUrl = detailsUrl;
    }

    public String getTrustedPublishingUrl() {
        return trustedPublishingUrl;
    }

    public void setTrustedPublishingUrl(String trustedPublishingUrl) {
        this.trustedPublishingUrl = trustedPublishingUrl;
    }
}
