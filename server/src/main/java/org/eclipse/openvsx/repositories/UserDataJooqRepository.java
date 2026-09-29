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
package org.eclipse.openvsx.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.UserData;

import static org.eclipse.openvsx.jooq.Tables.USER_DATA;

@Component
public class UserDataJooqRepository {

    private final DSLContext dsl;

    public UserDataJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Page<UserData> findUsers(String search, String role, Pageable pageable) {
        var conditions = buildConditions(search, role);

        var total = dsl.selectCount()
                .from(USER_DATA)
                .where(conditions)
                .fetchOne(0, long.class);

        var query = baseQuery();
        query.addConditions(conditions);
        query.addOrderBy(DSL.lower(USER_DATA.LOGIN_NAME).asc());
        query.addOffset((int) pageable.getOffset());
        query.addLimit(pageable.getPageSize());
        var content = query.fetch(this::toUserData);

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    private SelectQuery<Record> baseQuery() {
        var query = dsl.selectQuery();
        query.addSelect(
                USER_DATA.ID,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER,
                USER_DATA.PROVIDER_URL,
                USER_DATA.ROLE);
        query.addFrom(USER_DATA);
        return query;
    }

    private List<Condition> buildConditions(String search, String role) {
        var conditions = new ArrayList<Condition>();
        conditions.add(USER_DATA.PROVIDER.isNotNull());

        if (StringUtils.isNotBlank(role)) {
            if ("none".equalsIgnoreCase(role)) {
                conditions.add(USER_DATA.ROLE.isNull());
            } else {
                conditions.add(USER_DATA.ROLE.equalIgnoreCase(role));
            }
        }

        if (StringUtils.isNotBlank(search)) {
            var like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            var searchCondition = DSL.lower(USER_DATA.LOGIN_NAME).like(like)
                    .or(DSL.lower(USER_DATA.FULL_NAME).like(like));

            conditions.add(searchCondition);
        }

        return conditions;
    }

    private UserData toUserData(Record row) {
        var user = new UserData();
        user.setId(row.get(USER_DATA.ID));
        user.setLoginName(row.get(USER_DATA.LOGIN_NAME));
        user.setFullName(row.get(USER_DATA.FULL_NAME));
        user.setAvatarUrl(row.get(USER_DATA.AVATAR_URL));
        user.setProvider(row.get(USER_DATA.PROVIDER));
        user.setProviderUrl(row.get(USER_DATA.PROVIDER_URL));
        user.setRole(UserData.Role.valueOfIgnoreCase(row.get(USER_DATA.ROLE)));
        return user;
    }
}
