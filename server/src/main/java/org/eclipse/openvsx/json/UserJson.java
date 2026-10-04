/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "User",
    description = "User data"
)
@JsonInclude(Include.NON_NULL)
public class UserJson extends ResultJson {

    public static UserJson error(String message) {
        var user = new UserJson();
        user.setError(message);
        return user;
    }

    @Schema(description = "Login name")
    @NotNull
    private String loginName;

    @Schema(hidden = true)
    private String tokensUrl;

    @Schema(hidden = true)
    private String createTokenUrl;

    @Schema(hidden = true)
    private String role;

    @Schema(hidden = true)
    private Set<String> permissions;

    @Schema(description = "Full name")
    private String fullName;

    @Schema(description = "URL to the user's avatar image")
    private String avatarUrl;

    @Schema(description = "URL to the user's profile page")
    private String homepage;

    @Schema(description = "Authentication provider (e.g. github)")
    private String provider;

    @Schema(hidden = true)
    private PublisherAgreement publisherAgreement;

    @Schema(hidden = true)
    private List<UserJson> additionalLogins;

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getTokensUrl() {
        return tokensUrl;
    }

    public void setTokensUrl(String tokensUrl) {
        this.tokensUrl = tokensUrl;
    }

    public String getCreateTokenUrl() {
        return createTokenUrl;
    }

    public void setCreateTokenUrl(String createTokenUrl) {
        this.createTokenUrl = createTokenUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public PublisherAgreement getPublisherAgreement() {
        return publisherAgreement;
    }

    public void setPublisherAgreement(PublisherAgreement publisherAgreement) {
        this.publisherAgreement = publisherAgreement;
    }

    public List<UserJson> getAdditionalLogins() {
        return additionalLogins;
    }

    public void setAdditionalLogins(List<UserJson> additionalLogins) {
        this.additionalLogins = additionalLogins;
    }

    @JsonInclude(Include.NON_NULL)
    public static class PublisherAgreement {

        /* 'none' | 'signed' | 'outdated' */
        private String status;

        @Schema(hidden = true)
        private String version;

        private String timestamp;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PublisherAgreement that = (PublisherAgreement) o;
            return Objects.equals(status, that.status) &&
                    Objects.equals(version, that.version) &&
                    Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, version, timestamp);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserJson userJson = (UserJson) o;
        return Objects.equals(loginName, userJson.loginName)
                && Objects.equals(tokensUrl, userJson.tokensUrl)
                && Objects.equals(createTokenUrl, userJson.createTokenUrl)
                && Objects.equals(role, userJson.role)
                && Objects.equals(permissions, userJson.permissions)
                && Objects.equals(fullName, userJson.fullName)
                && Objects.equals(avatarUrl, userJson.avatarUrl)
                && Objects.equals(homepage, userJson.homepage)
                && Objects.equals(provider, userJson.provider)
                && Objects.equals(publisherAgreement, userJson.publisherAgreement)
                && Objects.equals(additionalLogins, userJson.additionalLogins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                loginName,
                tokensUrl,
                createTokenUrl,
                role,
                permissions,
                fullName,
                avatarUrl,
                homepage,
                provider,
                publisherAgreement,
                additionalLogins);
    }
}
