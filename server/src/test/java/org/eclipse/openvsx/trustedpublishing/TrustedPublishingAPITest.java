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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.AccessTokenJson;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.TrustedPublisherInputJson;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingService.TrustedPublishers;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = TrustedPublishingAPI.class,
    excludeAutoConfiguration = { OAuth2ClientWebSecurityAutoConfiguration.class }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebUiProperties.class)
class TrustedPublishingAPITest {

    private static final String NAMESPACE = "foo";

    private static final String EXTENSION = "bar";

    private static final String PROVIDER = "github";

    private static final Map<String, String> REGISTRATION = Map
            .of("owner", "foo-org", "repository", "bar-repo", "workflow", "publish.yml");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MeterRegistry meterRegistry;

    @MockitoBean
    UserService users;

    @MockitoBean
    EclipseService eclipseService;

    @MockitoBean
    TrustedPublishingService trustedPublishing;

    // ---------------------------------------------------------------------------------------
    // POST /user/namespace/{namespace}/trusted-publishing/create
    // ---------------------------------------------------------------------------------------

    @Test
    void createTrustedPublisher_returns403_whenNotLoggedIn() throws Exception {
        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trustedPublishing);
    }

    @Test
    void createTrustedPublisher_returns400_whenMandatoryFieldsAreMissing() throws Exception {
        mockLoggedInUser();

        var missingProvider = registrationBody(NAMESPACE, EXTENSION, null, REGISTRATION);
        var missingExtension = registrationBody(NAMESPACE, null, PROVIDER, REGISTRATION);
        var missingNamespace = registrationBody(null, EXTENSION, PROVIDER, REGISTRATION);
        var missingRegistration = registrationBody(NAMESPACE, EXTENSION, PROVIDER, Map.of());

        for (var body : List.of(missingProvider, missingExtension, missingNamespace, missingRegistration)) {
            mockMvc.perform(createRequest(NAMESPACE, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.error")
                                    .value(
                                            "The fields provider, namespace, extension and registration are mandatory."));
        }

        verify(trustedPublishing, never())
                .registerTrustedPublisher(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTrustedPublisher_returns400_whenNamespaceDoesNotMatchPath() throws Exception {
        mockLoggedInUser();

        mockMvc.perform(createRequest("other", registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The namespace in the path and in the request body must match."));

        verify(trustedPublishing, never())
                .registerTrustedPublisher(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTrustedPublisher_returns201_andRegisteredPublisher() throws Exception {
        var user = mockLoggedInUser();
        var publisher = trustedPublisher(7L);
        when(trustedPublishing.registerTrustedPublisher(user, NAMESPACE, EXTENSION, PROVIDER, REGISTRATION))
                .thenReturn(publisher);

        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.namespace").value(NAMESPACE))
                .andExpect(jsonPath("$.extension").value(EXTENSION))
                .andExpect(jsonPath("$.provider").value(PROVIDER))
                .andExpect(jsonPath("$.registration.owner").value("foo-org"))
                .andExpect(jsonPath("$.registration.repository").value("bar-repo"))
                .andExpect(jsonPath("$.registration.workflow").value("publish.yml"))
                .andExpect(jsonPath("$.createdTimestamp").value("2026-01-02T03:04:05Z"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(eclipseService).checkPublisherAgreement(user);
    }

    @Test
    void createTrustedPublisher_returns403_whenPublisherAgreementIsMissing() throws Exception {
        var user = mockLoggedInUser();
        doThrow(
                new ErrorResultException(
                        "You must sign a Publisher Agreement with the Eclipse Foundation before publishing any extension.",
                        HttpStatus.FORBIDDEN))
                .when(eclipseService)
                .checkPublisherAgreement(user);

        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "You must sign a Publisher Agreement with the Eclipse Foundation before publishing any extension."));

        verify(trustedPublishing, never())
                .registerTrustedPublisher(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTrustedPublisher_returns404_whenNamespaceIsUnknown() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.registerTrustedPublisher(user, NAMESPACE, EXTENSION, PROVIDER, REGISTRATION))
                .thenThrow(new NotFoundException());

        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Namespace not found: " + NAMESPACE));
    }

    @Test
    void createTrustedPublisher_returns403_whenUserDoesNotOwnNamespace() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.registerTrustedPublisher(user, NAMESPACE, EXTENSION, PROVIDER, REGISTRATION))
                .thenThrow(new ErrorResultException("You must be an owner of this namespace.", HttpStatus.FORBIDDEN));

        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You must be an owner of this namespace."));
    }

    @Test
    void createTrustedPublisher_returns400_whenRegistrationIsRejected() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.registerTrustedPublisher(user, NAMESPACE, EXTENSION, PROVIDER, REGISTRATION))
                .thenThrow(new ErrorResultException("An equivalent trusted publisher is already registered."));

        mockMvc.perform(createRequest(NAMESPACE, registrationBody(NAMESPACE, EXTENSION, PROVIDER, REGISTRATION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("An equivalent trusted publisher is already registered."));
    }

    // ---------------------------------------------------------------------------------------
    // GET /user/namespace/{namespace}/trusted-publishing
    // ---------------------------------------------------------------------------------------

    @Test
    void getTrustedPublishers_returns403_whenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trustedPublishing);
    }

    @Test
    void getTrustedPublishers_returnsPublishersAndRegistrableExtensions() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.getTrustedPublishers(user, NAMESPACE))
                .thenReturn(new TrustedPublishers(List.of(trustedPublisher(7L)), List.of("baz", "qux")));

        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trustedPublishers.length()").value(1))
                .andExpect(jsonPath("$.trustedPublishers[0].id").value(7))
                .andExpect(jsonPath("$.trustedPublishers[0].namespace").value(NAMESPACE))
                .andExpect(jsonPath("$.trustedPublishers[0].extension").value(EXTENSION))
                .andExpect(jsonPath("$.trustedPublishers[0].provider").value(PROVIDER))
                .andExpect(jsonPath("$.registrableExtensions.length()").value(2))
                .andExpect(jsonPath("$.registrableExtensions[0]").value("baz"))
                .andExpect(jsonPath("$.registrableExtensions[1]").value("qux"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void getTrustedPublishers_returnsEmptyLists_whenNothingIsRegistered() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.getTrustedPublishers(user, NAMESPACE))
                .thenReturn(new TrustedPublishers(List.of(), List.of()));

        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trustedPublishers.length()").value(0))
                .andExpect(jsonPath("$.registrableExtensions.length()").value(0));
    }

    @Test
    void getTrustedPublishers_returns404_whenNamespaceIsUnknown() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.getTrustedPublishers(user, NAMESPACE)).thenThrow(new NotFoundException());

        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Namespace not found: " + NAMESPACE));
    }

    @Test
    void getTrustedPublishers_returns404_whenFeatureIsDisabled() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.getTrustedPublishers(user, NAMESPACE))
                .thenThrow(new ErrorResultException("Trusted publishing is not enabled.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Trusted publishing is not enabled."));
    }

    @Test
    void getTrustedPublishers_returns403_whenUserDoesNotOwnNamespace() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.getTrustedPublishers(user, NAMESPACE))
                .thenThrow(new ErrorResultException("You must be an owner of this namespace.", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/user/namespace/{namespace}/trusted-publishing", NAMESPACE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You must be an owner of this namespace."));
    }

    // ---------------------------------------------------------------------------------------
    // POST /user/namespace/{namespace}/trusted-publishing/delete/{id}
    // ---------------------------------------------------------------------------------------

    @Test
    void deleteTrustedPublisher_returns403_whenNotLoggedIn() throws Exception {
        mockMvc.perform(post("/user/namespace/{namespace}/trusted-publishing/delete/{id}", NAMESPACE, 7))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trustedPublishing);
    }

    @Test
    void deleteTrustedPublisher_returns200_andSuccessMessage() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.deleteTrustedPublisher(user, NAMESPACE, 7L))
                .thenReturn(ResultJson.success("Deleted trusted publisher for namespace " + NAMESPACE + "."));

        mockMvc.perform(post("/user/namespace/{namespace}/trusted-publishing/delete/{id}", NAMESPACE, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("Deleted trusted publisher for namespace " + NAMESPACE + "."))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(trustedPublishing).deleteTrustedPublisher(user, NAMESPACE, 7L);
    }

    @Test
    void deleteTrustedPublisher_returns404_whenPublisherIsUnknown() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.deleteTrustedPublisher(user, NAMESPACE, 7L)).thenThrow(new NotFoundException());

        mockMvc.perform(post("/user/namespace/{namespace}/trusted-publishing/delete/{id}", NAMESPACE, 7))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Trusted publisher does not exist."));
    }

    @Test
    void deleteTrustedPublisher_returns403_whenUserDoesNotOwnNamespace() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.deleteTrustedPublisher(user, NAMESPACE, 7L))
                .thenThrow(new ErrorResultException("You must be an owner of this namespace.", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/user/namespace/{namespace}/trusted-publishing/delete/{id}", NAMESPACE, 7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You must be an owner of this namespace."));
    }

    // ---------------------------------------------------------------------------------------
    // POST /api/-/trusted-publishing/token
    // ---------------------------------------------------------------------------------------

    @Test
    void requestPublishToken_returns400_whenMandatoryFieldsAreMissing() throws Exception {
        var missingNamespace = tokenRequestBody(null, EXTENSION, "the-token");
        var missingExtension = tokenRequestBody(NAMESPACE, null, "the-token");
        var missingToken = tokenRequestBody(NAMESPACE, EXTENSION, null);

        for (var body : List.of(missingNamespace, missingExtension, missingToken)) {
            mockMvc.perform(tokenRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("The fields namespace, extension and token are mandatory."));
        }

        verify(trustedPublishing, never()).requestPublishToken(anyString(), anyString(), anyString());
    }

    @Test
    void requestPublishToken_returns201_andAccessToken() throws Exception {
        // the exchange authenticates through the presented OIDC token, so no logged-in user is needed
        var accessToken = new AccessTokenJson();
        accessToken.setId(42L);
        accessToken.setValue("the-access-token");
        accessToken.setDescription("Trusted publishing (github)");
        when(trustedPublishing.requestPublishToken(NAMESPACE, EXTENSION, "the-token")).thenReturn(accessToken);

        mockMvc.perform(tokenRequest(tokenRequestBody(NAMESPACE, EXTENSION, "the-token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.value").value("the-access-token"))
                .andExpect(jsonPath("$.description").value("Trusted publishing (github)"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verifyNoInteractions(users);
    }

    @Test
    void requestPublishToken_returns403_whenNoTrustedPublisherMatches() throws Exception {
        when(trustedPublishing.requestPublishToken(NAMESPACE, EXTENSION, "the-token")).thenThrow(
                new ErrorResultException(
                        "No trusted publisher matches the presented token.",
                        HttpStatus.FORBIDDEN));

        mockMvc.perform(tokenRequest(tokenRequestBody(NAMESPACE, EXTENSION, "the-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No trusted publisher matches the presented token."));
    }

    // A refusal tells a publishing workflow never to come back, so the registry being unable to reach the
    // provider must not look like one - the CI job should retry instead of failing the build.
    @Test
    void requestPublishToken_returns503_whenTheTokenCouldNotBeVerified() throws Exception {
        when(trustedPublishing.requestPublishToken(NAMESPACE, EXTENSION, "the-token")).thenThrow(
                new ErrorResultException(
                        "Could not verify the token with GitHub, please retry.",
                        HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(tokenRequest(tokenRequestBody(NAMESPACE, EXTENSION, "the-token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Could not verify the token with GitHub, please retry."));
    }

    @Test
    void requestPublishToken_returns400_whenTokenIsNotAcceptable() throws Exception {
        when(trustedPublishing.requestPublishToken(NAMESPACE, EXTENSION, "the-token"))
                .thenThrow(new ErrorResultException("Unsupported token issuer."));

        mockMvc.perform(tokenRequest(tokenRequestBody(NAMESPACE, EXTENSION, "the-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported token issuer."));
    }

    // ---------------------------------------------------------------------------------------
    // GET /api/-/trusted-publishing/status
    // ---------------------------------------------------------------------------------------

    @Test
    void getTrustedPublishingStatus_returns403_whenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/-/trusted-publishing/status")).andExpect(status().isForbidden());

        verifyNoInteractions(trustedPublishing);
    }

    @Test
    void getTrustedPublishingStatus_reportsDisabledFeature() throws Exception {
        mockLoggedInUser();
        when(trustedPublishing.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/-/trusted-publishing/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.trustedPublisherProviders").doesNotExist());

        verify(trustedPublishing, never()).getTrustedPublisherProviders();
    }

    @Test
    void getTrustedPublishingStatus_hidesProviders_whenPublisherAgreementIsMissing() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.isEnabled()).thenReturn(true);
        when(eclipseService.hasPublisherAgreement(user)).thenReturn(false);

        mockMvc.perform(get("/api/-/trusted-publishing/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.trustedPublisherProviders").doesNotExist());

        verify(trustedPublishing, never()).getTrustedPublisherProviders();
    }

    @Test
    void getTrustedPublishingStatus_listsActiveProviders() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.isEnabled()).thenReturn(true);
        when(eclipseService.hasPublisherAgreement(user)).thenReturn(true);

        // LinkedHashMap so the order of the listed providers is the one asserted below
        var providers = new LinkedHashMap<String, TrustedPublishingProviderSupport>();
        providers.put(
                PROVIDER,
                provider(
                        PROVIDER,
                        "GitHub Actions",
                        "https://github.com",
                        List.of(
                                TrustedPublisherInputJson.create("owner", "The owner of the repository", false),
                                TrustedPublisherInputJson.create("environment", "The environment", true))));
        providers.put("gitlab", provider("gitlab", "GitLab CI/CD", "https://gitlab.com", List.of()));
        when(trustedPublishing.getTrustedPublisherProviders()).thenReturn(providers);

        mockMvc.perform(get("/api/-/trusted-publishing/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.trustedPublisherProviders.length()").value(2))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].id").value(PROVIDER))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].name").value("GitHub Actions"))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].url").value("https://github.com"))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].registrationInputs.length()").value(2))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].registrationInputs[0].key").value("owner"))
                .andExpect(
                        jsonPath("$.trustedPublisherProviders[0].registrationInputs[0].description")
                                .value("The owner of the repository"))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].registrationInputs[0].optional").value(false))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].registrationInputs[1].key").value("environment"))
                .andExpect(jsonPath("$.trustedPublisherProviders[0].registrationInputs[1].optional").value(true))
                .andExpect(jsonPath("$.trustedPublisherProviders[1].id").value("gitlab"))
                .andExpect(jsonPath("$.trustedPublisherProviders[1].name").value("GitLab CI/CD"));
    }

    @Test
    void getTrustedPublishingStatus_returns404_whenProviderLookupFindsFeatureDisabled() throws Exception {
        var user = mockLoggedInUser();
        when(trustedPublishing.isEnabled()).thenReturn(true);
        when(eclipseService.hasPublisherAgreement(user)).thenReturn(true);
        when(trustedPublishing.getTrustedPublisherProviders())
                .thenThrow(new ErrorResultException("Trusted publishing is not enabled.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/-/trusted-publishing/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Trusted publishing is not enabled."));
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private UserData mockLoggedInUser() {
        var user = new UserData();
        user.setId(1L);
        user.setLoginName("test_user");
        when(users.findLoggedInUser()).thenReturn(user);
        return user;
    }

    private static TrustedPublisher trustedPublisher(long id) {
        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setName(NAMESPACE);
        var extension = new Extension();
        extension.setId(2L);
        extension.setName(EXTENSION);
        extension.setNamespace(namespace);

        var publisher = new TrustedPublisher();
        publisher.setId(id);
        publisher.setExtension(extension);
        publisher.setProvider(PROVIDER);
        publisher.setRegistration(REGISTRATION);
        publisher.setCreatedTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        return publisher;
    }

    private static TrustedPublishingProviderSupport provider(
            String id,
            String name,
            String url,
            List<TrustedPublisherInputJson> registrationInputs
    ) {
        var provider = mock(TrustedPublishingProviderSupport.class);
        when(provider.getProviderId()).thenReturn(id);
        when(provider.getProviderName()).thenReturn(name);
        when(provider.getProviderUrl()).thenReturn(url);
        when(provider.getRegistrationInputs()).thenReturn(registrationInputs);
        return provider;
    }

    private static MockHttpServletRequestBuilder createRequest(String namespace, String body) {
        return post("/user/namespace/{namespace}/trusted-publishing/create", namespace)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder tokenRequest(String body) {
        return post("/api/-/trusted-publishing/token").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String registrationBody(
            String namespace,
            String extension,
            String provider,
            Map<String, String> registration
    ) {
        var body = new LinkedHashMap<String, Object>();
        body.put("namespace", namespace);
        body.put("extension", extension);
        body.put("provider", provider);
        body.put("registration", registration);
        return JsonMapper.shared().writeValueAsString(body);
    }

    private static String tokenRequestBody(String namespace, String extension, String token) {
        var body = new LinkedHashMap<String, Object>();
        body.put("namespace", namespace);
        body.put("extension", extension);
        body.put("token", token);
        return JsonMapper.shared().writeValueAsString(body);
    }
}
