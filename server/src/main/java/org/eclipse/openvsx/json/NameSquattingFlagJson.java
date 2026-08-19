/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An extension flagged by the name squatting publisher check, with every finding recorded for it.
 * <p>
 * Findings are grouped per extension rather than per version, because both moderation decisions an
 * administrator can take - clearing the check as a false positive and deactivating the extension -
 * apply to the extension as a whole.
 */
@Schema(
    name = "NameSquattingFlag",
    description = "An extension flagged by the name squatting publisher check"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingFlagJson {

    @Schema(description = "Namespace of the flagged extension")
    private String namespace;

    @Schema(description = "Name of the flagged extension")
    private String extensionName;

    @Schema(description = "Display name taken from the most recent flagged version")
    private String displayName;

    @Schema(description = "Publisher who uploaded the most recent flagged version")
    private String publisher;

    @Schema(description = "Profile URL of that publisher")
    private String publisherUrl;

    @Schema(
        description = "What became of the extension after the check ran: PUBLISHED when it is live, "
                + "DEACTIVATED when it exists but all versions are inactive, and REJECTED when "
                + "publication was blocked so the extension was never created",
        allowableValues = { "PUBLISHED", "DEACTIVATED", "REJECTED" }
    )
    private String state;

    @Schema(description = "Number of active versions the extension currently has")
    private int activeVersionCount;

    @Schema(description = "Number of name squatting findings recorded for the extension")
    private int findingCount;

    @Schema(description = "When the extension was first flagged (UTC)")
    private String dateFirstDetected;

    @Schema(description = "When the extension was most recently flagged (UTC)")
    private String dateLastDetected;

    @Schema(description = "The individual findings, most recent first")
    private List<NameSquattingFindingJson> findings;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getPublisherUrl() {
        return publisherUrl;
    }

    public void setPublisherUrl(String publisherUrl) {
        this.publisherUrl = publisherUrl;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getActiveVersionCount() {
        return activeVersionCount;
    }

    public void setActiveVersionCount(int activeVersionCount) {
        this.activeVersionCount = activeVersionCount;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(int findingCount) {
        this.findingCount = findingCount;
    }

    public String getDateFirstDetected() {
        return dateFirstDetected;
    }

    public void setDateFirstDetected(String dateFirstDetected) {
        this.dateFirstDetected = dateFirstDetected;
    }

    public String getDateLastDetected() {
        return dateLastDetected;
    }

    public void setDateLastDetected(String dateLastDetected) {
        this.dateLastDetected = dateLastDetected;
    }

    public List<NameSquattingFindingJson> getFindings() {
        return findings;
    }

    public void setFindings(List<NameSquattingFindingJson> findings) {
        this.findings = findings;
    }
}
