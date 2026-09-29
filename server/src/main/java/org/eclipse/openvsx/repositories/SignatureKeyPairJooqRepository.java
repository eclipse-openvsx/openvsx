/******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
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

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

import static org.eclipse.openvsx.jooq.Tables.*;
import static org.eclipse.openvsx.jooq.Tables.EXTENSION;

@Component
public class SignatureKeyPairJooqRepository {
    private final DSLContext dsl;

    public SignatureKeyPairJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public String findPublicId(String namespace, String extension, String targetPlatform, String version) {
        var query = dsl.selectQuery();
        query.addSelect(SIGNATURE_KEY_PAIR.PUBLIC_ID);
        query.addFrom(SIGNATURE_KEY_PAIR);
        query.addJoin(EXTENSION_VERSION, EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID.eq(SIGNATURE_KEY_PAIR.ID));
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(
                NAMESPACE.NAME.equalIgnoreCase(namespace),
                EXTENSION.NAME.equalIgnoreCase(extension),
                EXTENSION_VERSION.ACTIVE.eq(true));
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        if (!VersionAlias.LATEST.equals(version) && !onlyPreRelease) {
            query.addConditions(EXTENSION_VERSION.VERSION.eq(version));
        }
        if (TargetPlatform.isValid(targetPlatform)) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if (onlyPreRelease) {
            query.addConditions(EXTENSION_VERSION.PRE_RELEASE.eq(true));
        }

        query.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                EXTENSION_VERSION.TIMESTAMP.desc());
        query.addLimit(1);
        return query.fetchOne(SIGNATURE_KEY_PAIR.PUBLIC_ID);
    }
}
