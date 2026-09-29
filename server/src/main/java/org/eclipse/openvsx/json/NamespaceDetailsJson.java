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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "NamespaceDetails",
    description = "Details of a namespace"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceDetailsJson extends ResultJson {

    public static NamespaceDetailsJson error(String message) {
        var result = new NamespaceDetailsJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Name of the namespace")
    @NotNull
    private String name;

    @Schema(description = "Display name of the namespace")
    @NotNull
    private String displayName;

    @Schema(description = "Description of the namespace")
    private String description;

    @Schema(description = "Logo URL of the namespace")
    private String logo;

    @Schema(description = "Website URL of the namespace")
    private String website;

    @Schema(description = "Support URL of the namespace")
    private String supportLink;

    @Schema(description = "Map of social network names to their profile URLs")
    private Map<String, String> socialLinks;

    @Schema(description = "Map of extension names to their metadata URLs")
    private List<SearchEntryJson> extensions;

    @Schema(description = "Indicates whether the namespace has an owner")
    @NotNull
    private boolean verified;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getSupportLink() {
        return supportLink;
    }

    public void setSupportLink(String supportLink) {
        this.supportLink = supportLink;
    }

    public Map<String, String> getSocialLinks() {
        return socialLinks;
    }

    public void setSocialLinks(Map<String, String> socialLinks) {
        this.socialLinks = socialLinks;
    }

    public List<SearchEntryJson> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<SearchEntryJson> extensions) {
        this.extensions = extensions;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamespaceDetailsJson that = (NamespaceDetailsJson) o;
        return verified == that.verified
                && Objects.equals(name, that.name)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(logo, that.logo)
                && Objects.equals(website, that.website)
                && Objects.equals(supportLink, that.supportLink)
                && Objects.equals(socialLinks, that.socialLinks)
                && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(name, displayName, description, logo, website, supportLink, socialLinks, extensions, verified);
    }
}
