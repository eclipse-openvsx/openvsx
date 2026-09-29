/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Allow list / block list entry for individual files (by SHA256 hash).
 * Used to approve or block specific file content across all future extensions.
 * One decision per file hash (unique constraint on file_hash).
 */
@Entity
@Table(name = "file_decision")
public class FileDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Decision value: file is allowed */
    public static final String ALLOWED = "ALLOWED";

    /** Decision value: file is blocked */
    public static final String BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(generator = "fileDecisionSeq")
    @SequenceGenerator(name = "fileDecisionSeq", sequenceName = "file_decision_seq", allocationSize = 1)
    private long id;

    /** SHA256 hash uniquely identifying the file content (unique - one decision per hash) */
    @Column(name = "file_hash", nullable = false, unique = true, length = 128)
    private String fileHash;

    /** Original file name for reference */
    @Column(name = "file_name", length = 1024)
    private String fileName;

    /** File extension/type */
    @Column(name = "file_type", length = 50)
    private String fileType;

    /** Decision: ALLOWED or BLOCKED */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    /** Admin who made the decision */
    @ManyToOne
    @JoinColumn(name = "decided_by_id", nullable = false)
    private UserData decidedBy;

    /** When the decision was made */
    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    /** Extension display name where file was first encountered */
    @Column(name = "display_name", length = 255)
    private String displayName;

    /** Namespace name where file was first encountered */
    @Column(name = "namespace_name", length = 255)
    private String namespaceName;

    /** Extension name where file was first encountered */
    @Column(name = "extension_name", length = 255)
    private String extensionName;

    /** Publisher name where file was first encountered */
    @Column(name = "publisher", length = 255)
    private String publisher;

    /** Version where file was first encountered */
    @Column(name = "version", length = 100)
    private String version;

    /** Optional link to the scan that triggered this decision (nullable) */
    @ManyToOne
    @JoinColumn(name = "scan_id")
    private ExtensionScan scan;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public UserData getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UserData decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecidedByName() {
        if (decidedBy == null) {
            return null;
        }
        var email = decidedBy.getEmail();
        return (email != null && !email.isBlank()) ? email : decidedBy.getLoginName();
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
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

    public ExtensionScan getScan() {
        return scan;
    }

    public void setScan(ExtensionScan scan) {
        this.scan = scan;
    }

    public boolean isAllowed() {
        return ALLOWED.equals(decision);
    }

    public boolean isBlocked() {
        return BLOCKED.equals(decision);
    }

    public static FileDecision allowed(String fileHash, UserData decidedBy) {
        var decision = new FileDecision();
        decision.setFileHash(fileHash);
        decision.setDecision(ALLOWED);
        decision.setDecidedBy(decidedBy);
        decision.setDecidedAt(TimeUtil.getCurrentUTC());
        return decision;
    }

    public static FileDecision blocked(String fileHash, UserData decidedBy) {
        var decision = new FileDecision();
        decision.setFileHash(fileHash);
        decision.setDecision(BLOCKED);
        decision.setDecidedBy(decidedBy);
        decision.setDecidedAt(TimeUtil.getCurrentUTC());
        return decision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileDecision that = (FileDecision) o;
        return id == that.id
                && Objects.equals(fileHash, that.fileHash)
                && Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fileHash, decision);
    }
}
