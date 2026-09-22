/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.eclipse;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import org.eclipse.openvsx.entities.AuthToken;
import org.eclipse.openvsx.entities.UserData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * EclipseTokenService is constructor-injected with RestTemplateConfig's shared {@code restTemplate}
 * bean (see the fix in this PR). That bean's message converters must actually be able to write the
 * application/x-www-form-urlencoded token-refresh request this service sends - a plain
 * StringHttpMessageConverter + JacksonJsonHttpMessageConverter pair (the bean's converters before this
 * test was added) cannot, which makes every token refresh silently fail and return null.
 */
class EclipseTokenServiceTest {

    @Test
    void refreshesTokenThroughTheSharedRestTemplatesConverters() {
        // Mirrors RestTemplateConfig.restTemplate()'s converter list exactly.
        var restTemplate = new RestTemplate(
                List.of(
                        new StringHttpMessageConverter(),
                        new FormHttpMessageConverter(),
                        new JacksonJsonHttpMessageConverter()));
        var server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://auth.example/token"))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(formData()))
                .andRespond(
                        withSuccess(
                                """
                                        {"access_token": "new-access", "refresh_token": "new-refresh", "expires_in": 3600}
                                        """,
                                MediaType.APPLICATION_JSON));

        var registration = ClientRegistration.withRegistrationId("eclipse")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://auth.example/authorize")
                .tokenUri("http://auth.example/token")
                .build();
        var clientRegistrationRepository = Mockito.mock(ClientRegistrationRepository.class);
        Mockito.when(clientRegistrationRepository.findByRegistrationId("eclipse")).thenReturn(registration);

        var user = new UserData();
        user.setId(1L);
        user.setEclipseToken(
                new AuthToken(
                        "old-access",
                        Instant.now().minusSeconds(7200),
                        Instant.now().minusSeconds(3600), // expired -> triggers a refresh
                        Set.of(),
                        "refresh-token",
                        Instant.now().plusSeconds(3600))); // refresh token itself still valid

        var transactions = Mockito.mock(TransactionTemplate.class);
        Mockito.when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<AuthToken> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        var entityManager = Mockito.mock(EntityManager.class);
        Mockito.when(entityManager.find(UserData.class, 1L)).thenReturn(user);

        var service = new EclipseTokenService(transactions, entityManager, clientRegistrationRepository, restTemplate);

        var refreshed = service.getActiveEclipseToken(user);

        assertThat(refreshed).isNotNull();
        assertThat(refreshed.accessToken()).isEqualTo("new-access");
        server.verify();
    }

    private static MultiValueMap<String, String> formData() {
        var data = new LinkedMultiValueMap<String, String>();
        data.add("grant_type", "refresh_token");
        data.add("client_id", "client-id");
        data.add("client_secret", "client-secret");
        data.add("refresh_token", "refresh-token");
        return data;
    }
}
