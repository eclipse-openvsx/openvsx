/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;

@Component
@Profile("test")
public class TestService {

    @Autowired
    EntityManager entityManager;

    @Transactional
    public void createUser() {
        var user = new UserData();
        user.setLoginName("test_user");
        entityManager.persist(user);
        var token = new PersonalAccessToken();
        token.setCreatedTimestamp(LocalDateTime.now());
        token.setActive(true);
        token.setUser(user);
        token.setValue("test_token");
        token.setType(PersonalAccessTokenType.LLT);
        entityManager.persist(token);
    }

}
