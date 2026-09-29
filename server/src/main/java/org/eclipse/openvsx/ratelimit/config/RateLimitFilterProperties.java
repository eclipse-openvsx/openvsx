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

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;

public class RateLimitFilterProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The URL to which the filter should be registered
     */
    @NotBlank
    private String url = ".*";

    @AssertTrue(message = "Invalid filter URL regex pattern.")
    @JsonIgnore
    public boolean isUrlValid() {
        try {
            Pattern.compile(url);
            return !url.equals("/*");
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * The filter order has a default of the highest precedence reduced by 10
     */
    @NotNull
    private Integer filterOrder = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * The HTTP Content-Type which should be returned
     */
    private String httpContentType;

    /**
     * The HTTP status code which should be returned when limiting the rate.
     */
    private HttpStatus httpStatusCode;

    /**
     * The HTTP content which should be used in case of rate limiting
     */
    private String httpResponseBody;

    private Map<String, String> httpResponseHeaders = new HashMap<>();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(Integer filterOrder) {
        this.filterOrder = filterOrder;
    }

    public String getHttpContentType() {
        return httpContentType;
    }

    public void setHttpContentType(String httpContentType) {
        this.httpContentType = httpContentType;
    }

    public HttpStatus getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(HttpStatus httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getHttpResponseBody() {
        return httpResponseBody;
    }

    public void setHttpResponseBody(String httpResponseBody) {
        this.httpResponseBody = httpResponseBody;
    }

    public Map<String, String> getHttpResponseHeaders() {
        return httpResponseHeaders;
    }

    public void setHttpResponseHeaders(Map<String, String> httpResponseHeaders) {
        this.httpResponseHeaders = httpResponseHeaders;
    }
}
