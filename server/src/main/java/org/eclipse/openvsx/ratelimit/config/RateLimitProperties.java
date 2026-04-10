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
package org.eclipse.openvsx.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = RateLimitProperties.PROPERTY_PREFIX)
public class RateLimitProperties {

    public static final String PROPERTY_PREFIX = "ovsx.rate-limit";

    /**
     * Enables or disables the tiered rate limit mechanism.
     */
    @NotNull
    private Boolean enabled = false;

    /**
     * The token prefix to use when generating a new rate limit token.
     */
    @NotNull
    private String tokenPrefix = "";

    @NotBlank
    private String ipAddressFunction = "getRemoteAddr()";

    @Valid
    private UsageStatsProperties usageStats = new UsageStatsProperties();

    @Valid
    private List<RateLimitFilterProperties> filters = new ArrayList<>();

    @NotBlank
    private String defaultHttpContentType = "application/json";

    @NotNull
    private HttpStatus defaultHttpStatusCode = HttpStatus.TOO_MANY_REQUESTS;

    /**
     * The HTTP content which should be used in case of rate limiting
     */
    private String defaultHttpResponseBody = "{ \"message\": \"Too many requests!\" }";

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getIpAddressFunction() {
        return ipAddressFunction;
    }

    public void setIpAddressFunction(String ipAddressFunction) {
        this.ipAddressFunction = ipAddressFunction;
    }

    public UsageStatsProperties getUsageStats() {
        return usageStats;
    }

    public void setUsageStats(UsageStatsProperties usageData) {
        this.usageStats = usageData;
    }

    public List<RateLimitFilterProperties> getFilters() {
        return filters;
    }

    public void setFilters(List<RateLimitFilterProperties> filters) {
        this.filters = filters;
    }

    public String getDefaultHttpContentType() {
        return defaultHttpContentType;
    }

    public void setDefaultHttpContentType(String defaultHttpContentType) {
        this.defaultHttpContentType = defaultHttpContentType;
    }

    public HttpStatus getDefaultHttpStatusCode() {
        return defaultHttpStatusCode;
    }

    public void setDefaultHttpStatusCode(HttpStatus defaultHttpStatusCode) {
        this.defaultHttpStatusCode = defaultHttpStatusCode;
    }

    public String getDefaultHttpResponseBody() {
        return defaultHttpResponseBody;
    }

    public void setDefaultHttpResponseBody(String defaultHttpResponseBody) {
        this.defaultHttpResponseBody = defaultHttpResponseBody;
    }
}