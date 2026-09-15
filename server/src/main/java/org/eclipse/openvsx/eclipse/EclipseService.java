/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.eclipse;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.AuthToken;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.HttpHeadersUtil;
import org.eclipse.openvsx.util.TimeUtil;

@Service
public class EclipseService {

    private static final String VAR_PERSON_ID = "personId";

    public static final DateTimeFormatter CUSTOM_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();

    private static final TypeReference<List<String>> TYPE_LIST_STRING = new TypeReference<>() {
    };
    private static final TypeReference<List<EclipseProfile>> TYPE_LIST_PROFILE = new TypeReference<>() {
    };
    private static final TypeReference<List<PublisherAgreementResponse>> TYPE_LIST_AGREEMENT = new TypeReference<>() {
    };

    protected final Logger logger = LoggerFactory.getLogger(EclipseService.class);

    private final EclipseTokenService tokens;
    private final ExtensionService extensions;
    private final EntityManager entityManager;
    private final RestTemplate restTemplate;
    private final JsonMapper jsonMapper;

    @Value("${ovsx.eclipse.base-url:}")
    String eclipseApiUrl;

    @Value("${ovsx.eclipse.publisher-agreement.version:}")
    String publisherAgreementVersion;

    @Value("${ovsx.eclipse.publisher-agreement.allowed-versions:}")
    List<String> publisherAgreementAllowedVersions;

    public EclipseService(
            EclipseTokenService tokens,
            ExtensionService extensions,
            EntityManager entityManager,
            RestTemplate restTemplate
    ) {
        this.tokens = tokens;
        this.extensions = extensions;
        this.entityManager = entityManager;
        this.restTemplate = restTemplate;
        this.jsonMapper = JsonMapper.builder().build();
    }

    public boolean isActive() {
        return !StringUtils.isEmpty(publisherAgreementVersion) && !publisherAgreementAllowedVersions.isEmpty();
    }

    /**
     * Check whether the given user has an active publisher agreement and returns {@code true} if user has it.
     */
    public boolean hasPublisherAgreement(UserData user) {
        // Users without authentication provider have been created directly in the DB,
        // so we skip the agreement check in this case.
        if (!isActive() || user.getProvider() == null) {
            return true;
        }
        var personId = user.getEclipsePersonId();
        if (personId == null) {
            return false;
        }
        var json = user.toUserJson();
        enrichUserJsonWithPublisherAgreement(json, user);
        var publisherAgreement = json.getPublisherAgreement();

        return publisherAgreement != null && publisherAgreement.getStatus().equals("signed");
    }

    /**
     * Check whether the given user has an active publisher agreement.
     * @throws ErrorResultException if the user has no active agreement
     */
    public void checkPublisherAgreement(UserData user) {
        if (!isActive()) {
            return;
        }
        // Users without authentication provider have been created directly in the DB,
        // so we skip the agreement check in this case.
        if (user.getProvider() == null) {
            return;
        }
        var personId = user.getEclipsePersonId();
        if (personId == null) {
            throw new ErrorResultException(
                    "You must log in with an Eclipse Foundation account and sign a Publisher Agreement before publishing any extension.",
                    HttpStatus.FORBIDDEN);
        }

        var json = user.toUserJson();
        enrichUserJsonWithPublisherAgreement(json, user);
        var publisherAgreement = json.getPublisherAgreement();

        if (publisherAgreement == null || publisherAgreement.getStatus().equals("none")) {
            throw new ErrorResultException(
                    "You must sign a Publisher Agreement with the Eclipse Foundation before publishing any extension.",
                    HttpStatus.FORBIDDEN);
        }

        if (!publisherAgreement.getStatus().equals("signed")) {
            if (publisherAgreement.getVersion() != null) {
                throw new ErrorResultException(
                        "Your Publisher Agreement with the Eclipse Foundation is outdated (version "
                                + publisherAgreement.getVersion() + "). The current version is "
                                + publisherAgreementVersion + ".",
                        HttpStatus.FORBIDDEN);
            } else {
                throw new ErrorResultException(
                        "Your Publisher Agreement with the Eclipse Foundation is outdated.",
                        HttpStatus.FORBIDDEN);
            }
        }
    }

