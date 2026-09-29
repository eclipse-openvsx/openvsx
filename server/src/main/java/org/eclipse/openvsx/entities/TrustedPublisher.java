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
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.eclipse.openvsx.json.TrustedPublisherJson;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * A trusted publisher registration: claims resolved from a trust request, pinned to a namespace
 * and extension. The {@code claims} contains claims (extended with provider specific information), and
 * are kept within boundaries of application, they should not leave it.
 */
@Entity
@Table(name = "trusted_publisher")
public class TrustedPublisher implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "trustedPublisherSeq")
    @SequenceGenerator(name = "trustedPublisherSeq", sequenceName = "trusted_publisher_seq", allocationSize = 1)
    private long id;

    @ManyToOne
    @JoinColumn(name = "extension_id", nullable = false)
    private Extension extension;

    @Column(nullable = false, length = 32)
    private String provider;

    /**
     * Registration are public data; it may be shown to end user, and is in same format as user originally provided.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> registration;

    /**
     * Claims are internal only; should not leave the boundaries of application and is in provider specific format.
     * For example, workflow file is put into a "path"-like construct, that is provider specific.
     * Hence, {@link #toJson()} omits it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> claims;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private UserData createdBy;

    @Column(name = "created_timestamp", nullable = false)
    private LocalDateTime createdTimestamp;

    /**
     * Convert to a JSON object.
     */
    public TrustedPublisherJson toJson() {
        var json = new TrustedPublisherJson();
        json.setId(this.getId());
        json.setProvider(this.getProvider());
        json.setNamespace(this.getExtension().getNamespace().getName());
        json.setExtension(this.getExtension().getName());
        json.setRegistration(this.getRegistration());
        if (this.getCreatedTimestamp() != null) {
            json.setCreatedTimestamp(TimeUtil.toUTCString(this.getCreatedTimestamp()));
        }
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Extension getExtension() {
        return extension;
    }

    public void setExtension(Extension extension) {
        this.extension = extension;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, String> getRegistration() {
        return registration;
    }

    public void setRegistration(Map<String, String> registration) {
        this.registration = registration;
    }

    public Map<String, String> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, String> claims) {
        this.claims = claims;
    }

    public UserData getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserData createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TrustedPublisher that = (TrustedPublisher) o;
        return id == that.id
                && Objects.equals(extension, that.extension)
                && Objects.equals(provider, that.provider)
                && Objects.equals(claims, that.claims)
                && Objects.equals(createdBy, that.createdBy)
                && Objects.equals(createdTimestamp, that.createdTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, provider, claims, createdBy, createdTimestamp);
    }
}
