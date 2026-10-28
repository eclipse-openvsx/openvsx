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
 * One step in the publisher agreement of a user, whichever provider keeps the agreement itself. Rows are only
 * appended, so they are the audit trail, and the latest revocation or reinstatement decides whether the user is
 * blocked from signing. See {@code PublisherAgreementService}.
 */
@Entity
@Table(name = "publisher_agreement_event")
public class PublisherAgreementEvent implements Serializable {

    public enum Type {
        /** The user signed; {@code version} is the version signed. */
        SIGNED,
        /** An admin revoked the agreement, which blocks the user from signing again. */
        REVOKED,
        /** An admin lifted the block of a revocation. */
        REINSTATED
    }

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "publisherAgreementEventSeq")
    @SequenceGenerator(
        name = "publisherAgreementEventSeq",
        sequenceName = "publisher_agreement_event_seq",
        allocationSize = 1
    )
    private long id;

    @Column(name = "user_data_id", nullable = false)
    private long userDataId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type type;

    private @Nullable String version;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** The user or admin who caused the event; {@code null} once that user is gone. */
    @Column(name = "actor_id")
    private @Nullable Long actorId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserDataId() {
        return userDataId;
    }

    public void setUserDataId(long userDataId) {
        this.userDataId = userDataId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public @Nullable Long getActorId() {
        return actorId;
    }

    public void setActorId(@Nullable Long actorId) {
        this.actorId = actorId;
    }
}