    /**
     * Get the publicly available user profile.
     */
    public EclipseProfile getPublicProfile(String personId) {
        var urlTemplate = buildApiUrl("account/profile/{personId}");
        var uriVariables = Map.of(VAR_PERSON_ID, personId);
        var request = new HttpEntity<Void>(HttpHeadersUtil.getAcceptJsonHeaders());

        try {
            var response = restTemplate.exchange(urlTemplate, HttpMethod.GET, request, String.class, uriVariables);
            return parseEclipseProfile(response);
        } catch (RestClientException exc) {
            if (exc instanceof HttpStatusCodeException) {
                var status = ((HttpStatusCodeException) exc).getStatusCode();
                if (status == HttpStatus.NOT_FOUND) {
                    throw new ErrorResultException(
                            "No Eclipse profile data available for user '" + personId + "': " + exc.getMessage());
                }
            }

            var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
            logger.error("Get request failed with URL: {}", url, exc);
            throw new ErrorResultException(
                    "Request for retrieving user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update the given user data with a profile obtained from Eclipse API.
     */
    @Transactional
    public void updateUserData(UserData user, EclipseProfile profile) {
        // find-then-set rather than merge: merge writes every column of the detached copy, so
        // anything that changed on the row since it was loaded gets reverted. Only the field
        // below is this method's to change - see #989.
        var managedUser = entityManager.find(UserData.class, user.getId());
        if (managedUser == null) {
            return;
        }
        managedUser.setEclipsePersonId(profile.getName());
    }

    public void enrichUserJsonWithPublisherAgreement(UserJson json, UserData user) {
        if (!isActive()) {
            return;
        }

        var usableToken = true;
        PublisherAgreement agreement = null;
        try {
            // Add information on the publisher agreement
            agreement = getPublisherAgreement(user);
        } catch (ErrorResultException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                usableToken = false;
            } else {
                logger.warn("Failed to retrieve publisher agreement", e);
            }
        }

        // If we do not have a valid access token, access the public profile to find a signed OpenVSX publisher agreement.
        // Note: this service uses cached data so it might not reflect the actual situation.
        if (!usableToken) {
            var eclipsePersonId = user.getEclipsePersonId();
            if (eclipsePersonId != null) {
                try {
                    var profile = getPublicProfile(user.getEclipsePersonId());
                    var publisherAgreement = profile.getOpenVsxPublisherAgreement();
                    if (publisherAgreement.isPresent()) {
                        agreement = new PublisherAgreement(true, null, publisherAgreement.get().getVersion(), null);
                    }
                } catch (ErrorResultException e) {
                    // public profile could not be retrieved for the user, could be blocked.
                    logger.warn(e.getMessage());
                }
            }
        }

        enrichUserJson(json, user, agreement, usableToken);
    }

    public void enrichUserJson(UserJson json, UserData user, PublisherAgreement agreement) {
        enrichUserJson(json, user, agreement, true);
    }

    /**
     * Enrich the given JSON user data with Eclipse-specific information.
     */
    private void enrichUserJson(UserJson json, UserData user, PublisherAgreement agreement, boolean usableToken) {
        if (!isActive()) {
            return;
        }

        var publisherAgreement = new UserJson.PublisherAgreement();
        publisherAgreement.setStatus("none");
        json.setPublisherAgreement(publisherAgreement);

        var personId = user.getEclipsePersonId();
        if (personId == null) {
            return;
        }

        if (agreement != null && agreement.isActive() && agreement.version() != null) {
            var status = publisherAgreementAllowedVersions.contains(agreement.version()) ? "signed" : "outdated";
            publisherAgreement.setStatus(status);
        }

        if (agreement != null) {
            publisherAgreement.setVersion(agreement.version());
        }

        if (agreement != null && agreement.timestamp() != null) {
            publisherAgreement.setTimestamp(TimeUtil.toUTCString(agreement.timestamp()));
        }

        // Report user as logged in only if there is a usable token:
        // we need the token to access the Eclipse REST API
        if (usableToken) {
            var eclipseLogin = new UserJson();
            eclipseLogin.setProvider("eclipse");
            eclipseLogin.setLoginName(personId);
            if (json.getAdditionalLogins() == null) {
                json.setAdditionalLogins(new ArrayList<>(List.of(eclipseLogin)));
            } else {
                json.getAdditionalLogins().add(eclipseLogin);
            }
        }
    }

    /**
     * Determine the given user's publisher agreement status for admin purposes: one of
     * 'none', 'signed', 'outdated', or null if the status could not be reliably determined
     * (e.g. the Eclipse profile lookup failed, or is blocked). Callers must treat null as
     * "undetermined" and not fall back to guessing 'none' or any other value.
     */
    public @Nullable String determinePublisherAgreementStatus(UserData user) {
        if (!isActive()) {
            return null;
        }

        var personId = user.getEclipsePersonId();
        if (personId == null) {
            // No Eclipse account is linked at all - that's a definite, known fact (not signed),
            // unlike the isActive()/lookup-failure cases below where the status genuinely can't
            // be determined. Report it as "none" rather than null.
            return "none";
        }

        try {
            var profile = getPublicProfile(personId);
            var openVsxPublisherAgreement = profile.getOpenVsxPublisherAgreement();
            if (openVsxPublisherAgreement.isEmpty()
                    || StringUtils.isEmpty(openVsxPublisherAgreement.get().getVersion())) {
                return "none";
            }
            return publisherAgreementAllowedVersions.contains(openVsxPublisherAgreement.get().getVersion())
                    ? "signed"
                    : "outdated";
        } catch (RuntimeException e) {
            // Profile could not be retrieved (e.g. the user is blocked on Eclipse's side) or
            // something else unexpected went wrong - the status is genuinely unknown, so it
            // must not be reported as any of the determined values above.
            logger.error("Failed to get public profile", e);
            return null;
        }
    }

    public void adminEnrichUserJson(UserJson json, UserData user) {
        var status = determinePublisherAgreementStatus(user);
        if (status == null) {
            // Leave json.publisherAgreement unset (null) rather than guessing - the frontend
            // must treat a missing agreement the same as an undetermined one, not as 'none'.
            return;
        }

        var publisherAgreement = new UserJson.PublisherAgreement();
        publisherAgreement.setStatus(status);
        json.setPublisherAgreement(publisherAgreement);
    }

    /**
     * Get the user profile available through an access token.
     */
    public EclipseProfile getUserProfile(String accessToken) {
        var requestUrl = buildApiUrl("openvsx/profile");
        var headers = HttpHeadersUtil.getAcceptJsonHeaders();
        headers.setBearerAuth(accessToken);
        var request = new RequestEntity<>(headers, HttpMethod.GET, URI.create(requestUrl));

        try {
            var response = restTemplate.exchange(request, String.class);
            return parseEclipseProfile(response);
        } catch (RestClientException exc) {
            logger.error("Get request failed with URL: {}", requestUrl, exc);
            throw new ErrorResultException(
                    "Request for retrieving user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private EclipseProfile parseEclipseProfile(ResponseEntity<String> response) {
        var json = response.getBody();
        if (json == null) {
            return new EclipseProfile();
        }

        try {
            if (json.startsWith("[\"")) {
                var error = jsonMapper.readValue(json, TYPE_LIST_STRING);
                logger.error("Profile request failed:\n{}", json);
                throw new ErrorResultException(
                        "Request to the Eclipse Foundation server failed: " + error,
                        HttpStatus.INTERNAL_SERVER_ERROR);
            } else if (json.startsWith("[")) {
                var profileList = jsonMapper.readValue(json, TYPE_LIST_PROFILE);
                if (profileList.isEmpty()) {
                    throw new ErrorResultException(
                            "No Eclipse user profile available.",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return profileList.getFirst();
            } else {
                return jsonMapper.readValue(json, EclipseProfile.class);
            }
        } catch (JacksonException exc) {
            logger.error("Failed to parse JSON response ({}):\n{}", response.getStatusCode(), json, exc);
            throw new ErrorResultException(
                    "Parsing Eclipse user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get the publisher agreement of the given user with the user's current access token.
     */
    public PublisherAgreement getPublisherAgreement(UserData user) {
        var eclipseToken = checkEclipseToken(user);
        var personId = user.getEclipsePersonId();
        if (StringUtils.isEmpty(personId)) {
            return null;
        }
        var urlTemplate = buildApiUrl("openvsx/publisher_agreement/{personId}");
        var uriVariables = Map.of(VAR_PERSON_ID, personId);
        var headers = HttpHeadersUtil.getAcceptJsonHeaders();
        headers.setBearerAuth(eclipseToken.accessToken());
        var request = new HttpEntity<>(headers);

        try {
            var json = restTemplate.exchange(urlTemplate, HttpMethod.GET, request, String.class, uriVariables);
            return parseAgreementResponse(json);
        } catch (RestClientException exc) {
            HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
            if (exc instanceof HttpStatusCodeException) {
                status = ((HttpStatusCodeException) exc).getStatusCode();
                // The endpoint yields 404 if the specified user has not signed a publisher agreement
                if (status == HttpStatus.NOT_FOUND) {
                    return null;
                }
            }

            var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
            logger.error("Get request failed with URL: {}", url, exc);
            throw new ErrorResultException(
                    "Request for retrieving publisher agreement failed: " + exc.getMessage(),
                    status);
        }
    }

    private static final Pattern STATUS_400_MESSAGE = Pattern
            .compile("400 Bad Request: \\[\\[\"(?<message>[^\"]+)\"]]");

    /**
     * Sign the publisher agreement on behalf of the given user.
     */
    public PublisherAgreement signPublisherAgreement(UserData user) {
        var requestUrl = buildApiUrl("openvsx/publisher_agreement");
        var eclipseToken = checkEclipseToken(user);
        var headers = HttpHeadersUtil.getAcceptJsonHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(eclipseToken.accessToken());
        var data = new SignAgreementParam(publisherAgreementVersion, user.getLoginName());
        var request = new HttpEntity<>(data, headers);

        try {
            var json = restTemplate.postForEntity(requestUrl, request, String.class);

            // The request was successful: reactivate all previously published extensions
            extensions.reactivateExtensions(user);

            // Parse the response and store the publisher agreement metadata
            return parseAgreementResponse(json);
        } catch (RestClientException exc) {
            String message = exc.getMessage();
            var statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            if (exc instanceof HttpStatusCodeException) {
                var excStatus = ((HttpStatusCodeException) exc).getStatusCode();
                // The endpoint yields 409 if the specified user has already signed a publisher agreement
                if (excStatus == HttpStatus.CONFLICT) {
                    message = "A publisher agreement is already present for user " + user.getLoginName() + ".";
                    statusCode = HttpStatus.BAD_REQUEST;
                } else if (excStatus == HttpStatus.BAD_REQUEST) {
                    var matcher = STATUS_400_MESSAGE.matcher(exc.getMessage());
                    if (matcher.matches()) {
                        message = matcher.group("message");
                    }
                }
            }
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
                message = "Request for signing publisher agreement failed: " + message;
            }

            String payload;
            try {
                payload = jsonMapper.writeValueAsString(data);
            } catch (JacksonException exc2) {
                payload = "<" + exc2.getMessage() + ">";
            }
            logger.error("Post request failed with URL: {} Payload: {}", requestUrl, payload, exc);
            throw new ErrorResultException(message, statusCode);
        }
    }

    private PublisherAgreement parseAgreementResponse(ResponseEntity<String> response) {
        var json = response.getBody();
        if (json == null) {
            return null;
        }

        try {
            PublisherAgreementResponse agreementResponse;
            if (json.startsWith("[\"")) {
                var error = jsonMapper.readValue(json, TYPE_LIST_STRING);
                logger.error("Publisher agreement request failed:\n{}", json);
                throw new ErrorResultException(
                        "Request to the Eclipse Foundation server failed: " + error,
                        HttpStatus.INTERNAL_SERVER_ERROR);
            } else if (json.startsWith("[")) {
                var profileList = jsonMapper.readValue(json, TYPE_LIST_AGREEMENT);
                if (profileList.isEmpty()) {
                    throw new ErrorResultException(
                            "No publisher agreement available.",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                agreementResponse = profileList.getFirst();
            } else {
                agreementResponse = jsonMapper.readValue(json, PublisherAgreementResponse.class);
            }

            var timestamp = parseDate(agreementResponse.effectiveDate);
            return new PublisherAgreement(
                    TimeUtil.getCurrentUTC().isAfter(timestamp),
                    agreementResponse.documentID,
                    agreementResponse.version,
                    timestamp);
        } catch (JacksonException exc) {
            logger.error("Failed to parse JSON response ({}):\n{}", response.getStatusCode(), json, exc);
            throw new ErrorResultException(
                    "Parsing publisher agreement response failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private LocalDateTime parseDate(String dateString) {
        try {
            return LocalDateTime.parse(dateString, CUSTOM_DATE_TIME);
        } catch (DateTimeParseException exc) {
            logger.error("Failed to parse timestamp.", exc);
            return null;
        }
    }

    /**
     * Revoke the given user's publisher agreement. If an admin user is given,
     * the admin's access token is used for the Eclipse API request, otherwise
     * the access token of the target user is used.
     */
    public void revokePublisherAgreement(UserData user, UserData admin) {
        checkEclipseData(user);

        var eclipseToken = admin == null ? checkEclipseToken(user) : checkEclipseToken(admin);
        var headers = new HttpHeaders();
        headers.setBearerAuth(eclipseToken.accessToken());
        var request = new HttpEntity<>(headers);
        var urlTemplate = buildApiUrl("openvsx/publisher_agreement/{personId}");
        var uriVariables = Map.of(VAR_PERSON_ID, user.getEclipsePersonId());

        try {
            var requestCallback = restTemplate.httpEntityCallback(request);
            restTemplate.execute(urlTemplate, HttpMethod.DELETE, requestCallback, null, uriVariables);
        } catch (RestClientException exc) {
            if (exc instanceof HttpStatusCodeException
                    && ((HttpStatusCodeException) exc).getStatusCode() == HttpStatus.NOT_FOUND) {
                // Same convention as getPublisherAgreement(): 404 means the user never signed
                // one, so there is nothing to revoke - not an error worth failing the caller for.
                return;
            }

            var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
            logger.error("Delete request failed with URL: {}", url, exc);
            throw new ErrorResultException(
                    "Request for revoking publisher agreement failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void checkApiUrl() {
        if (StringUtils.isEmpty(eclipseApiUrl)) {
            throw new ErrorResultException("Missing URL for Eclipse API.");
        }
    }

    private String buildApiUrl(String path) {
        checkApiUrl();

        var baseUrl = eclipseApiUrl;
        if (eclipseApiUrl.charAt(eclipseApiUrl.length() - 1) != '/') {
            baseUrl += '/';
        }

        return baseUrl + path;
    }

    private AuthToken checkEclipseToken(UserData user) {
        var eclipseToken = tokens.getActiveEclipseToken(user);
        if (eclipseToken == null || StringUtils.isEmpty(eclipseToken.accessToken())) {
            throw new ErrorResultException("Authorization by Eclipse required.", HttpStatus.FORBIDDEN);
        }
        return eclipseToken;
    }

    private void checkEclipseData(UserData user) {
        if (StringUtils.isEmpty(user.getEclipsePersonId())) {
            throw new ErrorResultException(
                    "Eclipse person ID is unavailable for user: "
                            + user.getProvider() + "/" + user.getLoginName());
        }
    }
}
