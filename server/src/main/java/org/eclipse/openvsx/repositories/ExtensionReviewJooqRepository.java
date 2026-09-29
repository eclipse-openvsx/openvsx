/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.repositories;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.UserData;

import static org.eclipse.openvsx.jooq.tables.ExtensionReview.EXTENSION_REVIEW;

@Component
public class ExtensionReviewJooqRepository {

    private final DSLContext dsl;

    public ExtensionReviewJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean hasActiveReview(Extension extension, UserData user) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(EXTENSION_REVIEW)
                        .where(EXTENSION_REVIEW.EXTENSION_ID.eq(extension.getId()))
                        .and(EXTENSION_REVIEW.USER_ID.eq(user.getId()))
                        .and(EXTENSION_REVIEW.ACTIVE.eq(true)));
    }
}
