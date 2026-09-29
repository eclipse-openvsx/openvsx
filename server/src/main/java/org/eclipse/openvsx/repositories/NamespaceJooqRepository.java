/******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
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

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Row2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Namespace;

import static org.eclipse.openvsx.jooq.Tables.NAMESPACE;
import static org.eclipse.openvsx.jooq.Tables.NAMESPACE_MEMBERSHIP;

@Component
public class NamespaceJooqRepository {

    private final DSLContext dsl;

    public NamespaceJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void updatePublicIds(Map<Long, String> publicIds) {
        if (publicIds.isEmpty()) {
            return;
        }

        var namespace = NAMESPACE.as("n");
        var rows = publicIds.entrySet().stream()
                .map(e -> DSL.row(e.getKey(), e.getValue()))
                .toList();

        var updates = DSL.values(rows.toArray(Row2[]::new)).as("u", "id", "public_id");
        dsl.update(namespace)
                .set(namespace.PUBLIC_ID, updates.field("public_id", String.class))
                .from(updates)
                .where(namespace.ID.eq(updates.field("id", Long.class)))
                .execute();
    }

    public boolean publicIdExists(String publicId) {
        return dsl.selectOne()
                .from(NAMESPACE)
                .where(NAMESPACE.PUBLIC_ID.eq(publicId))
                .fetch()
                .isNotEmpty();
    }

    public String findNameByNameIgnoreCase(String name) {
        return dsl.select(NAMESPACE.NAME)
                .from(NAMESPACE)
                .where(NAMESPACE.NAME.equalIgnoreCase(name))
                .fetchOne(NAMESPACE.NAME);
    }

    public boolean exists(String name) {
        return dsl.fetchExists(dsl.selectOne().from(NAMESPACE).where(NAMESPACE.NAME.equalIgnoreCase(name)));
    }

    /**
     * Find namespaces with names similar to the given name using PostgreSQL's Levenshtein distance.
     * This checks if any existing namespace names are too similar to the proposed name.
     */
    public List<Namespace> findSimilarNamespacesByLevenshtein(
            String namespaceName,
            List<String> excludeNamespaces,
            double levenshteinThreshold,
            boolean verifiedOnly,
            int limit
    ) {
        var query = dsl.selectQuery();

        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME);

        query.addFrom(NAMESPACE);

        if (excludeNamespaces != null && !excludeNamespaces.isEmpty()) {
            query.addConditions(NAMESPACE.NAME.notIn(excludeNamespaces));
        }

        if (verifiedOnly) {
            var hasOwnerSubquery = DSL.selectOne()
                    .from(NAMESPACE_MEMBERSHIP)
                    .where(NAMESPACE_MEMBERSHIP.NAMESPACE.eq(NAMESPACE.ID))
                    .and(NAMESPACE_MEMBERSHIP.ROLE.eq("owner"));

            query.addConditions(DSL.exists(hasOwnerSubquery));
        }

        var lowerNamespaceName = namespaceName.toLowerCase();

        int inputLen = namespaceName.length();
        int minLen = (int) Math.floor(inputLen * (1.0 - levenshteinThreshold));
        int maxLen = (int) Math.ceil(inputLen / (1.0 - levenshteinThreshold));

        query.addConditions(DSL.length(NAMESPACE.NAME).between(minLen, maxLen));

        var maxLength = DSL.greatest(
                DSL.val(lowerNamespaceName.length()),
                DSL.length(NAMESPACE.NAME));
        var maxDistance = maxLength.mul(levenshteinThreshold);

        var levenshteinDist = DSL.function(
                "levenshtein_less_equal",
                Integer.class,
                DSL.val(lowerNamespaceName),
                DSL.lower(NAMESPACE.NAME),
                DSL.val(1), // insertion cost
                DSL.val(1), // deletion cost
                DSL.val(1), // substitution cost
                maxDistance.cast(Integer.class));

        query.addConditions(levenshteinDist.le(maxDistance));

        // Order by best match first (lowest Levenshtein distance)
        query.addOrderBy(levenshteinDist.asc());
        query.addLimit(limit);

        // Map results to Namespace entities
        return query.fetch().map(record -> {
            var namespace = new Namespace();
            namespace.setId(record.get(NAMESPACE.ID));
            namespace.setPublicId(record.get(NAMESPACE.PUBLIC_ID));
            namespace.setName(record.get(NAMESPACE.NAME));
            namespace.setDisplayName(record.get(NAMESPACE.DISPLAY_NAME));
            return namespace;
        });
    }
}
