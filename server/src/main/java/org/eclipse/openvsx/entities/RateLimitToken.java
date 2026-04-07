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

import jakarta.persistence.*;
import org.eclipse.openvsx.json.RateLimitTokenJson;
import org.eclipse.openvsx.util.TimeUtil;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "rate_limit_token")
public class RateLimitToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "rateLimitTokenSeq")
    @SequenceGenerator(name = "rateLimitTokenSeq", sequenceName = "rate_limit_token_seq", allocationSize = 1)
    private long id;

    @ManyToOne
    @JoinColumn(name = "customer")
    private Customer customer;

    @Column(length = 64)
    private String value;

    private boolean active;

    private LocalDateTime createdTimestamp;

    @Column(length = 2048)
    private String description;

    /**
     * Convert to a JSON object.
     */
    public RateLimitTokenJson toJson() {
        var json = new RateLimitTokenJson();
        json.setId(this.getId());
        // The value is not included: it is displayed only when the token is created
        if (this.getCreatedTimestamp() != null) {
            json.setCreatedTimestamp(TimeUtil.toUTCString(this.getCreatedTimestamp()));
        }
        json.setDescription(this.getDescription());
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
		this.id = id;
	}

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitToken that = (RateLimitToken) o;
        return id == that.id
                && active == that.active
                && Objects.equals(customer, that.customer)
                && Objects.equals(value, that.value)
                && Objects.equals(createdTimestamp, that.createdTimestamp)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customer, value, active, createdTimestamp, description);
    }
}