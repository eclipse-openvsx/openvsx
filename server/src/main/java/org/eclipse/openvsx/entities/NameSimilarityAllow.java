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

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * A pair of extensions, or of namespaces, an admin has confirmed are not squatting on each other, so
 * the pair is no longer reported. A pair is stored once, whichever way round it was allowed.
 * <p>
 * A {@link NameSimilarityField#NAMESPACE} pair sets the namespace ids, any other the extension ids.
 */
@Entity
@Table(name = "name_similarity_allow")
public class NameSimilarityAllow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "nameSimilarityAllowSeq")
    @SequenceGenerator(name = "nameSimilarityAllowSeq", sequenceName = "name_similarity_allow_seq", allocationSize = 1)
    private long id;

    /** The look-alike that was allowed, for a pair of extensions. */
    @Column(name = "extension_id")
    private @Nullable Long extensionId;

    /** The extension the look-alike was allowed alongside. */
    @Column(name = "other_extension_id")
    private @Nullable Long otherExtensionId;

    /** The look-alike that was allowed, for a pair of namespaces. */
    @Column(name = "namespace_id")
    private @Nullable Long namespaceId;

    /** The namespace the look-alike was allowed alongside. */
    @Column(name = "other_namespace_id")
    private @Nullable Long otherNamespaceId;

    /** The field that matched most closely when the pair was allowed. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NameSimilarityField field;

    /** Trigram similarity of the two values, from 0 to 1. */
    @Column(nullable = false)
    private float similarity;

    /** The look-alike's value of {@link #field} when the pair was allowed. */
    @Column(name = "lookalike_value", nullable = false)
    private String lookalikeValue;

    /** The other side's value of {@link #field} when the pair was allowed. */
    @Column(name = "other_value", nullable = false)
    private String otherValue;

    @Column(length = 2048)
    private @Nullable String reason;

    /** The admin who allowed the pair; {@code null} once that user is gone. */
    @Column(name = "decided_by_id")
    private @Nullable Long decidedById;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    public long getId() {
        return id;
    }

    public @Nullable Long getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(@Nullable Long extensionId) {
        this.extensionId = extensionId;
    }

    public @Nullable Long getOtherExtensionId() {
        return otherExtensionId;
    }

    public void setOtherExtensionId(@Nullable Long otherExtensionId) {
        this.otherExtensionId = otherExtensionId;
    }

    public @Nullable Long getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(@Nullable Long namespaceId) {
        this.namespaceId = namespaceId;
    }

    public @Nullable Long getOtherNamespaceId() {
        return otherNamespaceId;
    }

    public void setOtherNamespaceId(@Nullable Long otherNamespaceId) {
        this.otherNamespaceId = otherNamespaceId;
    }

    public NameSimilarityField getField() {
        return field;
    }

    public void setField(NameSimilarityField field) {
        this.field = field;
    }

    public float getSimilarity() {
        return similarity;
    }

    public void setSimilarity(float similarity) {
        this.similarity = similarity;
    }

    public String getLookalikeValue() {
        return lookalikeValue;
    }

    public void setLookalikeValue(String lookalikeValue) {
        this.lookalikeValue = lookalikeValue;
    }

    public String getOtherValue() {
        return otherValue;
    }

    public void setOtherValue(String otherValue) {
        this.otherValue = otherValue;
    }

    public @Nullable String getReason() {
        return reason;
    }

    public void setReason(@Nullable String reason) {
        this.reason = reason;
    }

    public @Nullable Long getDecidedById() {
        return decidedById;
    }

    public void setDecidedById(@Nullable Long decidedById) {
        this.decidedById = decidedById;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }
}
