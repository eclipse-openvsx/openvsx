/*******************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.jspecify.annotations.Nullable;

@JsonInclude(Include.NON_NULL)
public class AccessTokenJson extends ResultJson {

    public static AccessTokenJson error(String message) {
        var result = new AccessTokenJson();
        result.setError(message);
        return result;
    }

    private Long id;

    @Nullable
    private String value;

    private String createdTimestamp;

    @Nullable
    private String accessedTimestamp;

    @Nullable
    private String expiresTimestamp;

    private boolean notified;

    private String description;

    private String deleteTokenUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }

    public String getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(String createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    @Nullable
    public String getAccessedTimestamp() {
        return accessedTimestamp;
    }

    public void setAccessedTimestamp(@Nullable String accessedTimestamp) {
        this.accessedTimestamp = accessedTimestamp;
    }

    @Nullable
    public String getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public void setExpiresTimestamp(@Nullable String expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDeleteTokenUrl() {
        return deleteTokenUrl;
    }

    public void setDeleteTokenUrl(String deleteTokenUrl) {
        this.deleteTokenUrl = deleteTokenUrl;
    }
}
