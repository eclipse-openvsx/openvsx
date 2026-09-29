/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jakarta.validation.constraints.NotNull;

@JsonInclude(Include.NON_NULL)
public class UserPublishInfoJson extends ResultJson {

    public static UserPublishInfoJson error(String message) {
        var userPublishInfo = new UserPublishInfoJson();
        userPublishInfo.setError(message);
        return userPublishInfo;
    }

    @NotNull
    private UserJson user;

    @NotNull
    private List<ExtensionJson> extensions;

    @NotNull
    private Integer activeAccessTokenNum;

    public UserJson getUser() {
        return user;
    }

    public void setUser(UserJson user) {
        this.user = user;
    }

    public List<ExtensionJson> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<ExtensionJson> extensions) {
        this.extensions = extensions;
    }

    public Integer getActiveAccessTokenNum() {
        return activeAccessTokenNum;
    }

    public void setActiveAccessTokenNum(Integer activeAccessTokenNum) {
        this.activeAccessTokenNum = activeAccessTokenNum;
    }
}
