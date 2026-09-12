/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Extension, Namespace, NamespaceMembership, UserData and PersonalAccessToken form bidirectional
 * JPA associations (e.g. Extension#namespace / Namespace#extensions). Their equals()/hashCode()
 * used to hash the full back-referencing object or collection on both sides, so computing one
 * side's hashCode recursed into the other side's hashCode without a base case, guaranteed to
 * throw StackOverflowError once both sides of the association were populated - exactly what
 * happens whenever a persistent entity is put into a HashSet/HashMap, e.g.
 * ExtensionService#reactivateExtensions and AdminService#revokePublisherContributions.
 */
class EntityEqualsHashCodeRecursionTest {

    @Test
    void extensionAndNamespaceHashCodeDoNotRecurse() {
        var namespace = new Namespace();
        namespace.setName("foo");

        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);

        namespace.setExtensions(List.of(extension));
        namespace.setMemberships(List.of());

        assertThatCode(extension::hashCode).doesNotThrowAnyException();
        assertThatCode(namespace::hashCode).doesNotThrowAnyException();
    }

    @Test
    void namespaceMembershipHashCodeDoesNotRecurse() {
        var namespace = new Namespace();
        namespace.setName("foo");

        var user = new UserData();
        user.setLoginName("someone");

        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(NamespaceMembership.ROLE_OWNER);

        namespace.setExtensions(List.of());
        namespace.setMemberships(List.of(membership));
        // UserData#tokens/#memberships have no setters - only Hibernate populates them, via reflection
        ReflectionTestUtils.setField(user, "tokens", List.of());
        ReflectionTestUtils.setField(user, "memberships", List.of(membership));

        assertThatCode(membership::hashCode).doesNotThrowAnyException();
        assertThatCode(namespace::hashCode).doesNotThrowAnyException();
        assertThatCode(user::hashCode).doesNotThrowAnyException();
    }

    @Test
    void personalAccessTokenAndUserDataHashCodeDoNotRecurse() {
        var user = new UserData();
        user.setLoginName("someone");

        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setType(PersonalAccessTokenType.LLT);

        ReflectionTestUtils.setField(user, "tokens", List.of(token));
        ReflectionTestUtils.setField(user, "memberships", List.of());

        assertThatCode(token::hashCode).doesNotThrowAnyException();
        assertThatCode(user::hashCode).doesNotThrowAnyException();
    }
}
