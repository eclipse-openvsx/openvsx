/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.trustedpublishing;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.TrustedPublisherJson;
import org.eclipse.openvsx.json.TrustedPublisherListJson;
import org.eclipse.openvsx.json.TrustedPublisherProviderJson;
import org.eclipse.openvsx.json.TrustedPublisherStatusJson;
import org.eclipse.openvsx.json.TrustedPublisherTokenRequestJson;
import org.eclipse.openvsx.settings.MutatingOperation;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;

@RestController
public class TrustedPublishingAPI {

    private final UserService users;
    private final EclipseService eclipseService;
    private final TrustedPublishingService trustedPublishing;

    public TrustedPublishingAPI(
            UserService users,
            EclipseService eclipseService,
            TrustedPublishingService trustedPublishing
    ) {
        this.users = users;
        this.eclipseService = eclipseService;
        this.trustedPublishing = trustedPublishing;
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/trusted-publishing/create",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<TrustedPublisherJson> createTrustedPublisher(
            @PathVariable String namespace,
            @RequestBody TrustedPublisherJson request
    ) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            // throws when the user has no signed publisher agreement, and so has to be caught below
            eclipseService.checkPublisherAgreement(user);
            if (!StringUtils.hasText(request.getProvider())
                    || !StringUtils.hasText(request.getNamespace()) || !StringUtils.hasText(request.getExtension())
                    || request.getRegistration() == null || request.getRegistration().isEmpty()) {
                var json = TrustedPublisherJson
                        .error("The fields provider, namespace, extension and registration are mandatory.");
                return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
            }
            if (!Objects.equals(namespace, request.getNamespace())) {
                var json = TrustedPublisherJson.error("The namespace in the path and in the request body must match.");
                return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
            }

            var publisher = trustedPublishing.registerTrustedPublisher(
                    user,
                    request.getNamespace(),
                    request.getExtension(),
                    request.getProvider(),
                    request.getRegistration());
            return new ResponseEntity<>(publisher.toJson(), HttpStatus.CREATED);
        } catch (NotFoundException exc) {
            var json = TrustedPublisherJson.error("Namespace not found: " + namespace);
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(TrustedPublisherJson.class);
        }
    }

    @GetMapping(
        path = "/user/namespace/{namespace}/trusted-publishing",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TrustedPublisherListJson> getTrustedPublishers(@PathVariable String namespace) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            var trustedPublishers = trustedPublishing.getTrustedPublishers(user, namespace);
            var json = new TrustedPublisherListJson();
            json.setTrustedPublishers(
                    trustedPublishers.publishers().stream()
                            .map(TrustedPublisher::toJson)
                            .toList());
            json.setRegistrableExtensions(trustedPublishers.registrableExtensions());
            return ResponseEntity.ok(json);
        } catch (NotFoundException exc) {
            var json = TrustedPublisherListJson.error("Namespace not found: " + namespace);
            return new ResponseEntity<>(json, HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(TrustedPublisherListJson.class);
        }
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/trusted-publishing/delete/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<ResultJson> deleteTrustedPublisher(@PathVariable String namespace, @PathVariable long id) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            return ResponseEntity.ok(trustedPublishing.deleteTrustedPublisher(user, namespace, id));
        } catch (NotFoundException exc) {
            return new ResponseEntity<>(ResultJson.error("Trusted publisher does not exist."), HttpStatus.NOT_FOUND);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        }
    }

    /**
     * Exchanges a valid OIDC ID token, issued by a trusted publishing provider and matching a registered
     * trusted publisher, for a short-lived access token to publish with.
     */
    @PostMapping(
        path = "/api/-/trusted-publishing/token",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @MutatingOperation
    public ResponseEntity<AccessTokenJson> requestPublishToken(@RequestBody TrustedPublisherTokenRequestJson request) {
        if (!StringUtils.hasText(request.getNamespace()) || !StringUtils.hasText(request.getExtension())
                || !StringUtils.hasText(request.getToken())) {
            var json = AccessTokenJson.error("The fields namespace, extension and token are mandatory.");
            return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
        }

        try {
            var json = trustedPublishing
                    .requestPublishToken(request.getNamespace(), request.getExtension(), request.getToken());
            return new ResponseEntity<>(json, HttpStatus.CREATED);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(AccessTokenJson.class);
        }
    }

    /**
     * Queries TP status. Shown to logged-in users.
     */
    @GetMapping(
        path = "/api/-/trusted-publishing/status",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TrustedPublisherStatusJson> getTrustedPublishingStatus() {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            var json = new TrustedPublisherStatusJson();
            if (trustedPublishing.isEnabled()) {
                json.setEnabled(true);
                json.setAllowed(eclipseService.hasPublisherAgreement(user));
                if (json.isAllowed()) {
                    json.setTrustedPublisherProviders(
                            trustedPublishing.getTrustedPublisherProviders().values().stream()
                                    .map(p -> {
                                        var providerJson = new TrustedPublisherProviderJson();
                                        providerJson.setId(p.getProviderId());
                                        providerJson.setName(p.getProviderName());
                                        providerJson.setUrl(p.getProviderUrl());
                                        providerJson.setRegistrationInputs(p.getRegistrationInputs());
                                        return providerJson;
                                    })
                                    .toList());
                }
            } else {
                json.setEnabled(false);
            }
            return ResponseEntity.ok(json);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(TrustedPublisherStatusJson.class);
        }
    }
}
