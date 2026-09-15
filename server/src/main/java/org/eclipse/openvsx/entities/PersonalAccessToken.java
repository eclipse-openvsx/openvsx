/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.util.TimeUtil;

import static java.util.Objects.requireNonNull;

// Same reasoning as on Extension: the paths that write a token row each touch a different column -
// the upgrade job rewrites `value` and `version`, using a token writes `accessed_timestamp`, revoking
// one writes `active`. Without @DynamicUpdate, Hibernate's full-row UPDATE would rewrite all of them
// from whatever the writing transaction happened to load, so an upgrade or a token use running
// alongside a revoke could silently put `active` back to true.
@Entity
@DynamicUpdate
@Table(name = "personal_access_token")
public class PersonalAccessToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "personalAccessTokenSeq")
    @SequenceGenerator(name = "personalAccessTokenSeq", sequenceName = "personal_access_token_seq")
    private long id;

    @ManyToOne
    @JoinColumn(name = "user_data")
    private UserData user;

    // 128, matching the width V1_72 gave the column: enough for the hex digest of any algorithm
    // `ovsx.access-token.token-hash-algorithm` may name (SHA-512's is 128 characters; the default
    // SHA-256's is 64), and for a still-unhashed version 0 value, whose 43-character body sits behind
    // a type marker and a deployment prefix.
    @Column(length = 128)
    private String value;

    private boolean active;

    private LocalDateTime createdTimestamp;

    private LocalDateTime accessedTimestamp;

    private LocalDateTime expiresTimestamp;

    private boolean notified;

    @Column(length = 2048)
    private String description;

    private int version;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PersonalAccessTokenType type;

    @ManyToOne
    @JoinColumn(name = "scope_extension_id")
    private Extension scopeExtension;

    @ManyToOne
    @JoinColumn(name = "scope_namespace_id")
    private Namespace scopeNamespace;

    @ManyToOne
    @JoinColumn(name = "trusted_publisher_id")
    private TrustedPublisher trustedPublisher;

    /**
     * The OIDC claims this token was exchanged for, carried from the exchange to the publish that uses it
     * and copied onto the version there. Null for every token that is not a trusted publishing one.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Nullable
    private Map<String, String> claims;

    /**
     * Convert to a JSON object.
     */
    public AccessTokenJson toAccessTokenJson() {
        var json = new AccessTokenJson();
        json.setId(this.getId());
        // The value is not included: it is displayed only when the token is created
        if (this.getCreatedTimestamp() != null) {
            json.setCreatedTimestamp(TimeUtil.toUTCString(this.getCreatedTimestamp()));
        }
        if (this.getAccessedTimestamp() != null) {
            json.setAccessedTimestamp(TimeUtil.toUTCString(this.getAccessedTimestamp()));
        }
        if (this.getExpiresTimestamp() != null) {
            json.setExpiresTimestamp(TimeUtil.toUTCString(this.getExpiresTimestamp()));
        }
        json.setNotified(this.isNotified());
        json.setDescription(this.getDescription());
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UserData getUser() {
        return user;
    }

    public void setUser(UserData user) {
        this.user = user;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime timestamp) {
        this.createdTimestamp = timestamp;
    }

    public LocalDateTime getAccessedTimestamp() {
        return accessedTimestamp;
    }

    public void setAccessedTimestamp(LocalDateTime timestamp) {
        this.accessedTimestamp = timestamp;
    }

    public LocalDateTime getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public void setExpiresTimestamp(LocalDateTime expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public PersonalAccessTokenType getType() {
        return type;
    }

    public void setType(PersonalAccessTokenType type) {
        this.type = requireNonNull(type);
    }

    public Extension getScopeExtension() {
        return scopeExtension;
    }

    public void setScopeExtension(Extension scopeExtension) {
        this.scopeExtension = scopeExtension;
    }

    public Namespace getScopeNamespace() {
        return scopeNamespace;
    }

    public void setScopeNamespace(Namespace scopeNamespace) {
        this.scopeNamespace = scopeNamespace;
    }

    @Nullable
    public Map<String, String> getClaims() {
        return claims;
    }

    public void setClaims(@Nullable Map<String, String> claims) {
        this.claims = claims;
    }

    public TrustedPublisher getTrustedPublisher() {
        return trustedPublisher;
    }

    public void setTrustedPublisher(TrustedPublisher trustedPublisher) {
        this.trustedPublisher = trustedPublisher;
    }

    // user uses id comparison to prevent infinite recursion
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PersonalAccessToken that = (PersonalAccessToken) o;
        return id == that.id
                && active == that.active
                && Objects.equals(getId(user), getId(that.user))
                && Objects.equals(value, that.value)
                && Objects.equals(createdTimestamp, that.createdTimestamp)
                && Objects.equals(accessedTimestamp, that.accessedTimestamp)
                && Objects.equals(expiresTimestamp, that.expiresTimestamp)
                && Objects.equals(notified, that.notified)
                && Objects.equals(description, that.description)
                && Objects.equals(type, that.type)
                && Objects.equals(scopeExtension, that.scopeExtension)
                && Objects.equals(scopeNamespace, that.scopeNamespace)
                && Objects.equals(trustedPublisher, that.trustedPublisher);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                getId(user),
                value,
                active,
                createdTimestamp,
                accessedTimestamp,
                expiresTimestamp,
                notified,
                description,
                type,
                scopeExtension,
                scopeNamespace,
                trustedPublisher);
    }

    private Long getId(UserData user) {
        return Optional.ofNullable(user).map(UserData::getId).orElse(null);
    }
}
