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

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.repositories.ExtensionScanRepository;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.security.CodedAuthException;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eclipse.openvsx.entities.FileResource.*;
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
    private final ExtensionScanRepository scanRepository;

    public UserAPI(
            RepositoryService repositories,
            UserService users,
            AccessTokenService tokens,
            EclipseService eclipse,
            StorageUtilService storageUtil,
            LocalRegistryService local,
            ExtensionService extensions,
            ExtensionScanRepository scanRepository
    ) {
        this.repositories = repositories;
        this.users = users;
        this.tokens = tokens;
        this.eclipse = eclipse;
        this.storageUtil = storageUtil;
        this.local = local;
        this.extensions = extensions;
        this.scanRepository = scanRepository;
    }

    @GetMapping(
        path = "/login-providers"
    )
    public ResponseEntity<LoginProvidersJson> login() {
        var json = new LoginProvidersJson();
        var providers = users.getLoginProviders();
        if(!providers.isEmpty()) {
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
        var authException = users.canLogin() ? request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) : null;
        if (!(authException instanceof AuthenticationException))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        var code = authException instanceof CodedAuthException ? ((CodedAuthException) authException).getCode() : null;
        return new ErrorJson(((AuthenticationException) authException).getMessage(), code);
    }

    /**
     * This endpoint is used to check whether there is a logged-in user. For this reason, it
     * does not return a 403 status, but an OK status with JSON body when no user data is
     * available. This is to avoid unnecessary network error logging in the browser console.
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
        json.setRole(user.getRole());
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
        return repositories.findActiveAccessTokens(user)
                .map(token -> {
                    var json = token.toAccessTokenJson();
                    json.setDeleteTokenUrl(createApiUrl(serverUrl, "user", "token", "delete", Long.toString(token.getId())));
                    return json;
                })
                .toList();
    }

    @PostMapping(
        path = "/user/token/create",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AccessTokenJson> createAccessToken(@RequestParam(required = false) String description) {
        if (description != null && description.length() > TOKEN_DESCRIPTION_SIZE) {
            var json = AccessTokenJson.error("The description must not be longer than " + TOKEN_DESCRIPTION_SIZE + " characters.");
            return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
        }
        var user = users.findLoggedInUser();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return new ResponseEntity<>(tokens.createAccessToken(user, description), HttpStatus.CREATED);
    }

    @PostMapping(
        path = "/user/token/delete/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> deleteAccessToken(@PathVariable long id) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            return ResponseEntity.ok(tokens.deactivateAccessToken(user, id));
        } catch(NotFoundException e) {
            return new ResponseEntity<>(ResultJson.error("Token does not exist."), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(
            path = "/user/extensions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<ExtensionJson> getOwnExtensions() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        var extVersions = repositories.findLatestVersions(user);
        var types = new String[]{ DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST };
        var fileUrls = storageUtil.getFileUrls(extVersions, UrlUtil.getBaseUrl(), types);
        return extVersions.stream()
                .map(latest -> {
                    var json = latest.toExtensionJson();
                    json.setPreview(latest.isPreview());
                    json.setActive(latest.getExtension().isActive());
                    json.setFiles(fileUrls.get(latest.getId()));
                    
                    // Add scan/review status information
                    enrichWithReviewStatus(json, latest);
                    
                    return json;
                })
                .toList();
    }
    
    /**
     * Add review/scan status information to the extension JSON.
     * 
     * This shows users the current state of their extension in simple terms:
     * - "published" - Extension is active and publicly available
     * - "under_review" - Extension is being reviewed (validation, scanning, etc.)
     * - "rejected" - Extension was blocked (quarantined or rejected)
     */
    private void enrichWithReviewStatus(ExtensionJson json, ExtensionVersion extVersion) {
        // Look up scan by extension metadata (namespace, name, version, platform)
        var ext = extVersion.getExtension();
        var scanResult = scanRepository.findFirstByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatformOrderByStartedAtDesc(
            ext.getNamespace().getName(),
            ext.getName(),
            extVersion.getVersion(),
            extVersion.getTargetPlatform()
        );

        if (Boolean.TRUE.equals(json.getActive())) {
            // Only mark published if scan result indicates PASSED or no scan result exists (scanning disabled / manual activation)
            if (scanResult == null || scanResult.getStatus() == ScanStatus.PASSED) {
                json.setReviewStatus("published");
                return;
            }
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
            default:
                json.setReviewStatus("under_review");
                json.setReviewMessage("Your extension is being reviewed. Please contact support for details.");
        }
    }

    @GetMapping(
            path = "/user/extension/{namespaceName}/{extensionName}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExtensionJson> getOwnExtension(@PathVariable String namespaceName, @PathVariable String extensionName) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            ExtensionJson json;
            var latest = repositories.findLatestVersion(user, namespaceName, extensionName);
            if (latest != null) {
                json = local.toExtensionVersionJson(latest, null, false);
                json.setAllTargetPlatformVersions(repositories.findTargetPlatformsGroupedByVersion(latest.getExtension(), user));
                json.setActive(latest.getExtension().isActive());
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
            var result = extensions.deleteExtension(namespaceName, extensionName, targetVersions, user);
            return ResponseEntity.ok(result);
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
            repositories.findActiveExtensionsForUrls(namespace).forEach(extension -> {
                String url = createApiUrl(serverUrl, "api", namespace.getName(), extension.getName());
                extensions.put(extension.getName(), url);
            });

            var json = new NamespaceJson();
            json.setName(namespace.getName());
            json.setExtensions(extensions);
            var isOwner = membership.getRole().equals(NamespaceMembership.ROLE_OWNER);
            json.setVerified(isOwner || repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER));
            if(isOwner) {
                json.setMembersUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "members"));
                json.setRoleUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "role"));
                json.setDetailsUrl(createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "details"));
            }

            return json;
        }).toList();
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/details",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
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
            return new ResponseEntity<>(NamespaceMembershipListJson.error("You don't have the permission to see this."), HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/role",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> setNamespaceMember(@PathVariable String namespace, @RequestParam String user,
            @RequestParam String role, @RequestParam(required = false) String provider) {
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

        return repositories.findCustomerMemberships(user).map(membership -> membership.getCustomer().toUserJson() ).toList();
    }

    @GetMapping(
            path = "/user/customers/{name}/usage",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UsageStatsListJson> getOwnUsageStats(@PathVariable String name, @RequestParam(required = false) String date) {
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

        return repositories.findUsersByLoginNameStartingWith(name, 5).stream()
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
            json.setRole(user.getRole());
            json.setTokensUrl(createApiUrl(serverUrl, "user", "tokens"));
            json.setCreateTokenUrl(createApiUrl(serverUrl, "user", "token", "create"));
            eclipse.enrichUserJson(json, user, agreement);

            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(UserJson.class);
        }
    }

}
