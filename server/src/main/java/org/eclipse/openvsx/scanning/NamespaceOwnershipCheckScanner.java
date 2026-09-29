/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.util.List;
import java.util.Objects;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.eclipse.openvsx.adapter.ExtensionQueryParam;
import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.adapter.IVSCodeService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.UrlUtil;

/**
 * Scanner that guards against namespace-squatting: it blocks publishing into a namespace that is
 * already claimed by some publisher in a referenced external gallery (by default the upstream VS
 * Code Marketplace), unless the publishing namespace is verified (has an owner, not only
 * contributors). The check is namespace-wide - it doesn't matter which extension(s) the upstream
 * publisher offers, only that the namespace name itself is already taken there.
 */
@Component
public class NamespaceOwnershipCheckScanner implements Scanner {

    public static final String TYPE = "NAMESPACE_OWNERSHIP_CHECK";

    /**
     * The VS Code Gallery API has no dedicated "publisher exists" filter, so an existence check has to
     * search by namespace name as free text and inspect the results for an exact publisher match. A
     * generous page size keeps that match from being missed when the namespace name also turns up
     * loosely-related, more "relevant" extensions from other publishers.
     */
    private static final int NAMESPACE_SEARCH_PAGE_SIZE = 100;

    private final NamespaceOwnershipCheckConfig config;
    private final RestTemplate restTemplate;
    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final ScannerRegistry scannerRegistry;

    public NamespaceOwnershipCheckScanner(
            NamespaceOwnershipCheckConfig config,
            RestTemplate restTemplate,
            RepositoryService repositories,
            EntityManager entityManager,
            ScannerRegistry scannerRegistry
    ) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.scannerRegistry = scannerRegistry;
    }

    @PostConstruct
    void register() {
        if (config.isEnabled()) {
            scannerRegistry.registerScanner(this);
        }
    }

    @Override
    @NonNull
    public String getScannerType() {
        return TYPE;
    }

    @Override
    public boolean isRequired() {
        return config.isRequired();
    }

    @Override
    public boolean enforcesThreats() {
        return config.isEnforced();
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public Scanner.@NonNull Invocation startScan(@NonNull Command command) throws ScannerException {
        var extVersion = entityManager.find(ExtensionVersion.class, command.extensionVersionId());
        if (extVersion == null) {
            throw new ScannerException("ExtensionVersion not found: " + command.extensionVersionId());
        }

        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();

        Extension active = repositories.findActiveExtension(extension.getName(), namespace.getName());
        if (!config.isCheckActiveExtensions() && active != null) {
            return new Scanner.Invocation.Completed(
                    Scanner.Result.clean(
                            "Extension '" + NamingUtil.toExtensionId(extension) + "' is already active."));
        }

        try {
            boolean namespaceExists = namespaceExistsInReferencedGallery(namespace.getName());
            if (!namespaceExists) {
                return new Scanner.Invocation.Completed(Scanner.Result.clean());
            }
        } catch (RestClientException ex) {
            if (config.isEnforced()) {
                throw new ScannerException("Failed to perform " + TYPE, ex);
            } else {
                return new Scanner.Invocation.Completed(
                        Scanner.Result.clean(
                                "Failed to perform " + TYPE + " scan: " + ex.getMessage()));
            }
        }

        if (repositories.isVerified(namespace)) {
            return new Scanner.Invocation.Completed(
                    Scanner.Result.clean(
                            "Namespace '" + namespace.getName()
                                    + "' exists in the referenced gallery and is verified."));
        }

        var threat = new Scanner.Threat(
                TYPE + "-conflict",
                "Namespace '" + namespace.getName() + "' exists in the referenced gallery, " +
                        "but is not verified.",
                "high");
        return new Scanner.Invocation.Completed(Scanner.Result.withThreats(List.of(threat)));
    }

    /**
     * Checks whether {@code namespaceName} is already claimed by some publisher in the referenced
     * gallery, regardless of which extension(s) that publisher offers there - the goal is to catch
     * namespace-squatting, not to match a specific extension identifier.
     * <p>
     * The VS Code Gallery API has no filter for "publisher exists", so this searches by the namespace
     * name as free text and then checks the results for an extension whose publisher name matches
     * {@code namespaceName} exactly (case-insensitively, as VS Code Marketplace publisher ids are
     * themselves case-insensitive) - a hit that merely mentions the name elsewhere doesn't count.
     * <p>
     * Returns {@code true} or {@code false} only if the check was performed and the result was clear
     * about it. In any other case this method throws.
     */
    private boolean namespaceExistsInReferencedGallery(String namespaceName) throws RestClientException {
        var requestUrl = UrlUtil.createApiUrl(config.getGalleryUrl(), "extensionquery");
        var requestData = new ExtensionQueryParam(
                List.of(
                        new ExtensionQueryParam.Filter(
                                List.of(
                                        new ExtensionQueryParam.Criterion(
                                                ExtensionQueryParam.Criterion.FILTER_TARGET,
                                                "Microsoft.VisualStudio.Code"),
                                        new ExtensionQueryParam.Criterion(
                                                ExtensionQueryParam.Criterion.FILTER_SEARCH_TEXT,
                                                namespaceName)),
                                1,
                                NAMESPACE_SEARCH_PAGE_SIZE,
                                0,
                                0)),
                0);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json;api-version=" + IVSCodeService.GALLERY_API_VERSION);
        var result = restTemplate
                .postForObject(requestUrl, new HttpEntity<>(requestData, headers), ExtensionQueryResult.class);
        if (result == null || result.results() == null || result.results().isEmpty()) {
            return false;
        }

        var extensions = result.results().getFirst().extensions();
        if (extensions == null) {
            return false;
        }

        return extensions.stream()
                .map(ExtensionQueryResult.Extension::publisher)
                .filter(Objects::nonNull)
                .map(ExtensionQueryResult.Publisher::publisherName)
                .anyMatch(namespaceName::equalsIgnoreCase);
    }
}
