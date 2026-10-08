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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.jspecify.annotations.Nullable;

/**
 * Rate limiting of the traffic a Fastly Compute service serves on the origin's behalf.
 * See {@code ovsx.rate-limit.edge.*} in doc/configuration.md.
 */
public class EdgeProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String HEADER_SECRET = "X-OpenVSX-Edge-Secret";
    public static final String HEADER_CUSTOMER = "X-OpenVSX-Edge-Customer";
    public static final String HEADER_CLIENT_IP = "X-OpenVSX-Client-IP";

    private boolean enabled = false;

    /** Proves a request or a usage batch came through the edge. */
    private String sharedSecret = "";

    private String fastlyApiUrl = "https://api.fastly.com";

    private String fastlyApiToken = "";

    private String kvStoreId = "";

    private String unblockSchedule = "* * * * *";

    /**
     * Whether {@code presented} is the configured shared secret. Always false while the edge is
     * disabled or no secret is configured, so the edge headers cannot be honoured by accident.
     */
    public boolean isTrusted(@Nullable String presented) {
        if (!enabled || sharedSecret.isEmpty() || presented == null) {
            return false;
        }

        return MessageDigest.isEqual(
                sharedSecret.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getFastlyApiUrl() {
        return fastlyApiUrl;
    }

    public void setFastlyApiUrl(String fastlyApiUrl) {
        this.fastlyApiUrl = fastlyApiUrl;
    }

    public String getFastlyApiToken() {
        return fastlyApiToken;
    }

    public void setFastlyApiToken(String fastlyApiToken) {
        this.fastlyApiToken = fastlyApiToken;
    }

    public String getKvStoreId() {
        return kvStoreId;
    }

    public void setKvStoreId(String kvStoreId) {
        this.kvStoreId = kvStoreId;
    }

    public String getUnblockSchedule() {
        return unblockSchedule;
    }

    public void setUnblockSchedule(String unblockSchedule) {
        this.unblockSchedule = unblockSchedule;
    }
}