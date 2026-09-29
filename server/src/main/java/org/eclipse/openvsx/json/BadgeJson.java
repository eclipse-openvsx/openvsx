/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "Badge",
    description = "A badge to be shown in the sidebar of the extension page in the registry"
)
@JsonInclude(Include.NON_NULL)
public class BadgeJson {

    @Schema(description = "Image URL of the badge")
    private String url;

    @Schema(description = "The link users will follow when clicking the badge")
    private String href;

    private String description;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BadgeJson badgeJson = (BadgeJson) o;
        return Objects.equals(url, badgeJson.url)
                && Objects.equals(href, badgeJson.href)
                && Objects.equals(description, badgeJson.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, href, description);
    }
}
