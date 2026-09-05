/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx;

import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import org.eclipse.openvsx.mirror.MirrorConfig;

@Configuration
public class RestTemplateConfig {

    /**
     * Use to serve requests to ensure that response is given within 30 seconds.
     * VS Code does not wait more than it and will time out a request.
     */
    @Bean
    public HttpConnPoolConfig foregroundHttpConnPool(
            @Value("${ovsx.foregroundHttpConnPool.maxTotal:20}") int maxTotal,
            @Value("${ovsx.foregroundHttpConnPool.defaultMaxPerRoute:20}") int defaultMaxPerRoute,
            @Value("${ovsx.foregroundHttpConnPool.connectionRequestTimeout:10000}") int connectionRequestTimeout,
            @Value("${ovsx.foregroundHttpConnPool.connectTimeout:10000}") int connectTimeout,
            @Value("${ovsx.foregroundHttpConnPool.socketTimeout:10000}") int socketTimeout
    ) {
        return createHttpConnPoolConfig(
                maxTotal,
                defaultMaxPerRoute,
                connectionRequestTimeout,
                connectTimeout,
                socketTimeout);
    }

    /**
     * Use to download files in background processing for requests not requiring redirects.
     * Never use to serve requests. Overall response time should be within 30secs.
     */
    @Bean
    public HttpConnPoolConfig backgroundHttpConnPool(
            @Value("${ovsx.backgroundHttpConnPool.maxTotal:20}") int maxTotal,
            @Value("${ovsx.backgroundHttpConnPool.defaultMaxPerRoute:20}") int defaultMaxPerRoute,
            @Value("${ovsx.backgroundHttpConnPool.connectionRequestTimeout:30000}") int connectionRequestTimeout,
            @Value("${ovsx.backgroundHttpConnPool.connectTimeout:30000}") int connectTimeout,
            @Value("${ovsx.backgroundHttpConnPool.socketTimeout:60000}") int socketTimeout
    ) {
        return createHttpConnPoolConfig(
                maxTotal,
                defaultMaxPerRoute,
                connectionRequestTimeout,
                connectTimeout,
                socketTimeout);
    }

    private HttpConnPoolConfig createHttpConnPoolConfig(
            int maxTotal,
            int defaultMaxPerRoute,
            int connectionRequestTimeout,
            int connectTimeout,
            int socketTimeout
    ) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(defaultMaxPerRoute);
        return new HttpConnPoolConfig(
                connectionManager,
                connectionRequestTimeout,
                connectTimeout,
                socketTimeout);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, HttpConnPoolConfig foregroundHttpConnPool) {
        var httpClient = createHttpClientBuilder(foregroundHttpConnPool).build();
        return builder
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory();
                    f.setHttpClient(httpClient);
                    return f;
                })
                .messageConverters(
                        new StringHttpMessageConverter(),
                        new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Bean
    public RestTemplate nonRedirectingRestTemplate(
            RestTemplateBuilder builder,
            HttpConnPoolConfig foregroundHttpConnPool
    ) {
        var httpClient = createHttpClientBuilder(foregroundHttpConnPool).disableRedirectHandling().build();
        return builder
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory();
                    f.setHttpClient(httpClient);
                    return f;
                })
                .build();
    }

    @Bean
    public RestTemplate backgroundRestTemplate(RestTemplateBuilder builder, HttpConnPoolConfig backgroundHttpConnPool) {
        var httpClient = createHttpClientBuilder(backgroundHttpConnPool).build();
        DefaultUriBuilderFactory defaultUriBuilderFactory = new DefaultUriBuilderFactory();
        defaultUriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        return builder
                .uriTemplateHandler(defaultUriBuilderFactory)
                .messageConverters(
                        new StringHttpMessageConverter(),
                        new JacksonJsonHttpMessageConverter())
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory();
                    f.setHttpClient(httpClient);
                    return f;
                })
                .build();
    }

    @Bean
    public RestTemplate backgroundNonRedirectingRestTemplate(
            RestTemplateBuilder builder,
            HttpConnPoolConfig backgroundHttpConnPool
    ) {
        var httpClient = createHttpClientBuilder(backgroundHttpConnPool).disableRedirectHandling().build();
        DefaultUriBuilderFactory defaultUriBuilderFactory = new DefaultUriBuilderFactory();
        defaultUriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        return builder
                .uriTemplateHandler(defaultUriBuilderFactory)
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory();
                    f.setHttpClient(httpClient);
                    return f;
                })
                .build();
    }

    @Bean
    public RestTemplate vsCodeIdRestTemplate(
            MirrorConfig mirrorConfig,
            RestTemplate restTemplate,
            RestTemplate backgroundRestTemplate
    ) {
        return mirrorConfig.isEnabled() ? backgroundRestTemplate : restTemplate;
    }

    private HttpClientBuilder createHttpClientBuilder(HttpConnPoolConfig httpConnPoolConfig) {
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(httpConnPoolConfig.connectTimeout(), TimeUnit.MILLISECONDS))
                .build();
        httpConnPoolConfig.connectionManager().setDefaultConnectionConfig(connectionConfig);
        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.of(httpConnPoolConfig.connectionRequestTimeout(), TimeUnit.MILLISECONDS))
                .build();
        return HttpClientBuilder
                .create()
                .setConnectionManager(httpConnPoolConfig.connectionManager())
                .setDefaultRequestConfig(requestConfig);
    }

    public record HttpConnPoolConfig(
            PoolingHttpClientConnectionManager connectionManager,
            int connectionRequestTimeout,
            int connectTimeout,
            int socketTimeout
    ) {}
}
