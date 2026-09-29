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

package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * One publicly visible transition of an {@link ExtensionVersion}: its publication, an administrative
 * deactivation, a reactivation, or its removal.
 * <p>
 * The log this builds up is append-only and is what the registry changes feed
 * ({@code /api/-/version-changes}) serves: an entry is never updated or reordered once written, so
 * consumers can page through the feed without skipping or repeating entries, and a version that
 * transitions repeatedly is reported once per transition instead of moving around in the feed.
 * <p>
 * Entries outlive the version they talk about. Purging a version only detaches its entries, clearing
 * {@link #getExtensionVersion()} while the copied coordinates keep identifying it, so that the feed can
 * still report that the version went away and the transitions consumers were already told about are not
 * retroactively withdrawn. That is also why the coordinates are copied onto every entry rather than
 * read from the version: there may no longer be a version to read them from.
 */
@Entity
@Table(name = "extension_version_change")
public class ExtensionVersionChange {

    @Id
    @GeneratedValue(generator = "extensionVersionChangeSeq")
    @SequenceGenerator(
        name = "extensionVersionChangeSeq",
        sequenceName = "extension_version_change_seq",
        allocationSize = 1
    )
    private long id;

    /**
     * The version this entry reports a transition of, or {@code null} once that version has been purged.
     * Only used to look up the history of a version that still exists -- the feed itself reads the
     * copied coordinates below, so that it keeps working for the entries this has been cleared on.
     */
    @ManyToOne
    @JoinColumn(
        name = "extension_version_id",
        foreignKey = @ForeignKey(name = "extension_version_change_extension_version_id_fkey")
    )
    private ExtensionVersion extensionVersion;

    /**
     * Namespace of the version, copied from it so that the entry outlives it.
     */
    private String namespace;

    /**
     * Name of the extension the version belongs to, copied from it so that the entry outlives it.
     */
    private String extension;

    /**
     * Version string, copied from the version so that the entry outlives it.
     */
    private String version;

    /**
     * Target platform of the version, copied from it so that the entry outlives it.
     */
    private String targetPlatform;

    /**
     * The state the version transitioned into. Stored by name, which is what the feed reports verbatim.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExtensionVersionState state;

    /**
     * Instant the version was published, copied from it so that the entry outlives it. The same on every
     * entry of a version, and {@code null} for the versions that carry no timestamp of their own.
     */
    private LocalDateTime timestamp;

    /**
     * Instant of the transition. The feed is ordered by this, so it has to be the instant the transition
     * actually happened -- an entry written with an instant in the past would sort into a part of the
     * feed that consumers have already read past.
     */
    private LocalDateTime changedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ExtensionVersion getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(ExtensionVersion extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public ExtensionVersionState getState() {
        return state;
    }

    public void setState(ExtensionVersionState state) {
        this.state = state;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (ExtensionVersionChange) o;
        return id == that.id
                // use the id to prevent infinite recursion
                && Objects.equals(getVersionId(extensionVersion), getVersionId(that.extensionVersion))
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(extension, that.extension)
                && Objects.equals(version, that.version)
                && Objects.equals(targetPlatform, that.targetPlatform)
                && Objects.equals(state, that.state)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(changedAt, that.changedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                getVersionId(extensionVersion),
                namespace,
                extension,
                version,
                targetPlatform,
                state,
                timestamp,
                changedAt);
    }

    private static Long getVersionId(ExtensionVersion extVersion) {
        return extVersion == null ? null : extVersion.getId();
    }
}
