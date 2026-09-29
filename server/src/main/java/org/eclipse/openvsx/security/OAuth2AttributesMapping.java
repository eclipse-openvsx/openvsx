/********************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.security;

import org.springframework.security.oauth2.core.user.OAuth2User;

import org.eclipse.openvsx.entities.UserData;

public record OAuth2AttributesMapping(
        String avatarUrl,
        String email,
        String fullName,
        String loginName,
        String providerUrl
) {

    /**
     * @param provider The configured OAuth2 provider from which the user object came from.
     * @param oauth2User The OAuth2 user object to get attributes from.
     * @return A {@link UserData} instance with attributes set according to the current configuration.
     */
    public UserData toUserData(String provider, OAuth2User oauth2User) {
        var userData = new UserData();
        userData.setAuthId(oauth2User.getName());
        userData.setProvider(provider);
        userData.setAvatarUrl(getAttribute(oauth2User, avatarUrl));
        userData.setEmail(getAttribute(oauth2User, email));
        userData.setFullName(getAttribute(oauth2User, fullName));
        userData.setLoginName(getAttribute(oauth2User, loginName));
        userData.setProviderUrl(getAttribute(oauth2User, providerUrl));
        return userData;
    }

    private <T> T getAttribute(OAuth2User oauth2User, String attribute) {
        return attribute == null ? null : oauth2User.getAttribute(attribute);
    }
}
