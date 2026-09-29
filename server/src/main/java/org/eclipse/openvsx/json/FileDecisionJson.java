/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "FileDecision",
    description = "File allow/block list decision"
)
@JsonInclude(Include.NON_NULL)
public class FileDecisionJson extends ResultJson {

    @Schema(description = "Unique identifier for the file decision")
    private String id;

    @Schema(description = "ID of the scan that originally flagged this file")
    private String scanId;

    @Schema(description = "Path to the file within the extension")
    private String fileName;

    @Schema(description = "SHA256 hash of the file")
    private String fileHash;

    @Schema(description = "File extension/type")
    private String fileType;

    @Schema(description = "The admin decision for this file")
    private String decision;

    @Schema(description = "Email of the admin who made the decision")
    private String decidedBy;

    @Schema(description = "When the decision was made (UTC)")
    private String dateDecided;

    @Schema(description = "Human-readable name of the extension containing this file")
    private String displayName;

    @Schema(description = "Extension namespace")
    private String namespace;

    @Schema(description = "Technical name of the extension")
    private String extensionName;

    @Schema(description = "Publisher name")
    private String publisher;

    @Schema(description = "Extension version when decision was made")
    private String version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDateDecided() {
        return dateDecided;
    }

    public void setDateDecided(String dateDecided) {
        this.dateDecided = dateDecided;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

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

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
