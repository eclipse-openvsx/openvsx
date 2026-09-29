/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.search;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@Configuration
@Profile("!test")
public class SearchConfig extends ElasticsearchConfiguration {

    protected final Logger logger = LoggerFactory.getLogger(SearchConfig.class);

    @Value("${ovsx.elasticsearch.host:}")
    String searchHost;

    @Value("${ovsx.elasticsearch.ssl:false}")
    boolean useSsl;

    @Value("${ovsx.elasticsearch.username:}")
    String username;

    @Value("${ovsx.elasticsearch.password:}")
    String password;

    @Value("${ovsx.elasticsearch.truststore:}")
    String trustStore;

    /**
     * Name from https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html#sslcontext-algorithms
     */
    @Value("${ovsx.elasticsearch.truststoreProtocol:TLSv1.2}")
    String trustStoreProtocol;

    @Value("${ovsx.elasticsearch.truststorePassword:}")
    String trustStorePassword;

    @Override
    public ClientConfiguration clientConfiguration() {
        var builder = ClientConfiguration.builder();
        var connected = StringUtils.isEmpty(searchHost)
                ? builder.connectedToLocalhost()
                : builder.connectedTo(searchHost);
        var secure = useSsl ? connected.usingSsl(sslContext()) : connected;
        var authenticated = StringUtils.isEmpty(username) || StringUtils.isEmpty(password)
                ? secure
                : secure.withBasicAuth(username, password);

        return authenticated.build();
    }

    /**
     * Returns a new trust store if {@link #trustStore} (and {@link #trustStorePassword}) properties
     * are non empty. Returns {@link SSLContext#getDefault() default} SSLContext otherwise.
     */
    private SSLContext sslContext() {
        if (!StringUtils.isEmpty(trustStore)) {
            var sslContextBuilder = SSLContextBuilder.create().setProtocol(trustStoreProtocol);
            if (!StringUtils.isEmpty(trustStorePassword)) {
                try {
                    sslContextBuilder.loadTrustMaterial(new File(trustStore), trustStorePassword.toCharArray());
                } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
                    logger.error("Unable to load password protected trust material {}", trustStore, e);
                }
            } else {
                try {
                    sslContextBuilder.loadTrustMaterial(new File(trustStore));
                } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e) {
                    logger.error("Unable to load trust material {}", trustStore, e);
                }
            }
            try {
                return sslContextBuilder.build();
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                logger.error("Error while creating SSLContext", e);
            }
        }
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error while getting default SSLContext", e);
        }
    }
}
