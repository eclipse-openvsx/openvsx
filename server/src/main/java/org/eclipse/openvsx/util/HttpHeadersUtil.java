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
package org.eclipse.openvsx.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.tika.Tika;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.eclipse.openvsx.storage.StorageUtil;

public class HttpHeadersUtil {

    /**
     * Fallback header for personal access tokens, used only when {@code Authorization} is already
     * claimed by a standard auth scheme (e.g. the CLI's own Basic auth to a fronting reverse proxy).
     * {@code Authorization: Bearer} is preferred (see {@link #resolveAccessToken}); this and the
     * legacy {@code token} query parameter are the fallbacks. No {@code X-} prefix per RFC 6648.
     */
    public static final String TOKEN_HEADER = "OpenVSX-Token";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final MediaType APPLICATION_ZIP = MediaType.valueOf("application/zip");
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private static final Tika tika = new Tika();

    private static final int MAX_FILENAME_LENGTH = 255;

    // Characters explicitly dangerous in Content-Disposition or HTTP headers
    private static final Set<Character> FORBIDDEN_CHARS = Set.of(
            '/',
            '\\',
            ':',
            '*',
            '?',
            '"',
            '<',
            '>',
            '|', // filesystem/shell
            '\r',
            '\n',
            '\0', // header injection
            ';',
            ',',
            '=' // header parameter delimiters
    );

    private static final Set<String> TEXT_VIEWABLE_MEDIA_TYPES = Set.of(
            "text/plain",
            "text/html",
            "text/markdown",
            "text/css",
            "text/javascript");

    private static final Set<String> PASSTHROUGH_MEDIA_TYPES = Set.of(
            "application/json",
            "application/xml");

    public static final String CSP_PREVENT_ALL = "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox";

    private HttpHeadersUtil() {
    }

    /**
     * Resolves a personal access token, checking in order: the {@code Authorization: Bearer} header,
     * the {@value #TOKEN_HEADER} header, then {@code queryToken} (the legacy {@code token} query
     * parameter). A blank value at any step is treated as absent and falls through to the next.
     */
    public static @Nullable String resolveAccessToken(HttpServletRequest request, @Nullable String queryToken) {
        var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            var bearerToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (!bearerToken.isBlank()) {
                return bearerToken;
            }
        }
        var headerToken = request.getHeader(TOKEN_HEADER);
        return (headerToken != null && !headerToken.isBlank()) ? headerToken.trim() : queryToken;
    }

    public static HttpHeaders getForwardedHeaders() {
        var headers = new HttpHeaders();
        try {
            var requestAttrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            var request = requestAttrs.getRequest();

            var it = request.getHeaderNames();
            while (it.hasMoreElements()) {
                var header = it.nextElement();
                headers.add(header, request.getHeader(header));
            }
        } catch (IllegalStateException _) {
        }
        headers.remove(HttpHeaders.HOST);
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        // Neither credential is meant for the upstream this is forwarded to (see callers).
        headers.remove(HttpHeaders.AUTHORIZATION);
        headers.remove(TOKEN_HEADER);
        return headers;
    }

    public static HttpHeaders getAcceptJsonHeaders() {
        var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    public static HttpHeaders createFileResponseHeaders(@NonNull Path file) {
        var fileName = file.getFileName().toString();
        return createFileResponseHeaders(file, fileName);
    }

    public static HttpHeaders createFileResponseHeaders(@NonNull Path file, @Nullable String fileName) {
        try (var inputStream = Files.newInputStream(file)) {
            return createFileResponseHeaders(inputStream, fileName);
        } catch (IOException ex) {
            return createFileResponseHeaders((InputStream) null, fileName);
        }
    }

    public static HttpHeaders createJsonFileResponseHeaders() {
        return createFileResponseHeaders((InputStream) null, "data.json");
    }

    public static HttpHeaders createFileResponseHeaders(@Nullable InputStream inputStream, @Nullable String fileName) {
        HttpHeaders headers = new HttpHeaders();

        // by default, we treat all files as octet stream
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        headers.set("X-Content-Type-Options", "nosniff"); // prevents MIME sniffing
        headers.set("X-Frame-Options", "DENY"); // prevents framing

        boolean viewableAsText = false;
        boolean forceDownload = true;

        // treat vsix and sigzip files special:
        //   - set content-disposition: attachment
        //   - no explicit cache control

        if (fileName != null && fileName.endsWith(".vsix")) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        } else if (fileName != null && fileName.endsWith(".sigzip")) {
            headers.setContentType(APPLICATION_ZIP);
        } else {
            try {
                var mediaTypeString = tika.detect(inputStream, fileName);
                if (TEXT_VIEWABLE_MEDIA_TYPES.contains(mediaTypeString)) {
                    viewableAsText = true;
                } else if (PASSTHROUGH_MEDIA_TYPES.contains(mediaTypeString)) {
                    // allow some media types to passthrough as is
                    forceDownload = false;
                    headers.setContentType(MediaType.valueOf(mediaTypeString));
                }
            } catch (IOException _) {
                // if we can not determine the media type, do not treat it as text
            }

            // add a default cache control for all other served files
            headers.setCacheControl(StorageUtil.getCacheControl(fileName));
        }

        if (viewableAsText) {
            // if we determined that the file can be safely viewed in its source form
            // set a text/plain content type to prevent any rendering in the browser
            headers.setContentType(TEXT_PLAIN_UTF8);
        } else if (forceDownload) {
            // files not viewable as text need to be downloaded and not displayed inline
            String sanitizedFileName = sanitize(fileName);
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(sanitizedFileName)
                    .build();
            headers.setContentDisposition(disposition);
        }

        // as a safety net, set the strictest possible CSP as we are serving user-controlled input
        headers.set("Content-Security-Policy", CSP_PREVENT_ALL);

        return headers;
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }

        // Strip any directory components an attacker may have included
        // (handles both Unix and Windows path separators)
        String sanitized = getSanitized(filename);

        // Collapse to just the extension if the name part is empty
        if (sanitized.isBlank()) {
            return "download";
        }

        // Prevent hidden files / relative path tricks
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.isBlank()) {
            return "download";
        }

        // Truncate, but preserve the extension
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            int dot = sanitized.lastIndexOf('.');
            if (dot > 0 && dot > sanitized.length() - 16) {
                // Has a short extension — preserve it
                String ext = sanitized.substring(dot); // e.g. ".tar.gz" won't work but ".gz" will
                String name = sanitized.substring(0, dot);
                name = name.substring(0, MAX_FILENAME_LENGTH - ext.length());
                sanitized = name + ext;
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
            }
        }

        return sanitized;
    }

    private static @NonNull String getSanitized(String filename) {
        String stripped = filename;
        int lastSep = Math.max(
                filename.lastIndexOf('/'),
                filename.lastIndexOf('\\'));
        if (lastSep >= 0) {
            stripped = filename.substring(lastSep + 1);
        }

        // Remove forbidden characters
        StringBuilder sb = new StringBuilder();
        for (char c : stripped.toCharArray()) {
            if (!FORBIDDEN_CHARS.contains(c) && c >= 0x20) { // drop control chars
                sb.append(c);
            }
        }

        return sb.toString().trim();
    }
}
