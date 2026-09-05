/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.ScanStatus;
import org.eclipse.openvsx.entities.UsageStats;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.CsrfTokenJson;
import org.eclipse.openvsx.json.CustomerJson;
import org.eclipse.openvsx.json.ErrorJson;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.LoginProvidersJson;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.NamespaceMembershipListJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.TargetPlatformVersionJson;
import org.eclipse.openvsx.json.UsageStatsListJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.NamespaceOwnershipCheckScanner;
import org.eclipse.openvsx.security.CodedAuthException;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;

import static org.eclipse.openvsx.entities.FileResource.CHANGELOG;
import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD;
import static org.eclipse.openvsx.entities.FileResource.ICON;
import static org.eclipse.openvsx.entities.FileResource.LICENSE;
import static org.eclipse.openvsx.entities.FileResource.MANIFEST;
import static org.eclipse.openvsx.entities.FileResource.README;
import static org.eclipse.openvsx.entities.FileResource.VSIXMANIFEST;
import static org.eclipse.openvsx.util.UrlUtil.createApiUrl;

@RestController
public class UserAPI {

    private static final int TOKEN_DESCRIPTION_SIZE = 255;

    protected final Logger logger = LoggerFactory.getLogger(UserAPI.class);

    private final RepositoryService repositories;
    private final UserService users;
    private final AccessTokenService tokens;
    private final EclipseService eclipse;
    private final StorageUtilService storageUtil;
    private final LocalRegistryService local;
    private final ExtensionService extensions;

    public UserAPI(
            RepositoryService repositories,
            UserService users,
            AccessTokenService tokens,
            EclipseService eclipse,
            StorageUtilService storageUtil,
            LocalRegistryService local,
            ExtensionService extensions
    ) {
        this.repositories = repositories;
        this.users = users;
        this.tokens = tokens;
        this.eclipse = eclipse;
        this.storageUtil = storageUtil;
        this.local = local;
        this.extensions = extensions;
    }

    @GetMapping(
        path = "/login-providers"
    )
    public ResponseEntity<LoginProvidersJson> login() {
        var json = new LoginProvidersJson();
        var providers = users.getLoginProviders();
        if (!providers.isEmpty()) {
            json.setLoginProviders(providers);
        } else {
            json.setSuccess("No login providers available.");
        }

        return ResponseEntity.ok(json);
    }

