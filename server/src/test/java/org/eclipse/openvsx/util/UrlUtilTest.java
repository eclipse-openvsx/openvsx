/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.util;

import java.util.ArrayList;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class UrlUtilTest {

    @Mock
    private HttpServletRequest request;

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void testCreateApiFileUrl() throws Exception {
        var baseUrl = "http://localhost/";
        assertThat(
                UrlUtil.createApiFileUrl(baseUrl, "foo", "bar", "linux-x64", "0.1.0", "foo.bar-0.1.0@linux-x64.vsix"))
                .isEqualTo("http://localhost/api/foo/bar/linux-x64/0.1.0/file/foo.bar-0.1.0@linux-x64.vsix");
    }

    @Test
    void testCreateApiFileUrlUniversalTarget() throws Exception {
        var baseUrl = "http://localhost/";
        assertThat(UrlUtil.createApiFileUrl(baseUrl, "foo", "bar", "universal", "0.1.0", "foo.bar-0.1.0.vsix"))
                .isEqualTo("http://localhost/api/foo/bar/0.1.0/file/foo.bar-0.1.0.vsix");
    }

    @Test
    void testCreateApiVersionUrl() throws Exception {
        var baseUrl = "http://localhost/";
        assertThat(UrlUtil.createApiVersionUrl(baseUrl, "foo", "bar", "universal", "1.0.0"))
                .isEqualTo("http://localhost/api/foo/bar/universal/1.0.0");
    }

    @Test
    void testCreateApiVersionUrlNoTarget() throws Exception {
        var baseUrl = "http://localhost/";
        assertThat(UrlUtil.createApiVersionUrl(baseUrl, "foo", "bar", null, "1.0.0"))
                .isEqualTo("http://localhost/api/foo/bar/1.0.0");
    }

    @Test
    void testApiUrl() throws Exception {
        var baseUrl = "http://localhost/";
        assertThat(UrlUtil.createApiUrl(baseUrl, "api", "foo", "b\ta/r"))
                .isEqualTo("http://localhost/api/foo/b%09a%2Fr");
    }

    @Test
    void testQuery() throws Exception {
        var url = "http://localhost/api/foo";
        assertThat(UrlUtil.addQuery(url, "a", "1", "b", null, "c", "b\ta/r"))
                .isEqualTo("http://localhost/api/foo?a=1&c=b%09a/r");
    }

    // Check base URL is localhost:8080 if there is no XForwarded headers
    @Test
    void testWithoutXForwarded() throws Exception {
        doReturn("http").when(request).getScheme();
        doReturn("localhost").when(request).getServerName();
        doReturn(8080).when(request).getServerPort();
        doReturn("/").when(request).getContextPath();
        assertThat(UrlUtil.getBaseUrl(request)).isEqualTo("http://localhost:8080/");
    }

    // Check base URL is using XForwarded headers
    @Test
    void testWithXForwarded() throws Exception {
        // basic request
        doReturn("http").when(request).getScheme();
        doReturn("localhost").when(request).getServerName();
        doReturn(8080).when(request).getServerPort();
        doReturn("/").when(request).getContextPath();

        // XForwarded content
        doReturn("https").when(request).getHeader("X-Forwarded-Proto");
        var items = new ArrayList<String>();
        items.add("open-vsx.org");
        doReturn(Collections.enumeration(items)).when(request).getHeaders("X-Forwarded-Host");
        doReturn("/openvsx").when(request).getHeader("X-Forwarded-Prefix");
        assertThat(UrlUtil.getBaseUrl(request)).isEqualTo("https://open-vsx.org/openvsx/");
    }

    // Check base URL is using array X-Forwarded-Host headers
    @Test
    void testWithXForwardedHostArray() throws Exception {
        // basic request
        doReturn("http").when(request).getScheme();
        doReturn("localhost").when(request).getServerName();
        doReturn(8080).when(request).getServerPort();
        doReturn("/").when(request).getContextPath();

        // XForwarded content
        doReturn("https").when(request).getHeader("X-Forwarded-Proto");
        var items = new ArrayList<String>();
        items.add("open-vsx.org");
        items.add("foo.com");
        items.add("bar.com");
        doReturn(Collections.enumeration(items)).when(request).getHeaders("X-Forwarded-Host");
        doReturn("/openvsx").when(request).getHeader("X-Forwarded-Prefix");
        assertThat(UrlUtil.getBaseUrl(request)).isEqualTo("https://open-vsx.org/openvsx/");
    }

    @Test
    void testGetBaseUrlWithConfiguredUrlPrefersIt() throws Exception {
        assertThat(UrlUtil.getBaseUrl("https://api.example.org/")).isEqualTo("https://api.example.org/");
    }

    @Test
    void testGetBaseUrlWithoutConfiguredUrlFallsBackToEmpty() throws Exception {
        // called outside of a web request context, so the request-derived fallback is empty
        assertThat(UrlUtil.getBaseUrl("")).isEmpty();
        assertThat(UrlUtil.getBaseUrl((String) null)).isEmpty();
    }

    // Check base URL is using comma separated X-Forwarded-Host headers
    @Test
    void testWithXForwardedHostCommaSeparated() throws Exception {
        // basic request
        doReturn("http").when(request).getScheme();
        doReturn("localhost").when(request).getServerName();
        doReturn(8080).when(request).getServerPort();
        doReturn("/").when(request).getContextPath();

        // XForwarded content
        doReturn("https").when(request).getHeader("X-Forwarded-Proto");
        var items = new ArrayList<String>();
        items.add("open-vsx.org, foo.com, bar.com");
        doReturn(Collections.enumeration(items)).when(request).getHeaders("X-Forwarded-Host");
        doReturn("/openvsx").when(request).getHeader("X-Forwarded-Prefix");
        assertThat(UrlUtil.getBaseUrl(request)).isEqualTo("https://open-vsx.org/openvsx/");
    }

}
