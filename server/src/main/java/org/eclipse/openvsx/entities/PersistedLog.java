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
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
public class PersistedLog {

    @Id
    @GeneratedValue(generator = "persistedLogSeq")
    @SequenceGenerator(name = "persistedLogSeq", sequenceName = "persisted_log_seq")
    private long id;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "user_data", foreignKey = @ForeignKey(name = "persisted_log_user_data_fkey"))
    private UserData user;

    @Column(length = 512)
    private String message;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public UserData getUser() {
        return user;
    }

    public void setUser(UserData user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