    /**
     * Retrieve the last authentication error and return its details.
     */
    @GetMapping(
        path = "/user/auth-error",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ErrorJson getAuthError(HttpServletRequest request) {
        var authException = users.canLogin()
                ? request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)
                : null;
        if (!(authException instanceof AuthenticationException)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var code = authException instanceof CodedAuthException ? ((CodedAuthException) authException).getCode() : null;
        return new ErrorJson(((AuthenticationException) authException).getMessage(), code);
    }

    /**
     * This endpoint is used to check whether there is a logged-in user. For this reason, it does not return a 403
     * status, but an OK status with JSON body when no user data is available. This is to avoid unnecessary network
     * error logging in the browser console.
     */
    @GetMapping(
        path = "/user",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public UserJson getUserData() {
        var user = users.findLoggedInUser();
        if (user == null) {
            return UserJson.error("Not logged in.");
        }
        var json = user.toUserJson();
        var serverUrl = UrlUtil.getBaseUrl();
        json.setRole(user.getRoleAsString());
        json.setTokensUrl(createApiUrl(serverUrl, "user", "tokens"));
        json.setCreateTokenUrl(createApiUrl(serverUrl, "user", "token", "create"));
        eclipse.enrichUserJsonWithPublisherAgreement(json, user);
        return json;
    }

    @GetMapping(
        path = "/user/csrf",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CsrfTokenJson getCsrfToken(HttpServletRequest request) {
        var csrfToken = (CsrfToken) request.getAttribute("_csrf");
        return csrfToken != null
                ? new CsrfTokenJson(csrfToken.getToken(), csrfToken.getHeaderName())
                : CsrfTokenJson.error("Token is not available.");
    }

    @GetMapping(
        path = "/user/tokens",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<AccessTokenJson> getAccessTokens() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var serverUrl = UrlUtil.getBaseUrl();
        return repositories.findActivePersonalAccessTokensAndType(user, PersonalAccessTokenType.LLT)
                .map(token -> {
                    var json = token.toAccessTokenJson();
                    json.setDeleteTokenUrl(
                            createApiUrl(serverUrl, "user", "token", "delete", Long.toString(token.getId())));
                    return json;
                })
                .toList();
    }

    @PostMapping(
        path = "/user/token/create",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<AccessTokenJson> createAccessToken(@RequestParam(required = false) String description) {
        if (description != null && description.length() > TOKEN_DESCRIPTION_SIZE) {
            var json = AccessTokenJson
                    .error("The description must not be longer than " + TOKEN_DESCRIPTION_SIZE + " characters.");
            return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
        }
        var user = users.findLoggedInUser();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return new ResponseEntity<>(tokens.createLongLivedAccessToken(user, description), HttpStatus.CREATED);
    }

    @PostMapping(
        path = "/user/token/delete/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> deleteAccessToken(@PathVariable long id) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            return ResponseEntity.ok(tokens.deactivateAccessToken(user, id));
        } catch (NotFoundException e) {
            return new ResponseEntity<>(ResultJson.error("Token does not exist."), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Lists the extensions shown in the authenticated user's settings view.
     * <p>
     * Only extensions the user published <em>and</em> whose namespace the user is <em>currently</em>
     * a member of are returned. Extensions the user published in a namespace they have since left
     * (or been removed from) are excluded, since the user no longer has any access to them (see
     * {@link #getOwnExtension}). The list includes inactive and removed (soft-deleted) versions of
     * the extensions that do qualify.
     *
     * @return {@code 200 OK} with the list of extensions, or {@code 403 Forbidden} if not logged in
     */
    @GetMapping(
        path = "/user/extensions",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<ExtensionJson> getOwnExtensions() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Restrict to namespaces the user is currently a member of: a user who left a namespace must
        // no longer see extensions they published there, even though they remain the publisher.
        var memberNamespaceIds = repositories.findMemberships(user).stream()
                .map(membership -> membership.getNamespace().getId())
                .collect(Collectors.toSet());
        var extVersions = repositories.findLatestVersions(user).stream()
                .filter(ev -> memberNamespaceIds.contains(ev.getExtension().getNamespace().getId()))
                .toList();

        var types = new String[] { DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST };
        var fileUrls = storageUtil.getFileUrls(extVersions, UrlUtil.getBaseUrl(), types);
        return extVersions.stream()
                .map(latest -> {
                    var json = latest.toExtensionJson();
                    json.setPreview(latest.isPreview());
                    json.setActive(latest.getExtension().isActive());
                    json.setRemoved(latest.isExtensionRemoved());
                    json.setVerified(repositories.isVerifiedPublisher(latest));
                    json.setFiles(fileUrls.get(latest.getId()));

                    // Add scan/review status information
                    enrichWithReviewStatus(json, latest);

                    return json;
                })
                .toList();
    }

    /**
     * Looks up the most recent scan recorded for {@code extVersion}, or {@code null} if none exists
     * (scanning disabled, or the version predates the scanning feature).
     */
    private ExtensionScan findLatestScan(ExtensionVersion extVersion) {
        return repositories.findLatestExtensionScan(extVersion);
    }

    /**
     * Whether {@code scanResult} recorded a threat from the {@link NamespaceOwnershipCheckScanner}:
     * the version's namespace already exists in a referenced external gallery and needs to be verified
     * (claimed) before the version can be activated here.
     */
    private boolean hasNamespaceOwnershipConflict(ExtensionScan scanResult) {
        return scanResult != null
                && repositories.findExtensionThreats(scanResult, NamespaceOwnershipCheckScanner.TYPE)
                        .stream()
                        .findAny()
                        .isPresent();
    }

    /**
     * Add review/scan status information to the extension JSON.
     * <p>
     * This shows users the current state of their extension in simple terms:
     * <ul>
     *   <li>"published" - Extension is active and publicly available</li>
     *   <li>"under_review" - Extension is being reviewed (validation, scanning, etc.)</li>
     *   <li>"rejected" - Extension was blocked (quarantined or rejected)</li>
     * </ul>
     */
    private void enrichWithReviewStatus(ExtensionJson json, ExtensionVersion extVersion) {
        var scanResult = findLatestScan(extVersion);
        json.setNamespaceOwnershipConflict(hasNamespaceOwnershipConflict(scanResult));

        if (Boolean.TRUE.equals(json.getActive())) {
            // Only mark published if scan result indicates PASSED or no scan result exists (scanning disabled / manual
            // activation)
            if (scanResult == null || scanResult.getStatus() == ScanStatus.PASSED) {
                json.setReviewStatus("published");
                return;
            }
        }

        if (extVersion.isRemoved()) {
            return;
        }

        // A version whose publish never finished is not waiting on anything, whatever the scan says, so
        // this is reported ahead of every state below: the work that would have activated it stopped, and
        // a scan that was never reached leaves the row looking exactly like one still queued. "Rejected"
        // is the closest of the three statuses this vocabulary has - the version will not become live
        // without someone intervening - where "under review" would promise attention nothing is giving it.
        // The recorded reason itself stays out of the response: it names server internals, and there is
        // nothing in it the publisher could act on.
        if (extVersion.getPublishError() != null) {
            json.setReviewStatus("rejected");
            json.setReviewMessage(
                    "Publishing this version did not complete. Please contact the registry operator.");
            return;
        }

        if (scanResult == null) {
            // No scan result found - show as under review
            json.setReviewStatus("under_review");
            json.setReviewMessage("Your extension is being reviewed.");
            return;
        }

        // Map internal status to simple user-facing status
        switch (scanResult.getStatus()) {
            case STARTED:
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension is being reviewed.");
                break;
            case VALIDATING:
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension is being reviewed.");
                break;
            case SCANNING:
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension is being reviewed.");
                break;
            case QUARANTINED:
                // Check if admin has made a decision on this quarantined scan
                var adminDecision = repositories.findAdminScanDecisionByScanId(scanResult.getId());
                if (adminDecision != null) {
                    if (adminDecision.isAllowed()) {
                        // Admin allowed the extension - show as published if active
                        if (Boolean.TRUE.equals(json.getActive())) {
                            json.setReviewStatus("published");
                        } else {
                            // Allowed but not yet active (edge case)
                            json.setReviewStatus("under_review");
                            json.setReviewMessage("Your extension has been approved and will be published shortly.");
                        }
                    } else {
                        // Admin blocked the extension
                        json.setReviewStatus("under_review");
                        json.setReviewMessage("Your extension is being reviewed. Please contact support for details.");
                    }
                } else {
                    // No admin decision yet - still under review
                    json.setReviewStatus("under_review");
                    if (scanResult.getErrorMessage() != null) {
                        json.setReviewMessage(scanResult.getErrorMessage());
                    } else {
                        json.setReviewMessage("Your extension is being reviewed. Please contact support for details.");
                    }
                }
                break;
            case REJECTED:
                json.setReviewStatus("rejected");
                if (scanResult.getErrorMessage() != null) {
                    json.setReviewMessage(scanResult.getErrorMessage());
                } else {
                    json.setReviewMessage("Your extension could not be published. Please contact support for details.");
                }
                break;
            case ERRORED:
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension could not be published. Please contact support for details.");
                break;
            default :
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension is being reviewed. Please contact support for details.");
        }
    }

    /**
     * Returns an extension for the authenticated user's settings view, including every version's
     * target platforms and, per version, whether the caller may delete it.
     * <p>
     * Access is restricted to <em>current</em> namespace members: a member (owner or not) sees
     * <em>all</em> versions of the extension, including versions they did not publish themselves and
     * removed (soft-deleted) ones. A user who is not a member of the namespace has no access and
     * receives {@code 404 Not Found}, even for versions they published while they were still a member.
     * <p>
     * Each returned version carries a {@code canDelete} flag mirroring the authorization enforced by
     * {@link #deleteExtension}: owners may delete any version, other members only the versions they
     * published themselves. This lets the settings UI disable delete controls the caller is not
     * allowed to use.
     *
     * @param namespaceName the namespace of the extension
     * @param extensionName the extension name
     * @return {@code 200 OK} with the extension, {@code 403 Forbidden} if not logged in, or
     *         {@code 404 Not Found} if the caller is not a namespace member or the extension does
     *         not exist
     */
    @GetMapping(
        path = "/user/extension/{namespaceName}/{extensionName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExtensionJson> getOwnExtension(
            @PathVariable String namespaceName,
            @PathVariable String extensionName
    ) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            var namespace = repositories.findNamespace(namespaceName);
            // Only current namespace members may inspect an extension here. A user who left the
            // namespace (or was never a member) has no access, even to versions they published
            // themselves. Members see every version, including ones they did not publish.
            var isOwner = namespace != null && repositories.isNamespaceOwner(user, namespace);
            var isMember = isOwner || (namespace != null && repositories.hasMembership(user, namespace));
            if (!isMember) {
                var error = "Extension not found: " + NamingUtil.toExtensionId(namespaceName, extensionName);
                throw new ErrorResultException(error, HttpStatus.NOT_FOUND);
            }

            var latest = repositories.findLatestVersion(namespaceName, extensionName, null, false, false);

            ExtensionJson json;
            if (latest != null) {
                json = local.toExtensionVersionJson(latest, null, false);
                var extension = latest.getExtension();
                // Each version is annotated with whether the caller may delete it, mirroring deleteExtension:
                // owners may delete any version, other members only the versions they published themselves.
                json.setAllTargetPlatformVersions(users.getVersionsWithDeletePermission(user, extension, isOwner));
                json.setActive(extension.isActive());
                json.setRemoved(latest.isExtensionRemoved());
                json.setNamespaceOwnershipConflict(hasNamespaceOwnershipConflict(findLatestScan(latest)));
            } else {
                var error = "Extension not found: " + NamingUtil.toExtensionId(namespaceName, extensionName);
                throw new ErrorResultException(error, HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ExtensionJson.class);
        }
    }

    @PostMapping(
        path = "/user/extension/{namespaceName}/{extensionName}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> deleteExtension(
            @PathVariable String namespaceName,
            @PathVariable String extensionName,
            @RequestBody List<TargetPlatformVersionJson> targetVersions
    ) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            // The authorization (namespace membership, owner vs. member) is shared with the
            // token-authenticated delete endpoint, see RegistryAPI.deleteExtension.
            var result = extensions.deleteExtensionAsUser(user, namespaceName, extensionName, targetVersions);
            return ResponseEntity.ok(result);
        } catch (NotFoundException exc) {
            var json = NamespaceDetailsJson
                    .error("Extension not found: " + NamingUtil.toExtensionId(namespaceName, extensionName));
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/user/namespaces",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<NamespaceJson> getOwnNamespaces() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return repositories.findMemberships(user).map(membership -> {
            var namespace = membership.getNamespace();
            var extensions = new LinkedHashMap<String, String>();
            var serverUrl = UrlUtil.getBaseUrl();
            // return all extension of the namespace, include deleted ones
            repositories.findExtensionsForUrls(namespace).forEach(extension -> {
                String url = createApiUrl(serverUrl, "api", namespace.getName(), extension.getName());
                extensions.put(extension.getName(), url);
            });

            var json = new NamespaceJson();
            json.setName(namespace.getName());
            json.setExtensions(extensions);
            var isOwner = membership.getRole().equals(NamespaceMembership.ROLE_OWNER);
            json.setVerified(isOwner || repositories.isVerified(namespace));
            if (isOwner) {
                json.setMembersUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "members"));
                json.setRoleUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "role"));
                json.setDetailsUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "details"));
                json.setTrustedPublishingUrl(
                        createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "trusted-publishing"));
            }

