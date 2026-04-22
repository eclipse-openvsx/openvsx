/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import com.google.common.base.Joiner;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.security.IdPrincipal;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.cache.CacheService.CACHE_NAMESPACE_DETAILS_JSON;

@Component
public class UserService {

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final CacheService cache;
    private final ExtensionValidator validator;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AttributesConfig attributesConfig;

    public UserService(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil,
            CacheService cache,
            ExtensionValidator validator,
            @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AttributesConfig attributesConfig
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.cache = cache;
        this.validator = validator;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.attributesConfig = attributesConfig;
    }

    public UserData findLoggedInUser() {
        if(!canLogin()) {
            return null;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof IdPrincipal principal) {
            return entityManager.find(UserData.class, principal.getId());
        }
        return null;
    }

    public boolean hasPublishPermission(UserData user, Namespace namespace) {
        if (UserData.ROLE_PRIVILEGED.equals(user.getRole())) {
            // Privileged users can publish to every namespace.
            return true;
        }

        return repositories.canPublishInNamespace(user, namespace);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson setNamespaceMember(UserData requestingUser, String namespaceName, String provider, String userLogin, String role) {
        var namespace = repositories.findNamespace(namespaceName);
        if (!repositories.isNamespaceOwner(requestingUser, namespace)) {
            throw new ErrorResultException("You must be an owner of this namespace.");
        }
        var targetUser = repositories.findUserByLoginName(provider, userLogin);
        if (targetUser == null) {
            throw new ErrorResultException("User not found: " + provider + "/" + userLogin);
        }

        if (role.equals("remove")) {
            return removeNamespaceMember(namespace, targetUser);
        } else {
            return addNamespaceMember(namespace, targetUser, role);
        }
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key="#namespace.name")
    public ResultJson removeNamespaceMember(Namespace namespace, UserData user) throws ErrorResultException {
        var membership = repositories.findMembership(user, namespace);
        if (membership == null) {
            throw new ErrorResultException("User " + user.getLoginName() + " is not a member of " + namespace.getName() + ".");
        }
        entityManager.remove(membership);
        return ResultJson.success("Removed " + user.getLoginName() + " from namespace " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key="#namespace.name")
    public ResultJson addNamespaceMember(Namespace namespace, UserData user, String role) {
        if (!(role.equals(NamespaceMembership.ROLE_OWNER)
                || role.equals(NamespaceMembership.ROLE_CONTRIBUTOR))) {
            throw new ErrorResultException("Invalid role: " + role);
        }
        var membership = repositories.findMembership(user, namespace);
        if (membership != null) {
            if (role.equals(membership.getRole())) {
                throw new ErrorResultException("User " + user.getLoginName() + " already has the role " + role + ".");
            }
            membership.setRole(role);
            return ResultJson.success("Changed role of " + user.getLoginName() + " in " + namespace.getName() + " to " + role + ".");
        }
        membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(role);
        entityManager.persist(membership);
        return ResultJson.success("Added " + user.getLoginName() + " as " + role + " of " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = { ErrorResultException.class, NotFoundException.class })
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key="#details.name")
    public ResultJson updateNamespaceDetails(NamespaceDetailsJson details, UserData user) {
        var namespace = repositories.findNamespace(details.getName());
        if (namespace == null) {
            throw new NotFoundException();
        }
        if (!repositories.isNamespaceOwner(user, namespace)) {
            throw new ErrorResultException("You must be an owner of this namespace.");
        }

        var issues = validator.validateNamespaceDetails(details);
        if (!issues.isEmpty()) {
            var message = issues.size() == 1
                    ? issues.getFirst().toString()
                    : "Multiple issues were found in the extension metadata:\n" + Joiner.on("\n").join(issues);

            throw new ErrorResultException(message);
        }

        if(!Objects.equals(details.getDisplayName(), namespace.getDisplayName())) {
            namespace.setDisplayName(details.getDisplayName());
        }
        if(!Objects.equals(details.getDescription(), namespace.getDescription())) {
            namespace.setDescription(details.getDescription());
        }
        if(!Objects.equals(details.getWebsite(), namespace.getWebsite())) {
            namespace.setWebsite(details.getWebsite());
        }
        if(!Objects.equals(details.getSupportLink(), namespace.getSupportLink())) {
            namespace.setSupportLink(details.getSupportLink());
        }
        if(!Objects.equals(details.getSocialLinks(), namespace.getSocialLinks())) {
            namespace.setSocialLinks(details.getSocialLinks());
        }
        if(StringUtils.isEmpty(details.getLogo()) && StringUtils.isNotEmpty(namespace.getLogoName())) {
            storageUtil.removeNamespaceLogo(namespace);
            namespace.setLogoName(null);
            namespace.setLogoStorageType(null);
        }

        return ResultJson.success("Updated details for namespace " + details.getName());
    }

    @Transactional
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key="#namespaceName")
    public ResultJson updateNamespaceDetailsLogo(String namespaceName, MultipartFile file, UserData user) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }
        if (!repositories.isNamespaceOwner(user, namespace)) {
            throw new ErrorResultException("You must be an owner of this namespace.");
        }

