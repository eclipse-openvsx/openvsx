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

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerErrorException;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.VersionTargetPlatformsJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.security.IdPrincipal;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.*;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.eclipse.openvsx.cache.CacheService.CACHE_NAMESPACE_DETAILS_JSON;

@Component
public class UserService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final CacheService cache;
    private final ExtensionValidator validator;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AttributesConfig attributesConfig;
    private final WebUiProperties webUi;

    public UserService(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil,
            CacheService cache,
            ExtensionValidator validator,
            @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AttributesConfig attributesConfig,
            WebUiProperties webUi
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.cache = cache;
        this.validator = validator;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.attributesConfig = attributesConfig;
        this.webUi = webUi;
    }

    public UserData findLoggedInUser() {
        if (!canLogin()) {
            return null;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof IdPrincipal principal) {
            return entityManager.find(UserData.class, principal.getId());
        }
        return null;
    }

    /**
     * Returns every target platform version of the given extension, each annotated with whether {@code user}
     * is allowed to delete it. This mirrors the authorization enforced when deleting a version: namespace
     * owners may delete any version, other members only the versions they published themselves.
     *
     * @param user the user the {@code canDelete} flags are computed for
     * @param extension the extension whose versions are listed
     * @param isOwner whether the user owns the extension's namespace, as already determined by the caller
     */
    public List<VersionTargetPlatformsJson> getVersionsWithDeletePermission(
            UserData user,
            Extension extension,
            boolean isOwner
    ) {
        var allVersions = repositories.findTargetPlatformsGroupedByVersion(extension);
        if (isOwner) {
            return allVersions.stream().map(version -> version.withCanDelete(true)).toList();
        }

        var publishedVersions = repositories.findTargetPlatformsGroupedByVersion(extension, user).stream()
                .map(VersionTargetPlatformsJson::version)
                .collect(Collectors.toSet());
        return allVersions.stream()
                .map(version -> version.withCanDelete(publishedVersions.contains(version.version())))
                .toList();
    }

    public boolean hasPublishPermission(UserData user, Namespace namespace) {
        if (UserData.Role.PRIVILEGED.equals(user.getRole())) {
            // Privileged users can publish to every namespace.
            return true;
        }

        return repositories.canPublishInNamespace(user, namespace);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson setNamespaceMember(
            UserData requestingUser,
            String namespaceName,
            String provider,
            String userLogin,
            String role
    ) {
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
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key = "#namespace.name")
    public ResultJson removeNamespaceMember(Namespace namespace, UserData user) throws ErrorResultException {
        var membership = repositories.findMembership(user, namespace);
        if (membership == null) {
            throw new ErrorResultException(
                    "User " + user.getLoginName() + " is not a member of " + namespace.getName() + ".");
        }
        return removeNamespaceMembership(membership);
    }

    /**
     * Removes a membership that the caller already holds, for callers walking a user's memberships rather
     * than naming one. Nothing here can fail on a row that has since gone or been duplicated, which matters
     * for {@code AdminService#revokePublisherContributions}: it revokes a whole publisher in one
     * transaction, and one unremovable membership must not take the token and version deactivations with it.
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key = "#membership.namespace.name")
    public ResultJson removeNamespaceMembership(NamespaceMembership membership) {
        var namespace = membership.getNamespace();
        var user = membership.getUser();
        entityManager.remove(membership);
        revokeTrustedPublishers(namespace, user);
        return ResultJson.success("Removed " + user.getLoginName() + " from namespace " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key = "#namespace.name")
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
            var wasOwner = NamespaceMembership.ROLE_OWNER.equals(membership.getRole());
            membership.setRole(role);
            if (wasOwner) {
                revokeTrustedPublishers(namespace, user);
            }
            return ResultJson.success(
                    "Changed role of " + user.getLoginName() + " in " + namespace.getName() + " to " + role + ".");
        }
        membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(role);
        entityManager.persist(membership);
        return ResultJson.success("Added " + user.getLoginName() + " as " + role + " of " + namespace.getName() + ".");
    }

    /**
     * Only a namespace owner may register a trusted publisher, so a registration must not outlive the ownership
     * it was made under: whoever stops being an owner - by demotion or by leaving the namespace - loses the
     * registrations they created there. Deleting them also invalidates the publishing tokens already issued
     * under them, which a mere loss of publishing rights would not.
     */
    private void revokeTrustedPublishers(Namespace namespace, UserData user) {
        var trustedPublishers = repositories.findTrustedPublishersByNamespaceAndCreatedBy(namespace, user).toList();
        trustedPublishers.forEach(repositories::deleteTrustedPublisher);
        if (!trustedPublishers.isEmpty()) {
            logger.info(
                    "Deleted {} trusted publisher(s) of {} in namespace {}, who is no longer an owner of it",
                    trustedPublishers.size(),
                    user.getLoginName(),
                    namespace.getName());
        }
    }

    @Transactional(rollbackOn = { ErrorResultException.class, NotFoundException.class })
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key = "#details.name")
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
                    : "Multiple issues were found in the extension metadata:\n"
                            + issues.stream().map(Object::toString).collect(Collectors.joining("\n"));

            throw new ErrorResultException(message);
        }

        if (!Objects.equals(details.getDisplayName(), namespace.getDisplayName())) {
            if (StringUtils.isNotEmpty(details.getDisplayName())) {
                var conflictingNamespaces = repositories.findConflictingNamespaces(details.getDisplayName(), namespace);
                if (!conflictingNamespaces.isEmpty()) {
                    // pick the first conflicting namespace
                    var conflictingNamespace = conflictingNamespaces.getFirst();
                    throw new ErrorResultException(
                            "Display name '" + details.getDisplayName()
                                    + "' collides with the name of existing namespace '"
                                    + conflictingNamespace.getName()
                                    + " (" + conflictingNamespace.getDisplayName() + ")"
                                    + "'. Please choose a different display name.");
                }
            }
            namespace.setDisplayName(details.getDisplayName());
        }
        if (!Objects.equals(details.getDescription(), namespace.getDescription())) {
            namespace.setDescription(details.getDescription());
        }
        if (!Objects.equals(details.getWebsite(), namespace.getWebsite())) {
            namespace.setWebsite(details.getWebsite());
        }
        if (!Objects.equals(details.getSupportLink(), namespace.getSupportLink())) {
            namespace.setSupportLink(details.getSupportLink());
        }
        if (!Objects.equals(details.getSocialLinks(), namespace.getSocialLinks())) {
            namespace.setSocialLinks(details.getSocialLinks());
        }
        if (StringUtils.isEmpty(details.getLogo()) && StringUtils.isNotEmpty(namespace.getLogoName())) {
            storageUtil.removeNamespaceLogo(namespace);
            namespace.setLogoName(null);
            namespace.setLogoStorageType(null);
        }

        return ResultJson.success("Updated details for namespace " + details.getName());
    }

    @Transactional
    @CacheEvict(value = { CACHE_NAMESPACE_DETAILS_JSON }, key = "#namespaceName")
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
            if (logoType == null || !expectedLogoTypes.contains(logoType.getType())) {
                throw new ErrorResultException("Namespace logo should be a png or jpg file");
            }

            namespace.setLogoName(NamingUtil.toLogoName(namespace, logoType));
            file.getInputStream().transferTo(out);
            logoFile.setNamespace(namespace);
            storageUtil.uploadNamespaceLogo(logoFile);
            if (StringUtils.isNotEmpty(oldNamespace.getLogoName())) {
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

    /**
     * Creates or updates a user after a successful OAuth2 dance.
     * <p>
     * The provided user must have an {code authId} being set.
     *
     * @param newUser the user data as mapped from the provided oauth2 attributes
     * @return a new persisted user object if no user with the same login is present, otherwise
     * an updated user object with information from the oauth2 attributes
     */
    @Transactional
    public UserData upsertUser(UserData newUser) {
        var userData = repositories.findUserByLoginName(newUser.getProvider(), newUser.getLoginName());
        if (userData == null) {
            entityManager.persist(newUser);
            userData = newUser;
        } else {
            // check that the existing auth ID matches the newly passed one to prevent account takeover attempts
            if (!userData.getAuthId().equals(newUser.getAuthId())) {
                logger.error(
                        "Login attempt by user '{}' with authId '{}' which does not match existing authId '{}'," +
                                "potential account takeover attempt, disallowing login",
                        newUser.getLoginName(),
                        newUser.getAuthId(),
                        userData.getAuthId());
                throw new AuthenticationServiceException(
                        "Could not login due to an existing user account with the same name. " +
                                "Please contact support for details.");
            }
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
        if (clientRegistrationRepository == null) {
            return Collections.emptyMap();
        }

        return attributesConfig.getProviders().stream()
                .filter(provider -> clientRegistrationRepository.findByRegistrationId(provider) != null)
                .map(
                        provider -> Map.entry(
                                provider,
                                UrlUtil.createApiUrl(
                                        UrlUtil.getBaseUrl(webUi.getApiUrl()),
                                        "oauth2",
                                        "authorization",
                                        provider)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