            return json;
        }).toList();
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/details",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> updateNamespaceDetails(@RequestBody NamespaceDetailsJson details) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                    .body(users.updateNamespaceDetails(details, user));
        } catch (NotFoundException exc) {
            var json = NamespaceDetailsJson.error("Namespace not found: " + details.getName());
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ResultJson.class);
        }
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/details/logo",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> updateNamespaceDetailsLogo(
            @PathVariable String namespace,
            @RequestParam MultipartFile file
    ) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            return ResponseEntity.ok()
                    .body(users.updateNamespaceDetailsLogo(namespace, file, user));
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(ResultJson.class);
        }
    }

    @GetMapping(
        path = "/user/namespace/{name}/members",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<NamespaceMembershipListJson> getNamespaceMembers(@PathVariable String name) {
        var user = users.findLoggedInUser();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        var memberships = repositories.findMembershipsForOwner(user, name);
        if (!memberships.isEmpty()) {
            var membershipList = new NamespaceMembershipListJson();
            membershipList.setNamespaceMemberships(memberships.stream().map(NamespaceMembership::toJson).toList());
            return new ResponseEntity<>(membershipList, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(
                    NamespaceMembershipListJson.error("You don't have the permission to see this."),
                    HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/role",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> setNamespaceMember(
            @PathVariable String namespace,
            @RequestParam String user,
            @RequestParam String role,
            @RequestParam(required = false) String provider
    ) {
        var requestingUser = users.findLoggedInUser();
        if (requestingUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        try {
            var json = users.setNamespaceMember(requestingUser, namespace, provider, user, role);
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    @GetMapping(
        path = "/user/customers",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<CustomerJson> getOwnCustomers() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return repositories.findCustomerMemberships(user).map(membership -> membership.getCustomer().toUserJson())
                .toList();
    }

    @GetMapping(
        path = "/user/customers/{name}/usage",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UsageStatsListJson> getOwnUsageStats(
            @PathVariable String name,
            @RequestParam(required = false) String date
    ) {
        try {
            var user = users.findLoggedInUser();
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            var membership = repositories.findCustomerMembership(user, customer);
            if (membership == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            var localDateTime = date != null ? TimeUtil.fromUTCString(date) : TimeUtil.getCurrentUTC();
            var stats = repositories.findUsageStatsByCustomerAndDate(customer, localDateTime);
            var dailyStats = repositories.findDailyUsageStats(customer, localDateTime.toLocalDate());
            var dailyP95 = dailyStats != null ? dailyStats.getP95Requests() : null;
            var result = new UsageStatsListJson(stats.stream().map(UsageStats::toJson).toList(), dailyP95);
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed retrieving usage stats", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
        path = "/user/search/{name}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<UserJson> getUsersStartWith(@PathVariable String name) {
        if (users.findLoggedInUser() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        var pageable = Pageable.ofSize(5);
        return repositories.searchUsers(name, null, pageable).stream()
                .map(UserData::toUserJson)
                .toList();
    }

    @PostMapping(
        path = "/user/publisher-agreement",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UserJson> signPublisherAgreement() {
        var user = users.findLoggedInUser();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        try {
            var agreement = eclipse.signPublisherAgreement(user);
            var json = user.toUserJson();
            var serverUrl = UrlUtil.getBaseUrl();
            json.setRole(user.getRoleAsString());
            json.setTokensUrl(createApiUrl(serverUrl, "user", "tokens"));
            json.setCreateTokenUrl(createApiUrl(serverUrl, "user", "token", "create"));
            eclipse.enrichUserJson(json, user, agreement);

            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(UserJson.class);
        }
    }

}
