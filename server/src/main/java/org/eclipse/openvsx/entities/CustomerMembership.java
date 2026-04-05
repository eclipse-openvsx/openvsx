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
import org.eclipse.openvsx.json.CustomerMembershipJson;
import org.eclipse.openvsx.json.NamespaceMembershipJson;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
public class CustomerMembership implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "customerMembershipSeq")
    @SequenceGenerator(name = "customerMembershipSeq", sequenceName = "customer_membership_seq", allocationSize = 1)
    private long id;

    @ManyToOne
    @JoinColumn(name = "customer")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "user_data")
    private UserData user;

    public CustomerMembershipJson toJson() {
        return new CustomerMembershipJson(
                this.customer.getName(),
                this.user.toUserJson()
        );
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

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerMembership that = (CustomerMembership) o;
        return id == that.id
                && Objects.equals(customer, that.customer)
                && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customer, user);
    }
}