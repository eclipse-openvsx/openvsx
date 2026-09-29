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
package org.eclipse.openvsx.security;

import java.util.Collection;

import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.eclipse.EclipseTokenService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.ErrorResultException;

import static java.util.Collections.emptyList;
import static org.eclipse.openvsx.security.CodedAuthException.*;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

@Service
public class OAuth2UserServices {

    private final UserService users;
    private final EclipseTokenService tokens;
    private final EntityManager entityManager;
    private final EclipseService eclipse;
    private final OAuth2AttributesConfig attributesConfig;
    private final DefaultOAuth2UserService springOAuth2UserService;
    private final OidcUserService springOidcUserService;

    public OAuth2UserServices(
            UserService users,
            EclipseTokenService tokens,
            EntityManager entityManager,
            EclipseService eclipse,
            OAuth2AttributesConfig attributesConfig
    ) {
        this.users = users;
        this.tokens = tokens;
        this.entityManager = entityManager;
        this.eclipse = eclipse;
        this.attributesConfig = attributesConfig;
        springOAuth2UserService = new DefaultOAuth2UserService();
        springOidcUserService = new OidcUserService();
    }

    public OAuth2UserService<OAuth2UserRequest, OAuth2User> getOauth2() {
        return this::loadUser;
    }
    public OAuth2UserService<OidcUserRequest, OidcUser> getOidc() {
        return this::loadUser;
    }

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        // We can assume that `UserData` already exists, because this event is fired after
        // `ExtendedOAuth2UserServices.loadUser` was processed.
        if (event.getSource() instanceof OAuth2LoginAuthenticationToken) {
            var auth = (OAuth2LoginAuthenticationToken) event.getSource();
            var registrationId = auth.getClientRegistration().getRegistrationId();
            if (registrationId.equals("eclipse")) {
                var idPrincipal = (IdPrincipal) auth.getPrincipal();
                tokens.updateEclipseToken(idPrincipal.getId(), auth.getAccessToken(), auth.getRefreshToken());
            }
        }
    }

    public IdPrincipal loadUser(OAuth2UserRequest userRequest) {
        return switch (userRequest.getClientRegistration().getRegistrationId()) {
            case "eclipse" -> loadEclipseUser(userRequest);
            default -> loadGenericUser(userRequest);
        };
    }

    public boolean canLogin() {
        return users.canLogin();
    }

    private IdPrincipal loadGenericUser(OAuth2UserRequest userRequest) {
        var registrationId = userRequest.getClientRegistration().getRegistrationId();
        var mapping = attributesConfig.getAttributeMapping(registrationId);
        if (mapping == null) {
            throw new CodedAuthException("Unsupported registration: " + registrationId, UNSUPPORTED_REGISTRATION);
        }

        var oauth2User = userRequest instanceof OidcUserRequest oidcRequest
                ? springOidcUserService.loadUser(oidcRequest)
                : springOAuth2UserService.loadUser(userRequest);

        var userAttributes = mapping.toUserData(registrationId, oauth2User);
        if (StringUtils.isEmpty(userAttributes.getLoginName())) {
            throw new CodedAuthException("Invalid login: missing 'login' field.", INVALID_USER);
        }

        var userData = users.upsertUser(userAttributes);
        return new IdPrincipal(userData.getId(), userData.getAuthId(), getAuthorities(userData));
    }

    private IdPrincipal loadEclipseUser(OAuth2UserRequest userRequest) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new CodedAuthException(
                    "Please log in with GitHub before connecting your Eclipse account.",
                    NEED_MAIN_LOGIN);
        }
        if (!(authentication.getPrincipal() instanceof IdPrincipal)) {
            throw new CodedAuthException("The current authentication is invalid.", NEED_MAIN_LOGIN);
        }
        var principal = (IdPrincipal) authentication.getPrincipal();
        var userData = entityManager.find(UserData.class, principal.getId());
        if (userData == null) {
            throw new CodedAuthException("The current authentication has no backing data.", NEED_MAIN_LOGIN);
        }
        try {
            var accessToken = userRequest.getAccessToken().getTokenValue();
            var profile = eclipse.getUserProfile(accessToken);
            if (StringUtils.isEmpty(profile.getGithubHandle())) {
                throw new CodedAuthException(
                        "Your Eclipse profile is missing a GitHub username.",
                        ECLIPSE_MISSING_GITHUB_ID);
            }
            if (!profile.getGithubHandle().equalsIgnoreCase(userData.getLoginName())) {
                throw new CodedAuthException(
                        "The GitHub username setting in your Eclipse profile ("
                                + profile.getGithubHandle()
                                + ") does not match your GitHub authentication ("
                                + userData.getLoginName() + ").",
                        ECLIPSE_MISMATCH_GITHUB_ID);
            }

            eclipse.updateUserData(userData, profile);
            return principal;
        } catch (ErrorResultException exc) {
            throw new AuthenticationServiceException(exc.getMessage(), exc);
        }
    }

    private Collection<GrantedAuthority> getAuthorities(UserData userData) {
        var role = userData.getRole();
        if (role == null) {
            return emptyList();
        }
        return switch (role) {
            case ADMIN -> createAuthorityList("ROLE_ADMIN");
            case PRIVILEGED -> createAuthorityList("ROLE_PRIVILEGED");
        };
    }
}
