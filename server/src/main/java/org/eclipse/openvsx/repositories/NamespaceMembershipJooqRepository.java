/*******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
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
package org.eclipse.openvsx.repositories;

import java.util.Collection;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;

import static org.eclipse.openvsx.jooq.Tables.*;

@Component
public class NamespaceMembershipJooqRepository {

    private final DSLContext dsl;

    public NamespaceMembershipJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<NamespaceMembership> findAllByNamespaceId(Collection<Long> namespaceIds) {
        return dsl.select(
                NAMESPACE_MEMBERSHIP.ID,
                NAMESPACE_MEMBERSHIP.ROLE,
                NAMESPACE_MEMBERSHIP.NAMESPACE,
                NAMESPACE_MEMBERSHIP.USER_DATA)
                .from(NAMESPACE_MEMBERSHIP)
                .where(NAMESPACE_MEMBERSHIP.NAMESPACE.in(namespaceIds))
                .fetch(row -> {
                    var namespaceMembership = new NamespaceMembership();
                    namespaceMembership.setId(row.get(NAMESPACE_MEMBERSHIP.ID));
                    namespaceMembership.setRole(row.get(NAMESPACE_MEMBERSHIP.ROLE));

                    var namespace = new Namespace();
                    namespace.setId(row.get(NAMESPACE_MEMBERSHIP.NAMESPACE));
                    namespaceMembership.setNamespace(namespace);

                    var user = new UserData();
                    user.setId(row.get(NAMESPACE_MEMBERSHIP.USER_DATA));
                    namespaceMembership.setUser(user);

                    return namespaceMembership;
                });
    }

    public boolean isVerified(Namespace namespace, UserData user) {
        var nm = NAMESPACE_MEMBERSHIP.as("nm");
        var onm = NAMESPACE_MEMBERSHIP.as("onm");
        var result = dsl.selectOne()
                .from(nm)
                .join(onm).on(onm.NAMESPACE.eq(nm.NAMESPACE))
                .where(onm.NAMESPACE.eq(namespace.getId()))
                .and(onm.ROLE.eq(NamespaceMembership.ROLE_OWNER))
                .and(nm.USER_DATA.eq(user.getId()))
                .fetch();

        return result.isNotEmpty();
    }

    public boolean hasRole(Namespace namespace, String role) {
        return dsl.fetchExists(
                dsl.selectOne().from(NAMESPACE_MEMBERSHIP)
                        .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(namespace.getId()))
                        .and(NAMESPACE_MEMBERSHIP.ROLE.equalIgnoreCase(role)));
    }

    public boolean isOwner(UserData user, Namespace namespace) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(NAMESPACE_MEMBERSHIP)
                        .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(namespace.getId()))
                        .and(NAMESPACE_MEMBERSHIP.USER_DATA.eq(user.getId()))
                        .and(NAMESPACE_MEMBERSHIP.ROLE.eq(NamespaceMembership.ROLE_OWNER)));
    }

    public List<NamespaceMembership> findByNamespaceName(String namespaceName) {
        var query = findMemberships();
        query.addConditions(NAMESPACE.NAME.equalIgnoreCase(namespaceName));
        return query.fetch(this::toNamespaceMembership);
    }

    public List<NamespaceMembership> findMembershipsForOwner(UserData owner, String namespaceName) {
        var namespaceOwnerQuery = dsl.select(NAMESPACE.ID)
                .from(NAMESPACE)
                .join(NAMESPACE_MEMBERSHIP).on(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(NAMESPACE.ID))
                .where(NAMESPACE.NAME.equalIgnoreCase(namespaceName))
                .and(NAMESPACE_MEMBERSHIP.USER_DATA.eq(owner.getId()))
                .and(NAMESPACE_MEMBERSHIP.ROLE.eq(NamespaceMembership.ROLE_OWNER));

        var query = findMemberships();
        query.addConditions(NAMESPACE.ID.eq(namespaceOwnerQuery));
        return query.fetch(this::toNamespaceMembership);
    }

    private SelectQuery<Record> findMemberships() {
        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE_MEMBERSHIP.ID,
                NAMESPACE_MEMBERSHIP.ROLE,
                USER_DATA.ID,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER);
        query.addFrom(NAMESPACE_MEMBERSHIP);
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(NAMESPACE_MEMBERSHIP.NAMESPACE));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(NAMESPACE_MEMBERSHIP.USER_DATA));
        return query;
    }

    private NamespaceMembership toNamespaceMembership(Record row) {
        var namespace = new Namespace();
        namespace.setId(row.get(NAMESPACE.ID));
        namespace.setName(row.get(NAMESPACE.NAME));

        var user = new UserData();
        user.setId(row.get(USER_DATA.ID));
        user.setLoginName(row.get(USER_DATA.LOGIN_NAME));
        user.setFullName(row.get(USER_DATA.FULL_NAME));
        user.setAvatarUrl(row.get(USER_DATA.AVATAR_URL));
        user.setProvider(row.get(USER_DATA.PROVIDER));
        user.setProviderUrl(row.get(USER_DATA.PROVIDER_URL));

        var membership = new NamespaceMembership();
        membership.setId(row.get(NAMESPACE_MEMBERSHIP.ID));
        membership.setRole(row.get(NAMESPACE_MEMBERSHIP.ROLE));
        membership.setNamespace(namespace);
        membership.setUser(user);

        return membership;
    }

    public boolean canPublish(UserData user, Namespace namespace) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(NAMESPACE_MEMBERSHIP)
                        .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(namespace.getId()))
                        .and(NAMESPACE_MEMBERSHIP.USER_DATA.eq(user.getId()))
                        .and(
                                NAMESPACE_MEMBERSHIP.ROLE.equalIgnoreCase(NamespaceMembership.ROLE_CONTRIBUTOR)
                                        .or(
                                                NAMESPACE_MEMBERSHIP.ROLE
                                                        .equalIgnoreCase(NamespaceMembership.ROLE_OWNER))));
    }

    public boolean hasMembership(UserData user, Namespace namespace) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(NAMESPACE_MEMBERSHIP)
                        .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(namespace.getId()))
                        .and(NAMESPACE_MEMBERSHIP.USER_DATA.eq(user.getId())));
    }
}