        var oldNamespace = SerializationUtils.clone(namespace);
        try (
                var logoFile = new TempFile("namespace-logo", ".png");
                var out = Files.newOutputStream(logoFile.getPath())
        ) {
            var tika = new Tika();
            var detectedType = tika.detect(file.getInputStream(), file.getOriginalFilename());
            var logoType = MimeTypes.getDefaultMimeTypes().getRegisteredMimeType(detectedType);
            var expectedLogoTypes = List.of(MediaType.image("png"), MediaType.image("jpg"));
            if(logoType == null || !expectedLogoTypes.contains(logoType.getType())) {
                throw new ErrorResultException("Namespace logo should be a png or jpg file");
            }

            namespace.setLogoName(NamingUtil.toLogoName(namespace, logoType));
            file.getInputStream().transferTo(out);
            logoFile.setNamespace(namespace);
            storageUtil.uploadNamespaceLogo(logoFile);
            if(StringUtils.isNotEmpty(oldNamespace.getLogoName())) {
                storageUtil.removeNamespaceLogo(oldNamespace);
            }
        } catch (IOException | MimeTypeException e) {
            throw new ServerErrorException("Failed to update namespace logo", e);
        }

        return ResultJson.success("Updated logo for namespace " + namespace.getName());
    }

    public boolean canLogin() {
        return !getLoginProviders().isEmpty();
    }

    @Transactional
    public UserData upsertUser(UserData newUser) {
        var userData = repositories.findUserByLoginName(newUser.getProvider(), newUser.getLoginName());
        if (userData == null) {
            entityManager.persist(newUser);
            userData = newUser;
        } else {
            var updated = false;
            if (!Strings.CS.equals(userData.getLoginName(), newUser.getLoginName())) {
                userData.setLoginName(newUser.getLoginName());
                updated = true;
            }
            if (!Strings.CS.equals(userData.getFullName(), newUser.getFullName())) {
                userData.setFullName(newUser.getFullName());
                updated = true;
            }
            if (!Strings.CS.equals(userData.getEmail(), newUser.getEmail())) {
                userData.setEmail(newUser.getEmail());
                updated = true;
            }
            if (!Strings.CS.equals(userData.getProviderUrl(), newUser.getProviderUrl())) {
                userData.setProviderUrl(newUser.getProviderUrl());
                updated = true;
            }
            if (!Strings.CS.equals(userData.getAvatarUrl(), newUser.getAvatarUrl())) {
                userData.setAvatarUrl(newUser.getAvatarUrl());
                updated = true;
            }
            if (updated) {
                cache.evictExtensionJsons(userData);
            }
        }

        return userData;
    }

    public Map<String, String> getLoginProviders() {
        if(clientRegistrationRepository == null) {
            return Collections.emptyMap();
        }

        return attributesConfig.getProviders().stream()
                .filter(provider -> clientRegistrationRepository.findByRegistrationId(provider) != null)
                .map(provider -> Map.entry(provider, UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), "oauth2", "authorization", provider)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
